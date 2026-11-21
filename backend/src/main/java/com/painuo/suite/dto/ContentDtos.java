package com.painuo.suite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 内容中心出入参。 */
public final class ContentDtos {

    private ContentDtos() {
    }

    /** 新增/编辑内容。 */
    public record SaveRequest(
            @NotBlank(message = "标题不能为空")
            @Size(max = 150, message = "标题最长 150 字")
            String title,

            @Size(max = 500, message = "摘要最长 500 字")
            String summary,

            @NotBlank(message = "正文不能为空")
            String body,

            @Size(max = 500, message = "封面地址过长")
            String coverUrl,

            @NotNull(message = "分类不能为空")
            Long categoryId,

            @NotBlank(message = "内容类型不能为空")
            @Pattern(regexp = "article|gallery|video", message = "内容类型只能是 article/gallery/video")
            String contentType,

            Boolean topFlag,

            Integer sort,

            List<String> tags
    ) {
    }

    /** 审核意见（驳回时使用）。 */
    public record ReviewRequest(
            @Size(max = 200, message = "审核意见最长 200 字")
            String remark
    ) {
    }

    /** 内容视图（列表与详情共用）。 */
    public record Item(
            Long id,
            String title,
            String summary,
            String body,
            String coverUrl,
            Long categoryId,
            String categoryName,
            String contentType,
            Integer status,
            String statusText,
            Integer topFlag,
            Integer sort,
            Long authorId,
            String authorName,
            LocalDateTime publishTime,
            Long viewCount,
            List<String> tags,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 分类新增/编辑。 */
    public record CategorySaveRequest(
            @NotBlank(message = "分类名称不能为空")
            @Size(max = 50, message = "分类名称最长 50 字")
            String name,

            @Size(max = 30, message = "分类编码最长 30 字")
            String code,

            Long parentId,

            Integer sort,

            Integer status
    ) {
    }

    /** 分类树节点。 */
    public record CategoryVO(
            Long id,
            Long parentId,
            String name,
            String code,
            Integer sort,
            Integer status,
            List<CategoryVO> children
    ) {
    }
}
