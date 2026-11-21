package com.painuo.suite.ai;

/**
 * 大模型提供方抽象。
 *
 * <p>抽象的目的不是"政治正确"，而是让 <b>离线可跑</b> 成为硬能力：
 * 没有 API Key、没有外网、甚至没有显卡时，{@link MockAiProvider} 让
 * 「生成摘要 / 优化标题 / 智能问答」三条链路都能端到端演示，CI 里也能断言输出。</p>
 */
public interface AiProvider {

    /** 提供方标识，写入 {@code ai_message.model} 便于后续计费与效果对比。 */
    String name();

    /** 是否具备真实调用条件（有 Key、有 baseUrl）。 */
    boolean available();

    /** 同步补全。失败应抛 {@link AiProviderException}，由上层决定是否降级。 */
    AiCompletion complete(AiCompletionRequest request);
}
