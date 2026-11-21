package com.painuo.suite.security;

import com.painuo.suite.cache.CacheService;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录态拦截器。
 *
 * <p>职责边界刻意收得很窄：只负责「解析 accessToken → 填充 AuthContext → 校验黑名单」。
 * 权限判断交给 {@link PermAspect}，因为要在方法参数绑定之后才拿得到注解 + 上下文。</p>
 *
 * <p>放行的路径在 {@link com.painuo.suite.config.WebMvcConfig} 里统一定义。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final JwtTokenProvider tokenProvider;
    private final CacheService cacheService;

    public AuthInterceptor(JwtTokenProvider tokenProvider, CacheService cacheService) {
        this.tokenProvider = tokenProvider;
        this.cacheService = cacheService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED);
        }
        JwtTokenProvider.ParsedToken parsed = tokenProvider.parse(token, JwtTokenProvider.TYPE_ACCESS);
        // 退出登录后令牌立即失效（黑名单 TTL 与令牌剩余时间一致，不会无限增长）
        if (cacheService.has(BLACKLIST_PREFIX + parsed.jti())) {
            throw BizException.of(ResultCode.TOKEN_INVALID, "登录状态已失效，请重新登录");
        }
        AuthContext.set(new AuthPrincipal(
                parsed.userId(), parsed.username(), parsed.nickname(),
                parsed.roles(), parsed.permissions(),
                request.getHeader("X-Refresh-Token")));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String value = header.substring(7).trim();
            return value.isEmpty() ? null : value;
        }
        // 小程序/调试场景允许 query 传 token
        String fromQuery = request.getParameter("access_token");
        return (fromQuery == null || fromQuery.isBlank()) ? null : fromQuery;
    }
}
