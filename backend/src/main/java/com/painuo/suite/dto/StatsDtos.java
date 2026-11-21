package com.painuo.suite.dto;

import java.math.BigDecimal;
import java.util.List;

/** 数据看板出入参（只出参）。 */
public final class StatsDtos {

    private StatsDtos() {
    }

    /** 总览指标。 */
    public record TotalsVO(
            long contentTotal,
            long publishedTotal,
            long pendingTotal,
            long activityTotal,
            long signupTotal,
            long verifiedTotal,
            long pointTotal,
            long userTotal,
            long viewTotal
    ) {
    }

    /** 趋势点（按天）。 */
    public record TrendPoint(String date, long count) {
    }

    /** 排名项（分类 / 区域）。 */
    public record RankItem(String name, long value) {
    }

    /** 场次转化：报名数 / 总名额。 */
    public record ConversionVO(
            String activityTitle,
            String sessionName,
            int capacity,
            int signed,
            BigDecimal conversionRate
    ) {
    }

    /** 看板聚合结果。 */
    public record DashboardVO(
            TotalsVO totals,
            List<TrendPoint> contentTrend,
            List<RankItem> categoryRank,
            List<RankItem> regionDistribution,
            List<ConversionVO> activityConversion
    ) {
    }
}
