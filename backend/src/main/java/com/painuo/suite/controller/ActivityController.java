package com.painuo.suite.controller;

import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.Result;
import com.painuo.suite.dto.ActivityDtos;
import com.painuo.suite.security.RequirePerm;
import com.painuo.suite.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动与场次管理。
 *
 * <p>名额（remain）没有"直接编辑"的接口，只能通过报名/取消/改容量三条受控路径变化，
 * 这样任何一次变动都是可追溯的。</p>
 */
@RestController
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    // ------------------------------------------------------------------ 后台

    @GetMapping("/api/activities")
    @RequirePerm("activity:list")
    public Result<PageResult<ActivityDtos.ActivityVO>> page(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) Integer status,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(activityService.pageForAdmin(keyword, status, PageQuery.of(page, size, null)));
    }

    @GetMapping("/api/activities/{id}")
    @RequirePerm("activity:list")
    public Result<ActivityDtos.ActivityVO> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }

    @PostMapping("/api/activities")
    @RequirePerm("activity:write")
    public Result<ActivityDtos.ActivityVO> create(@Valid @RequestBody ActivityDtos.ActivitySaveRequest request) {
        return Result.ok(activityService.create(request), "活动已创建");
    }

    @PutMapping("/api/activities/{id}")
    @RequirePerm("activity:write")
    public Result<ActivityDtos.ActivityVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody ActivityDtos.ActivitySaveRequest request) {
        return Result.ok(activityService.update(id, request), "活动已更新");
    }

    @DeleteMapping("/api/activities/{id}")
    @RequirePerm("activity:write")
    public Result<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return Result.ok(null, "活动已删除");
    }

    // ------------------------------------------------------------------ 场次

    @PostMapping("/api/activities/{id}/sessions")
    @RequirePerm("activity:write")
    public Result<ActivityDtos.SessionVO> createSession(@PathVariable Long id,
                                                        @Valid @RequestBody ActivityDtos.SessionSaveRequest request) {
        return Result.ok(activityService.createSession(id, request), "场次已创建");
    }

    @PutMapping("/api/sessions/{id}")
    @RequirePerm("activity:write")
    public Result<ActivityDtos.SessionVO> updateSession(@PathVariable Long id,
                                                        @Valid @RequestBody ActivityDtos.SessionSaveRequest request) {
        return Result.ok(activityService.updateSession(id, request), "场次已更新");
    }

    @DeleteMapping("/api/sessions/{id}")
    @RequirePerm("activity:write")
    public Result<Void> deleteSession(@PathVariable Long id) {
        activityService.deleteSession(id);
        return Result.ok(null, "场次已删除");
    }

    // ------------------------------------------------------------------ C 端

    @GetMapping("/api/public/activities")
    public Result<PageResult<ActivityDtos.ActivityVO>> publicPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(activityService.pagePublished(PageQuery.of(page, size, null)));
    }

    @GetMapping("/api/public/activities/{id}")
    public Result<ActivityDtos.ActivityVO> publicDetail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }
}
