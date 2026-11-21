package com.painuo.suite.controller;

import com.painuo.suite.common.ContentStatus;
import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.Result;
import com.painuo.suite.dto.ContentDtos;
import com.painuo.suite.entity.CmsTag;
import com.painuo.suite.security.RequirePerm;
import com.painuo.suite.service.ContentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内容中心。
 *
 * <p>接口刻意分成两组：{@code /api/...} 是运营后台（鉴权 + 按钮权限），
 * {@code /api/public/...} 是 C 端（小程序/官网，免鉴权、只暴露已发布数据）。
 * 这样 C 端的查询条件里永远不会出现 status，业务上就不可能越权读到草稿。</p>
 */
@RestController
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    // ------------------------------------------------------------------ 后台

    @GetMapping("/api/contents")
    @RequirePerm("content:list")
    public Result<PageResult<ContentDtos.Item>> page(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Long categoryId,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(required = false) String contentType,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.pageForAdmin(keyword, categoryId, status, contentType,
                PageQuery.of(page, size, null)));
    }

    @GetMapping("/api/contents/{id}")
    @RequirePerm("content:list")
    public Result<ContentDtos.Item> detail(@PathVariable Long id) {
        return Result.ok(contentService.adminDetail(id));
    }

    @PostMapping("/api/contents")
    @RequirePerm("content:write")
    public Result<ContentDtos.Item> create(@Valid @RequestBody ContentDtos.SaveRequest request) {
        return Result.ok(contentService.create(request), "内容已创建为草稿");
    }

    @PutMapping("/api/contents/{id}")
    @RequirePerm("content:write")
    public Result<ContentDtos.Item> update(@PathVariable Long id,
                                           @Valid @RequestBody ContentDtos.SaveRequest request) {
        return Result.ok(contentService.update(id, request), "保存成功");
    }

    @DeleteMapping("/api/contents/{id}")
    @RequirePerm("content:write")
    public Result<Void> delete(@PathVariable Long id) {
        contentService.delete(id);
        return Result.ok(null, "已删除");
    }

    /** 提交审核：草稿/驳回/下架 → 待审核。 */
    @PostMapping("/api/contents/{id}/submit")
    @RequirePerm("content:write")
    public Result<ContentDtos.Item> submit(@PathVariable Long id) {
        return Result.ok(contentService.transition(id, ContentStatus.PENDING, null), "已提交审核");
    }

    /** 审核通过：待审核 → 已发布。 */
    @PostMapping("/api/contents/{id}/publish")
    @RequirePerm("content:review")
    public Result<ContentDtos.Item> publish(@PathVariable Long id) {
        return Result.ok(contentService.transition(id, ContentStatus.PUBLISHED, null), "已发布");
    }

    /** 审核驳回：待审核 → 已驳回。 */
    @PostMapping("/api/contents/{id}/reject")
    @RequirePerm("content:review")
    public Result<ContentDtos.Item> reject(@PathVariable Long id,
                                           @RequestBody(required = false) ContentDtos.ReviewRequest request) {
        String remark = request == null ? null : request.remark();
        return Result.ok(contentService.transition(id, ContentStatus.REJECTED, remark), "已驳回");
    }

    /** 下架：已发布 → 已下架。 */
    @PostMapping("/api/contents/{id}/offline")
    @RequirePerm("content:review")
    public Result<ContentDtos.Item> offline(@PathVariable Long id) {
        return Result.ok(contentService.transition(id, ContentStatus.OFFLINE, null), "已下架");
    }

    // ------------------------------------------------------------------ 分类与标签

    @GetMapping("/api/categories")
    @RequirePerm("content:list")
    public Result<List<ContentDtos.CategoryVO>> categories() {
        return Result.ok(contentService.categoryTree());
    }

    @PostMapping("/api/categories")
    @RequirePerm("content:write")
    public Result<ContentDtos.CategoryVO> createCategory(@Valid @RequestBody ContentDtos.CategorySaveRequest request) {
        return Result.ok(contentService.createCategory(request), "分类已创建");
    }

    @PutMapping("/api/categories/{id}")
    @RequirePerm("content:write")
    public Result<ContentDtos.CategoryVO> updateCategory(@PathVariable Long id,
                                                         @Valid @RequestBody ContentDtos.CategorySaveRequest request) {
        return Result.ok(contentService.updateCategory(id, request), "分类已更新");
    }

    @DeleteMapping("/api/categories/{id}")
    @RequirePerm("content:write")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        contentService.deleteCategory(id);
        return Result.ok(null, "分类已删除");
    }

    @GetMapping("/api/tags")
    @RequirePerm("content:list")
    public Result<List<CmsTag>> tags() {
        return Result.ok(contentService.tags());
    }

    // ------------------------------------------------------------------ C 端（免鉴权）

    @GetMapping("/api/public/contents")
    public Result<PageResult<ContentDtos.Item>> publicPage(@RequestParam(required = false) Long categoryId,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String sort,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.pagePublished(categoryId, keyword, sort,
                PageQuery.of(page, size, null)));
    }

    @GetMapping("/api/public/contents/{id}")
    public Result<ContentDtos.Item> publicDetail(@PathVariable Long id) {
        return Result.ok(contentService.publishedDetail(id));
    }

    /** 浏览数 +1（小程序进详情页时调用，失败不影响阅读，所以单独接口）。 */
    @PostMapping("/api/public/contents/{id}/view")
    public Result<Void> view(@PathVariable Long id) {
        contentService.incrementView(id);
        return Result.ok();
    }

    @GetMapping("/api/public/categories")
    public Result<List<ContentDtos.CategoryVO>> publicCategories() {
        return Result.ok(contentService.categoryTree());
    }

    @GetMapping("/api/public/tags")
    public Result<List<CmsTag>> publicTags() {
        return Result.ok(contentService.tags());
    }
}
