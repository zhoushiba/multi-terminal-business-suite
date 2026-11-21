package com.painuo.suite.controller;

import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.Result;
import com.painuo.suite.dto.ActivityDtos;
import com.painuo.suite.security.RequirePerm;
import com.painuo.suite.service.SignupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约报名与核销。
 *
 * <p>报名与"我的报名"只要登录态（登录用户就是报名人，userId 从 token 取，
 * 不接受前端传 userId，避免越权替别人报名）；
 * 报名列表与核销属于运营动作，需要 {@code signup:list} / {@code signup:verify} 按钮权限。</p>
 */
@RestController
public class SignupController {

    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    /** 报名抢名额（并发安全，详见 SignupTransactionalService）。 */
    @PostMapping("/api/signups")
    public Result<ActivityDtos.SignupVO> signup(@Valid @RequestBody ActivityDtos.SignupRequest request) {
        return Result.ok(signupService.signup(request), "报名成功");
    }

    @GetMapping("/api/signups/mine")
    public Result<PageResult<ActivityDtos.SignupVO>> mine(@RequestParam(required = false) Integer status,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return Result.ok(signupService.mySignups(status, PageQuery.of(page, size, null)));
    }

    @DeleteMapping("/api/signups/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        signupService.cancel(id);
        return Result.ok(null, "已取消报名，名额已释放");
    }

    @GetMapping("/api/signups")
    @RequirePerm("signup:list")
    public Result<PageResult<ActivityDtos.SignupVO>> page(@RequestParam(required = false) Long activityId,
                                                          @RequestParam(required = false) Long sessionId,
                                                          @RequestParam(required = false) Integer status,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return Result.ok(signupService.pageForAdmin(activityId, sessionId, status, keyword,
                PageQuery.of(page, size, null)));
    }

    /** 核销：运营在后台输入核销码，或小程序扫码后回调。 */
    @PostMapping("/api/signups/verify")
    @RequirePerm("signup:verify")
    public Result<ActivityDtos.VerifyVO> verify(@Valid @RequestBody ActivityDtos.VerifyRequest request) {
        return Result.ok(signupService.verify(request.code()), "核销成功");
    }
}
