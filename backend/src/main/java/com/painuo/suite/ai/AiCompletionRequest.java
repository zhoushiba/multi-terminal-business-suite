package com.painuo.suite.ai;

/**
 * 补全请求。
 *
 * @param systemPrompt 系统提示词（角色设定 + 输出约束）
 * @param userPrompt   用户内容（含 RAG 拼接的参考资料）
 * @param temperature  采样温度，0 更稳定、1 更发散
 * @param maxTokens    最大输出 token
 */
public record AiCompletionRequest(
        String systemPrompt,
        String userPrompt,
        double temperature,
        int maxTokens
) {

    public static AiCompletionRequest of(String systemPrompt, String userPrompt) {
        return new AiCompletionRequest(systemPrompt, userPrompt, 0.3, 800);
    }
}
