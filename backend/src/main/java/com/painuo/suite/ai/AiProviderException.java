package com.painuo.suite.ai;

/** AI 调用异常，统一由 AiService 捕获并决定是否降级到 Mock。 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
