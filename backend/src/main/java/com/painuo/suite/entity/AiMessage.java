package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 消息明细。
 *
 * <p>落库的目的不只是"聊天记录"，更是<b>可观测性</b>：
 * provider/model/tokens/latency 四列可以回答"这个月 AI 花了多少钱、慢在哪"。</p>
 */
@Data
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** user / assistant。 */
    private String msgRole;

    private String content;

    private String provider;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Long latencyMs;

    private LocalDateTime createdAt;
}
