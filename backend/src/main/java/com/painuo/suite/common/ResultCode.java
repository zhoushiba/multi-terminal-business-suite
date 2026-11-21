package com.painuo.suite.common;

import org.springframework.http.HttpStatus;

/**
 * 全局错误码字典。
 *
 * <p>编码规则：{@code HTTP 状态码 * 100 + 业务序号}，方便前端「先按 HTTP 状态兜底、
 * 再按业务码精确提示」。0 表示成功。</p>
 */
public enum ResultCode {

    SUCCESS(0, "OK", HttpStatus.OK),

    PARAM_INVALID(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40101, "未登录或登录状态已失效", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, "访问令牌已过期", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(40103, "访问令牌非法", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(40104, "刷新令牌已失效，请重新登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40301, "没有操作权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(40401, "资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(40501, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(40901, "资源状态冲突", HttpStatus.CONFLICT),

    CONTENT_STATE_ILLEGAL(42201, "内容状态流转不合法", HttpStatus.UNPROCESSABLE_ENTITY),
    SIGNUP_SOLD_OUT(42202, "名额已售罄", HttpStatus.UNPROCESSABLE_ENTITY),
    SIGNUP_DUPLICATE(42203, "请勿重复报名", HttpStatus.UNPROCESSABLE_ENTITY),
    SIGNUP_EXPIRED(42204, "报名时间已截止", HttpStatus.UNPROCESSABLE_ENTITY),
    SIGNUP_STATE_ILLEGAL(42205, "报名状态不允许该操作", HttpStatus.UNPROCESSABLE_ENTITY),

    RATE_LIMITED(42901, "请求过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(50001, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    AI_PROVIDER_ERROR(50201, "AI 服务调用失败", HttpStatus.BAD_GATEWAY),
    STORAGE_ERROR(50301, "文件存储服务不可用", HttpStatus.SERVICE_UNAVAILABLE),
    WECHAT_ERROR(50401, "微信服务调用失败", HttpStatus.BAD_GATEWAY);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
