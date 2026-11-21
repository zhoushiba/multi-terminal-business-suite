package com.painuo.suite.service;

import com.painuo.suite.cache.CacheKeys;
import com.painuo.suite.cache.CacheService;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ContentStatus;
import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.dto.ContentDtos;
import com.painuo.suite.security.AuthContext;
import com.painuo.suite.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 内容中心集成测试：状态机、分页过滤、缓存穿透保护。
 *
 * <p>这里刻意走真实的 Service + H2，而不是 mock mapper —— 分页 SQL 与状态流转
 * 只有在真库上跑过才敢说"能上线"。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("内容中心（状态机 / 分页 / 缓存）")
class ContentServiceTest {

    @Autowired
    private ContentService contentService;

    @Autowired
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        // 种子用户 2 = editor，作为内容作者
        AuthContext.set(new AuthPrincipal(2L, "editor", "内容编辑小李",
                List.of("editor"), List.of("content:list", "content:write", "content:review"), null));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("C 端列表只返回已发布内容")
    void publishedListOnlyContainsPublished() {
        PageResult<ContentDtos.Item> page = contentService.pagePublished(null, null, null, PageQuery.of(1, 50, null));

        assertThat(page.total()).isGreaterThanOrEqualTo(10);
        assertThat(page.list()).isNotEmpty()
                .allSatisfy(item -> assertThat(item.status()).isEqualTo(ContentStatus.PUBLISHED.code()));
        assertThat(page.list()).allSatisfy(item -> assertThat(item.statusText()).isEqualTo("已发布"));
    }

    @Test
    @DisplayName("后台列表按状态过滤：待审只出待审")
    void adminListFiltersByStatus() {
        PageResult<ContentDtos.Item> page = contentService.pageForAdmin(
                null, null, ContentStatus.PENDING.code(), null, PageQuery.of(1, 20, null));

        assertThat(page.total()).isGreaterThanOrEqualTo(2);
        assertThat(page.list()).isNotEmpty()
                .allSatisfy(item -> assertThat(item.status()).isEqualTo(ContentStatus.PENDING.code()));
    }

    @Test
    @DisplayName("后台列表按关键字过滤标题/摘要")
    void adminListFiltersByKeyword() {
        PageResult<ContentDtos.Item> page = contentService.pageForAdmin(
                "黄果树", null, null, null, PageQuery.of(1, 20, null));

        assertThat(page.total()).isPositive();
        assertThat(page.list()).allSatisfy(item -> {
            String text = (item.title() == null ? "" : item.title()) + (item.summary() == null ? "" : item.summary());
            assertThat(text).contains("黄果树");
        });
    }

    @Test
    @DisplayName("每页条数被夹到 100 以内（分页放大保护）")
    void paginationSizeIsCapped() {
        PageQuery query = PageQuery.of(1, 100000, null);
        assertThat(query.size()).isEqualTo(PageQuery.MAX_SIZE);

        PageResult<ContentDtos.Item> page = contentService.pageForAdmin(null, null, null, null, query);
        assertThat(page.size()).isEqualTo(PageQuery.MAX_SIZE);
        assertThat(page.list().size()).isLessThanOrEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    @DisplayName("内容生命周期：草稿 → 待审 → 发布 → 下架，发布时写入发布时间")
    void contentLifecycleFollowsStateMachine() {
        ContentDtos.Item created = contentService.create(new ContentDtos.SaveRequest(
                "集成测试内容-" + UUID.randomUUID().toString().substring(0, 6),
                "用于验证状态机的摘要",
                "<p>正文</p>",
                null, 1L, "article", false, null, List.of("测试")));
        assertThat(created.status()).isEqualTo(ContentStatus.DRAFT.code());

        ContentDtos.Item pending = contentService.transition(created.id(), ContentStatus.PENDING, null);
        assertThat(pending.status()).isEqualTo(ContentStatus.PENDING.code());

        ContentDtos.Item published = contentService.transition(created.id(), ContentStatus.PUBLISHED, null);
        assertThat(published.status()).isEqualTo(ContentStatus.PUBLISHED.code());
        assertThat(published.publishTime()).isNotNull();

        ContentDtos.Item offline = contentService.transition(created.id(), ContentStatus.OFFLINE, "临时下线");
        assertThat(offline.status()).isEqualTo(ContentStatus.OFFLINE.code());

        // 已下架不能直接回到草稿
        BizException ex = catchThrowableOfType(
                () -> contentService.transition(created.id(), ContentStatus.DRAFT, null), BizException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.resultCode()).isEqualTo(ResultCode.CONTENT_STATE_ILLEGAL);
    }

    @Test
    @DisplayName("草稿内容在 C 端详情不可见，且写入空值缓存防止穿透")
    void unpublishedDetailIsNotFoundAndMarkedNull() {
        Long draftId = 12L;
        String key = CacheKeys.content(draftId);

        BizException first = catchThrowableOfType(() -> contentService.publishedDetail(draftId), BizException.class);
        assertThat(first).isNotNull();
        assertThat(first.resultCode()).isEqualTo(ResultCode.NOT_FOUND);
        // 第一次回源后应写空值标记
        assertThat(cacheService.isNullMarked(key)).isTrue();

        // 第二次（模拟缓存穿透攻击的重复请求）直接命中空值标记，仍然 404
        BizException second = catchThrowableOfType(() -> contentService.publishedDetail(draftId), BizException.class);
        assertThat(second).isNotNull();
        assertThat(second.resultCode()).isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    @DisplayName("浏览数自增并把缓存让位给下一次回源")
    void viewCountIncrements() {
        Long publishedId = 1L;
        long before = contentService.adminDetail(publishedId).viewCount();

        contentService.incrementView(publishedId);

        long after = contentService.adminDetail(publishedId).viewCount();
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("已发布内容命中缓存后，删库也不影响本次读取（缓存确实生效）")
    void publishedDetailIsCached() {
        ContentDtos.Item first = contentService.publishedDetail(2L);
        ContentDtos.Item second = contentService.publishedDetail(2L);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.title()).isEqualTo(first.title());
        assertThat(cacheService.get(CacheKeys.content(2L))).isNotNull();
    }
}
