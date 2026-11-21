package com.painuo.suite.controller;

import com.painuo.suite.common.Result;
import com.painuo.suite.dto.AuthDtos;
import com.painuo.suite.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台登录/刷新/登出与当前用户信息。
 *
 * <p>登出采用"access token 进黑名单"的方案：JWT 本身无状态，
 * 但配合 Redis（或内存缓存）里的 jti 黑名单就能实现即时失效，
 * 代价是一次缓存读，比维护全量会话表轻。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return Result.ok(authService.login(request), "登录成功");
    }

    @PostMapping("/refresh")
    public Result<AuthDtos.TokenResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return Result.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return Result.ok(null, "已退出登录");
    }

    @GetMapping("/profile")
    public Result<AuthDtos.Profile> profile() {
        return Result.ok(authService.profile());
    }

    /** 动态路由：前端登录后拿它注册 vue-router 路由与侧边菜单。 */
    @GetMapping("/routes")
    public Result<List<AuthDtos.RouteVO>> routes() {
        return Result.ok(authService.routes());
    }
}
