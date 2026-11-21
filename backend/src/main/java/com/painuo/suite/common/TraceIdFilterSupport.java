package com.painuo.suite.common;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪上下文。
 *
 * <p>traceId 在 {@link TraceIdFilter} 中生成并写入 SLF4J MDC，
 * 业务代码与 {@link Result} 均从这里读取，保证「日志 - 响应 - 前端报错弹窗」三处一致。</p>
 */
public final class TraceIdFilterSupport {

    private TraceIdFilterSupport() {
    }

    public static final String TRACE_ID = "traceId";
    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    /** 生成 16 位短 traceId，长度可控、便于在日志里 grep。 */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "" : value;
    }
}
