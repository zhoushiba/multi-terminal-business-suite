package com.painuo.suite.controller;

import com.painuo.suite.common.Result;
import com.painuo.suite.dto.AuthDtos;
import com.painuo.suite.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信小程序登录。
 *
 * <p>流程：小程序 {@code uni.login()} 拿 code → 这里换 openid → 首次自动建号 →
 * 返回同一套 JWT。没有配置 appId/appSecret 时 {@link com.painuo.suite.wechat.WechatClient}
 * 走 mock（openid = mock_ + code 的哈希），因此本地和 CI 都能跑通整条链路。</p>
 */
@RestController
@RequestMapping("/api/wx")
public class WxAuthController {

    private final AuthService authService;

    public WxAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.WxLoginRequest request) {
        return Result.ok(authService.wxLogin(request), "登录成功");
    }
}
