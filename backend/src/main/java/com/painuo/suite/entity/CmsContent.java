package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容主表。
 *
 * <p>{@code status} 走状态机（见 {@code ContentStateMachine}），
 * 不允许通过普通 CRUD 直接改，必须调用专门的流转接口——
 * 这是"内容审核"这类业务最容易被写坏的地方。</p>
 */
@Data
@TableName("cms_content")
public class CmsContent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 摘要：可由 AI 生成后人工修订。 */
    private String summary;

    /** 正文（富文本 HTML，前端做 XSS 白名单过滤）。 */
    private String body;

    private String coverUrl;

    private Long categoryId;

    /** article / gallery / video。 */
    private String contentType;

    /** 0 草稿 / 1 待审 / 2 已发布 / 3 已下架 / 4 已驳回。 */
    private Integer status;

    /** 1 置顶。 */
    private Integer topFlag;

    private Integer sort;

    private Long authorId;

    private String authorName;

    private LocalDateTime publishTime;

    private Long viewCount;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
