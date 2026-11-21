package com.painuo.suite.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应体。
 *
 * <pre>
 * { "code": 0, "message": "OK", "data": {...}, "traceId": "a1b2c3", "timestamp": 1730000000000 }
 * </pre>
 *
 * <p>成功恒为 {@code code = 0}；失败时 HTTP 状态码与 {@code code} 的前三位保持一致，
 * 前端拦截器可据此做统一处理。</p>
 *
 * @param <T> 业务数据类型
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Result<T>(
        /** 业务码，0 表示成功 */
        int code,
        /** 提示文案，可直接展示给用户 */
        String message,
        /** 业务数据 */
        T data,
        /** 链路追踪 ID，排查问题时可提供给后端 */
        String traceId,
        /** 服务端时间戳（毫秒） */
        long timestamp
) {

    public static <T> Result<T> ok(T data) {
        return of(ResultCode.SUCCESS, data, null);
    }

    public static <T> Result<T> ok(T data, String message) {
        return of(ResultCode.SUCCESS, data, message);
    }

    public static Result<Void> ok() {
        return of(ResultCode.SUCCESS, null, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return of(resultCode, null, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return of(resultCode, null, message);
    }

    public static <T> Result<T> of(ResultCode resultCode, T data, String message) {
        return new Result<>(
                resultCode.code(),
                message == null ? resultCode.message() : message,
                data,
                TraceContext.currentTraceId(),
                System.currentTimeMillis());
    }

    public boolean succeeded() {
        return code == ResultCode.SUCCESS.code();
    }
}
