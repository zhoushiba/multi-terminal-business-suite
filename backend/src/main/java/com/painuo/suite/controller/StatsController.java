package com.painuo.suite.controller;

import com.painuo.suite.common.Result;
import com.painuo.suite.dto.StatsDtos;
import com.painuo.suite.security.RequirePerm;
import com.painuo.suite.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板。
 *
 * <p>只暴露一个聚合接口：首页一次请求拿齐指标卡、趋势、分类排行、区域分布、转化率，
 * 减少小程序/后台首屏的往返次数。</p>
 */
@RestController
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * @param days 趋势图天数，默认 14，上限 90
     */
    @GetMapping("/api/stats/dashboard")
    @RequirePerm("stats:view")
    public Result<StatsDtos.DashboardVO> dashboard(@RequestParam(required = false) Integer days) {
        return Result.ok(statsService.dashboard(days));
    }

    @GetMapping("/api/stats/totals")
    @RequirePerm("stats:view")
    public Result<StatsDtos.TotalsVO> totals() {
        return Result.ok(statsService.totals());
    }
}
