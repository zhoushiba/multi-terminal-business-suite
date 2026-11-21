package com.painuo.suite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.painuo.suite.cache.CacheKeys;
import com.painuo.suite.cache.CacheService;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ContentStatus;
import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.config.AppProperties;
import com.painuo.suite.dto.ContentDtos;
import com.painuo.suite.entity.CmsCategory;
import com.painuo.suite.entity.CmsContent;
import com.painuo.suite.entity.CmsContentTag;
import com.painuo.suite.entity.CmsTag;
import com.painuo.suite.mapper.CmsCategoryMapper;
import com.painuo.suite.mapper.CmsContentMapper;
import com.painuo.suite.mapper.CmsContentTagMapper;
import com.painuo.suite.mapper.CmsTagMapper;
import com.painuo.suite.security.AuthContext;
import com.painuo.suite.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内容中心。
 *
 * <p>两个面试级别的实现点：</p>
 * <ol>
 *   <li><b>状态机</b>：状态只能由 {@link #transition} 流转，普通 CRUD 不允许写 status；</li>
 *   <li><b>热点缓存</b>：详情页 cache-aside，含空值缓存（防穿透）、TTL 抖动（防雪崩）、
 *       互斥重建（防击穿）。</li>
 * </ol>
 */
@Service
public class ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);

    /** 重建缓存的互斥锁 TTL，避免拿到锁的线程崩溃后死锁。 */
    private static final long LOCK_TTL_SECONDS = 10;
    /** 未抢到锁的线程等待时长，用于等第一个线程回填缓存。 */
    private static final long LOCK_WAIT_MILLIS = 60;
    /** 允许删除的状态：已发布的内容必须先下架。 */
    private static final Set<Integer> DELETABLE_STATUS = Set.of(
            ContentStatus.DRAFT.code(), ContentStatus.REJECTED.code(), ContentStatus.OFFLINE.code());

    private final CmsContentMapper contentMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsTagMapper tagMapper;
    private final CmsContentTagMapper contentTagMapper;
    private final CacheService cache;
    private final ObjectMapper objectMapper;
    private final AppProperties.Cache cacheProps;

    public ContentService(CmsContentMapper contentMapper,
                          CmsCategoryMapper categoryMapper,
                          CmsTagMapper tagMapper,
                          CmsContentTagMapper contentTagMapper,
                          CacheService cache,
                          ObjectMapper objectMapper,
                          AppProperties appProperties) {
        this.contentMapper = contentMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.contentTagMapper = contentTagMapper;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.cacheProps = appProperties.cache();
    }

    // ------------------------------------------------------------------ 查询

    /** 后台列表：支持关键字/分类/状态/类型过滤。 */
    public PageResult<ContentDtos.Item> pageForAdmin(String keyword, Long categoryId, Integer status,
                                                     String contentType, PageQuery pageQuery) {
        LambdaQueryWrapper<CmsContent> wrapper = Wrappers.<CmsContent>lambdaQuery()
                .and(hasText(keyword), w -> w.like(CmsContent::getTitle, keyword)
                        .or().like(CmsContent::getSummary, keyword))
                .eq(categoryId != null, CmsContent::getCategoryId, categoryId)
                .eq(status != null, CmsContent::getStatus, status)
                .eq(hasText(contentType), CmsContent::getContentType, contentType)
                .orderByDesc(CmsContent::getTopFlag)
                .orderByDesc(CmsContent::getId);
        return selectPage(wrapper, pageQuery);
    }

    /** 小程序/官网列表：只出已发布，支持 new/hot 两种排序。 */
    public PageResult<ContentDtos.Item> pagePublished(Long categoryId, String keyword, String sort,
                                                      PageQuery pageQuery) {
        LambdaQueryWrapper<CmsContent> wrapper = Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getStatus, ContentStatus.PUBLISHED.code())
                .eq(categoryId != null, CmsContent::getCategoryId, categoryId)
                .like(hasText(keyword), CmsContent::getTitle, keyword);
        if ("hot".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(CmsContent::getViewCount).orderByDesc(CmsContent::getId);
        } else {
            wrapper.orderByDesc(CmsContent::getTopFlag).orderByDesc(CmsContent::getPublishTime);
        }
        return selectPage(wrapper, pageQuery);
    }

    /**
     * 已发布内容详情（走缓存）。
     *
     * <p>缓存三件套的落地顺序很重要：</p>
     * <pre>
     *   1. 先看空值标记 —— 命中说明"这个 ID 确实不存在"，直接 404，不打库（防穿透）
     *   2. 再看正常缓存 —— 命中直接返回
     *   3. 未命中加互斥锁 —— 只放一个线程回源，其余等待后重读（防击穿）
     *   4. 回源查库 —— 查不到写空值标记；查到写正常缓存（TTL 已带抖动，防雪崩）
     * </pre>
     */
    public ContentDtos.Item publishedDetail(Long id) {
        String key = CacheKeys.content(id);
        if (cache.isNullMarked(key)) {
            throw BizException.notFound("内容");
        }
        String cached = cache.get(key);
        if (cached != null) {
            return readCache(cached);
        }

        if (cache.increment(CacheKeys.contentLock(id), LOCK_TTL_SECONDS) > 1) {
            sleepQuietly(LOCK_WAIT_MILLIS);
            String retry = cache.get(key);
            if (retry != null) {
                return readCache(retry);
            }
        }

        CmsContent content = contentMapper.selectById(id);
        if (content == null || content.getStatus() == null
                || content.getStatus() != ContentStatus.PUBLISHED.code()) {
            // 空值缓存：TTL 远小于正常值，避免脏数据长期占位
            cache.setNull(key, cacheProps.nullValueTtlSeconds());
            throw BizException.notFound("内容");
        }
        ContentDtos.Item item = toItem(content);
        try {
            cache.set(key, objectMapper.writeValueAsString(item), cacheProps.defaultTtlSeconds());
        } catch (JsonProcessingException ex) {
            log.warn("内容缓存序列化失败，本次跳过缓存: id={}", id, ex);
        }
        return item;
    }

    /** 后台详情：不做缓存，保证审核看到的是最新数据。 */
    public ContentDtos.Item adminDetail(Long id) {
        return toItem(requireContent(id));
    }

    // ------------------------------------------------------------------ 写操作

    @Transactional
    public ContentDtos.Item create(ContentDtos.SaveRequest request) {
        requireCategory(request.categoryId());
        AuthPrincipal principal = AuthContext.require();

        CmsContent content = new CmsContent();
        applyRequest(content, request);
        content.setStatus(ContentStatus.DRAFT.code());
        content.setViewCount(0L);
        content.setAuthorId(principal.userId());
        content.setAuthorName(hasText(principal.nickname()) ? principal.nickname() : principal.username());
        if (request.sort() == null) {
            Integer maxSort = contentMapper.selectMaxSort(request.categoryId());
            content.setSort(maxSort == null ? 0 : maxSort + 1);
        }
        contentMapper.insert(content);
        bindTags(content.getId(), request.tags());
        return adminDetail(content.getId());
    }

    @Transactional
    public ContentDtos.Item update(Long id, ContentDtos.SaveRequest request) {
        CmsContent existing = requireContent(id);
        requireCategory(request.categoryId());
        if (existing.getStatus() != null && existing.getStatus() == ContentStatus.PUBLISHED.code()
                && request.topFlag() == null) {
            log.info("编辑已发布内容 id={}，状态保持不变（如需重新审核请走 /offline → /submit）", id);
        }
        CmsContent patch = new CmsContent();
        patch.setId(id);
        applyRequest(patch, request);
        patch.setUpdatedAt(LocalDateTime.now());
        contentMapper.updateById(patch);
        bindTags(id, request.tags());
        evict(id);
        return adminDetail(id);
    }

    /** 逻辑删除：已发布内容必须先下架，防止"线上内容凭空消失"。 */
    @Transactional
    public void delete(Long id) {
        CmsContent content = requireContent(id);
        if (content.getStatus() == null || !DELETABLE_STATUS.contains(content.getStatus())) {
            throw BizException.of(ResultCode.CONTENT_STATE_ILLEGAL,
                    "当前状态（" + ContentStatus.labelOf(content.getStatus()) + "）不允许删除，请先下架");
        }
        contentMapper.deleteById(id);
        evict(id);
    }

    /**
     * 状态流转（唯一入口）。
     *
     * @param target 目标状态
     * @param remark 审核意见（驳回时建议填写）
     */
    @Transactional
    public ContentDtos.Item transition(Long id, ContentStatus target, String remark) {
        CmsContent content = requireContent(id);
        ContentStatus current = ContentStatus.of(content.getStatus());
        current.requireTransitionTo(target);

        CmsContent patch = new CmsContent();
        patch.setId(id);
        patch.setStatus(target.code());
        patch.setUpdatedAt(LocalDateTime.now());
        if (target == ContentStatus.PUBLISHED) {
            patch.setPublishTime(LocalDateTime.now());
        }
        contentMapper.updateById(patch);
        evict(id);
        log.info("内容状态流转: id={}, {} -> {}{}", id, current.label(), target.label(),
                hasText(remark) ? "，意见：" + remark : "");
        return adminDetail(id);
    }

    /** 浏览数 +1（列表页点开详情时调用）。 */
    public void incrementView(Long id) {
        contentMapper.incrementViewCount(id);
        evict(id);
    }

    // ------------------------------------------------------------------ 分类与标签

    /** 分类树（两级）。 */
    public List<ContentDtos.CategoryVO> categoryTree() {
        List<CmsCategory> all = categoryMapper.selectList(Wrappers.<CmsCategory>lambdaQuery()
                .orderByAsc(CmsCategory::getSort).orderByAsc(CmsCategory::getId));
        Map<Long, List<CmsCategory>> byParent = new LinkedHashMap<>();
        for (CmsCategory category : all) {
            byParent.computeIfAbsent(category.getParentId() == null ? 0L : category.getParentId(),
                    key -> new ArrayList<>()).add(category);
        }
        return buildCategoryTree(byParent, 0L);
    }

    @Transactional
    public ContentDtos.CategoryVO createCategory(ContentDtos.CategorySaveRequest request) {
        CmsCategory category = new CmsCategory();
        applyCategory(category, request);
        categoryMapper.insert(category);
        return toCategoryVO(category, List.of());
    }

    @Transactional
    public ContentDtos.CategoryVO updateCategory(Long id, ContentDtos.CategorySaveRequest request) {
        CmsCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw BizException.notFound("分类");
        }
        applyCategory(category, request);
        category.setId(id);
        categoryMapper.updateById(category);
        return toCategoryVO(category, List.of());
    }

    @Transactional
    public void deleteCategory(Long id) {
        Long children = categoryMapper.selectCount(Wrappers.<CmsCategory>lambdaQuery()
                .eq(CmsCategory::getParentId, id));
        if (children != null && children > 0) {
            throw BizException.of(ResultCode.CONFLICT, "存在子分类，无法删除");
        }
        Long contents = contentMapper.selectCount(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getCategoryId, id));
        if (contents != null && contents > 0) {
            throw BizException.of(ResultCode.CONFLICT, "该分类下还有内容，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    /** 全部标签（后台下拉与小程序筛选）。 */
    public List<CmsTag> tags() {
        return tagMapper.selectList(Wrappers.<CmsTag>lambdaQuery().orderByAsc(CmsTag::getId));
    }

    // ------------------------------------------------------------------ 内部方法

    private PageResult<ContentDtos.Item> selectPage(LambdaQueryWrapper<CmsContent> wrapper, PageQuery pageQuery) {
        Page<CmsContent> page = contentMapper.selectPage(pageQuery.toPage(), wrapper);
        Map<Long, String> categoryNames = categoryNameMap(page.getRecords());
        List<ContentDtos.Item> items = page.getRecords().stream()
                .map(content -> toItem(content, categoryNames.get(content.getCategoryId()), List.of()))
                .toList();
        return PageResult.of(items, page.getCurrent(), page.getSize(), page.getTotal());
    }

    private Map<Long, String> categoryNameMap(List<CmsContent> contents) {
        Set<Long> ids = contents.stream()
                .map(CmsContent::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        // 一次批量查询替代 N 次 selectById
        List<CmsCategory> categories = categoryMapper.selectBatchIds(ids);
        Map<Long, String> map = new HashMap<>(categories.size());
        for (CmsCategory category : categories) {
            map.put(category.getId(), category.getName());
        }
        return map;
    }

    private ContentDtos.Item toItem(CmsContent content) {
        CmsCategory category = content.getCategoryId() == null ? null
                : categoryMapper.selectById(content.getCategoryId());
        return toItem(content, category == null ? null : category.getName(),
                contentTagMapper.selectTagNames(content.getId()));
    }

    private ContentDtos.Item toItem(CmsContent content, String categoryName, List<String> tags) {
        return new ContentDtos.Item(
                content.getId(), content.getTitle(), content.getSummary(), content.getBody(),
                content.getCoverUrl(), content.getCategoryId(), categoryName, content.getContentType(),
                content.getStatus(), ContentStatus.labelOf(content.getStatus()), content.getTopFlag(),
                content.getSort(), content.getAuthorId(), content.getAuthorName(), content.getPublishTime(),
                content.getViewCount(), tags, content.getCreatedAt(), content.getUpdatedAt());
    }

    private void applyRequest(CmsContent content, ContentDtos.SaveRequest request) {
        content.setTitle(request.title());
        content.setSummary(request.summary());
        content.setBody(request.body());
        content.setCoverUrl(request.coverUrl());
        content.setCategoryId(request.categoryId());
        content.setContentType(request.contentType());
        content.setTopFlag(Boolean.TRUE.equals(request.topFlag()) ? 1 : 0);
        if (request.sort() != null) {
            content.setSort(request.sort());
        }
    }

    private void applyCategory(CmsCategory category, ContentDtos.CategorySaveRequest request) {
        category.setName(request.name());
        category.setCode(request.code());
        category.setParentId(request.parentId() == null ? 0L : request.parentId());
        category.setSort(request.sort() == null ? 0 : request.sort());
        category.setStatus(request.status() == null ? 1 : request.status());
    }

    /** 标签全量重写：先删关联再按名称 upsert，保证幂等。 */
    private void bindTags(Long contentId, List<String> tags) {
        contentTagMapper.delete(Wrappers.<CmsContentTag>lambdaQuery()
                .eq(CmsContentTag::getContentId, contentId));
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String name : tags) {
            if (!hasText(name)) {
                continue;
            }
            String trimmed = name.trim();
            CmsTag tag = tagMapper.selectOne(Wrappers.<CmsTag>lambdaQuery()
                    .eq(CmsTag::getName, trimmed).last("LIMIT 1"));
            if (tag == null) {
                tag = new CmsTag();
                tag.setName(trimmed);
                tagMapper.insert(tag);
            }
            CmsContentTag relation = new CmsContentTag();
            relation.setContentId(contentId);
            relation.setTagId(tag.getId());
            contentTagMapper.insert(relation);
        }
    }

    private CmsContent requireContent(Long id) {
        CmsContent content = contentMapper.selectById(id);
        if (content == null) {
            throw BizException.notFound("内容");
        }
        return content;
    }

    private void requireCategory(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw BizException.param("分类不存在: " + categoryId);
        }
    }

    private void evict(Long id) {
        cache.delete(CacheKeys.content(id));
    }

    private ContentDtos.Item readCache(String json) {
        try {
            return objectMapper.readValue(json, ContentDtos.Item.class);
        } catch (JsonProcessingException ex) {
            log.warn("内容缓存反序列化失败，回源数据库", ex);
            throw BizException.notFound("内容");
        }
    }

    private List<ContentDtos.CategoryVO> buildCategoryTree(Map<Long, List<CmsCategory>> byParent, Long parentId) {
        List<CmsCategory> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        return children.stream()
                .map(category -> toCategoryVO(category, buildCategoryTree(byParent, category.getId())))
                .toList();
    }

    private ContentDtos.CategoryVO toCategoryVO(CmsCategory category, List<ContentDtos.CategoryVO> children) {
        return new ContentDtos.CategoryVO(category.getId(), category.getParentId(), category.getName(),
                category.getCode(), category.getSort(), category.getStatus(), children);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
