package com.painuo.suite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 认证相关出入参。
 *
 * <p>用 record + {@code jakarta.validation} 注解：不可变、无法漏字段、
 * 校验规则与 DTO 定义放在一起，Controller 只需一个 {@code @Valid}。</p>
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 账号密码登录（运营后台）。 */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(max = 50, message = "用户名过长")
            String username,

            @NotBlank(message = "密码不能为空")
            @Size(max = 100, message = "密码过长")
            String password
    ) {
    }

    /** 刷新令牌。 */
    public record RefreshRequest(
            @NotBlank(message = "refreshToken 不能为空")
            String refreshToken
    ) {
    }

    /** 小程序登录：uni.login 拿到的 code。 */
    public record WxLoginRequest(
            @NotBlank(message = "code 不能为空")
            String code,

            @Size(max = 50, message = "昵称过长")
            String nickname,

            @Size(max = 500, message = "头像地址过长")
            String avatar
    ) {
    }

    /** 当前登录用户信息。 */
    public record Profile(
            Long id,
            String username,
            String nickname,
            String avatar,
            String phone,
            List<String> roles,
            List<String> permissions
    ) {
    }

    /** 登录成功的返回体。 */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            Profile user
    ) {
    }

    /**
     * 动态路由节点（前端据此 addRoute）。
     *
     * <p>只包含目录和菜单（type 0/1），按钮级权限走 {@code permissions} 字符串列表。</p>
     */
    public record RouteVO(
            Long id,
            Long parentId,
            String name,
            String path,
            String component,
            String icon,
            String perm,
            Integer type,
            Integer sort,
            List<RouteVO> children
    ) {
    }
}
