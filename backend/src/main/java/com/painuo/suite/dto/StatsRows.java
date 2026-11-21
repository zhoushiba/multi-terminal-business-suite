package com.painuo.suite.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 统计看板的 SQL 投影行。
 *
 * <p>用带 setter 的 POJO 而不是 record/Map：MyBatis 的
 * {@code argNameBasedConstructorAutoMapping} 默认关闭，record 构造映射容易踩坑；
 * 而 {@code Map<String,Object>} 又会把 COUNT 变成 BigDecimal/Long 不定的类型。
 * POJO + {@code mapUnderscoreToCamelCase} 是最稳的组合。</p>
 */
public final class StatsRows {

    private StatsRows() {
    }

    /** 内容发布趋势：一天的发布量。 */
    @Data
    public static class DateCountRow {
        private LocalDate statDate;
        private Long total;
    }

    /** 排名行：分类内容量 / 区域点位数。 */
    @Data
    public static class RankRow {
        private String name;
        private Long total;
        /** 由 RANK() 窗口函数给出，用于前端高亮前三。 */
        private Long rankNo;
    }

    /** 预约转化：每场活动中每个场次的容量与报名数。 */
    @Data
    public static class ConversionRow {
        private String activityTitle;
        private String sessionName;
        private Integer capacity;
        private Long signed;
        private BigDecimal conversionRate;
        private Long rankNo;
    }
}
