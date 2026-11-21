package com.painuo.suite.common;

import org.slf4j.MDC;

/**
 * traceId 读取入口（供 {@link Result} 使用）。
 */
public final class TraceContext {

    private TraceContext() {
    }

    public static String currentTraceId() {
        String value = MDC.get(TraceIdFilterSupport.MDC_KEY);
        return value == null ? "" : value;
    }
}
