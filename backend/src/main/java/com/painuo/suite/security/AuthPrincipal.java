package com.painuo.suite.security;

import java.util.List;

/**
 * 登录态主体：由 JWT 解析得到，请求级共享（存 ThreadLocal）。
 *
 * @param userId      用户 ID
 * @param username    登录名
 * @param nickname    昵称
 * @param roles       角色编码集合，如 {@code [admin, editor]}
 * @param permissions 权限标识集合，如 {@code [cms:content:create]}
 * @param refreshToken 当前请求携带 refreshToken 时才有值（仅 /auth/refresh 用）
 */
public record AuthPrincipal(
        Long userId,
        String username,
        String nickname,
        List<String> roles,
        List<String> permissions,
        String refreshToken
) {

    public boolean hasPermission(String perm) {
        return permissions != null && permissions.contains(perm);
    }

    public boolean hasAnyRole(String... codes) {
        if (roles == null) {
            return false;
        }
        for (String code : codes) {
            if (roles.contains(code)) {
                return true;
            }
        }
        return false;
    }
}
