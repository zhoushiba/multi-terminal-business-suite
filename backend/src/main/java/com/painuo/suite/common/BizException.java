package com.painuo.suite.common;

/**
 * 业务异常：所有可预期的业务失败都抛它，由 {@link GlobalExceptionHandler} 统一翻译成
 * {@link Result} + 对应 HTTP 状态码，避免 controller 里写 if-else 拼错误响应。
 */
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.message());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public ResultCode resultCode() {
        return resultCode;
    }

    public static BizException of(ResultCode code) {
        return new BizException(code);
    }

    public static BizException of(ResultCode code, String message) {
        return new BizException(code, message);
    }

    public static BizException notFound(String what) {
        return new BizException(ResultCode.NOT_FOUND, what + "不存在");
    }

    public static BizException forbidden(String message) {
        return new BizException(ResultCode.FORBIDDEN, message);
    }

    public static BizException param(String message) {
        return new BizException(ResultCode.PARAM_INVALID, message);
    }
}
