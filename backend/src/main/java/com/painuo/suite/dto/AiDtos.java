package com.painuo.suite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** AI 能力出入参。 */
public final class AiDtos {

    private AiDtos() {
    }

    /** AI 生成摘要/标题的通用入参（也可对已有内容发起）。 */
    public record AiAssistRequest(
            @Size(max = 150, message = "标题最长 150 字")
            String title,
            @NotBlank(message = "正文不能为空")
            String body,
            Long contentId
    ) {
    }

    /** 智能问答。 */
    public record ChatRequest(
            @NotBlank(message = "问题不能为空")
            @Size(max = 500, message = "问题最长 500 字")
            String question
    ) {
    }

    /** RAG 引用条目：回答依据的站内内容。 */
    public record Reference(
            Long contentId,
            String title,
            String snippet
    ) {
    }

    /**
     * AI 结果。
     *
     * @param fallback true 表示真实 provider 不可用或调用失败，已降级到 mock
     */
    public record AiResultVO(
            String content,
            String provider,
            String model,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            boolean fallback,
            List<Reference> references
    ) {
    }
}
