package com.painuo.suite.ai;

/**
 * 补全结果。
 *
 * @param content          模型输出正文
 * @param provider         提供方标识（mock / openai-compatible）
 * @param model            模型名
 * @param promptTokens     输入 token（mock 时为 0，仅用于展示口径统一）
 * @param completionTokens 输出 token
 * @param latencyMs        耗时，便于后台看板展示 AI 调用质量
 */
public record AiCompletion(
        String content,
        String provider,
        String model,
        int promptTokens,
        int completionTokens,
        long latencyMs
) {
}
