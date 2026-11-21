package com.painuo.suite.security;

import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;

/**
 * 请求级登录态上下文。
 *
 * <p>由 {@link AuthInterceptor} 在 preHandle 写入、afterCompletion 清理，
 * 业务层通过 {@code AuthContext.require()} 拿当前用户，不用层层传参。</p>
 */
public final class AuthContext {

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    public static AuthPrincipal get() {
        return HOLDER.get();
    }

    /** 必须已登录，否则抛 401。 */
    public static AuthPrincipal require() {
        AuthPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED);
        }
        return principal;
    }

    public static Long requireUserId() {
        return require().userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
