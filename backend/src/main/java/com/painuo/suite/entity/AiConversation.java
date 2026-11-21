package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 会话（按场景分：summary/title/qa，便于分别统计成本与效果）。 */
@Data
@TableName("ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /** summary / title / qa。 */
    private String scene;

    private Integer messageCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
