package com.painuo.suite.mapper;

import com.painuo.suite.dto.StatsRows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板聚合查询。
 *
 * <p>刻意保留手写 SQL：MyBatis-Plus 的 Wrapper 只能做单表条件查询，
 * 而看板需要 GROUP BY、LEFT JOIN 和窗口函数，这部分用 XML/注解更直观。</p>
 */
@Mapper
public interface StatsMapper {

    /**
     * 内容发布趋势（按天）。
     *
     * <p>{@code CAST(publish_time AS DATE)} 在 MySQL 8 与 H2(MySQL 模式) 下行为一致，
     * 所以单测可以直接用 H2 验证这段 SQL。</p>
     */
    @Select("""
            SELECT CAST(publish_time AS DATE) AS stat_date, COUNT(*) AS total
            FROM cms_content
            WHERE deleted = 0 AND status = 2 AND publish_time IS NOT NULL AND publish_time >= #{from}
            GROUP BY CAST(publish_time AS DATE)
            ORDER BY stat_date ASC
            """)
    List<StatsRows.DateCountRow> contentTrend(@Param("from") LocalDateTime from);

    /** 全站浏览量总和（首页大数字用，单表聚合）。 */
    @Select("SELECT COALESCE(SUM(view_count), 0) FROM cms_content WHERE deleted = 0")
    Long totalViews();

    /** 分类内容排行：一条 LEFT JOIN 聚合代替 N 次 count。 */
    @Select("""
            SELECT c.name AS name, COUNT(x.id) AS total
            FROM cms_category c
            LEFT JOIN cms_content x ON x.category_id = c.id AND x.deleted = 0
            WHERE c.deleted = 0
            GROUP BY c.id, c.name
            ORDER BY total DESC, c.id ASC
            """)
    List<StatsRows.RankRow> categoryRank();

    /**
     * 区域点位分布（含窗口函数）。
     *
     * <p>{@code RANK() OVER (ORDER BY COUNT(...) DESC)} 让前端可以直接按名次上色，
     * 不必在 JS 里再排一次序——也顺便满足了"SQL 聚合 + 窗口函数"的技术点。</p>
     */
    @Select("""
            SELECT r.name AS name,
                   COUNT(p.id) AS total,
                   RANK() OVER (ORDER BY COUNT(p.id) DESC) AS rank_no
            FROM geo_region r
            LEFT JOIN geo_point p ON p.region_id = r.id AND p.deleted = 0 AND p.status = 1
            GROUP BY r.id, r.name
            ORDER BY total DESC, r.id ASC
            """)
    List<StatsRows.RankRow> regionDistribution();

    /**
     * 预约转化率。
     *
     * <p>有效报名数 = status IN (1,2)（已报名 + 已核销，不含已取消）；
     * 转化率在 SQL 里算好并保留两位小数，避免前端除零。</p>
     */
    @Select("""
            SELECT a.title AS activity_title,
                   s.name AS session_name,
                   s.capacity AS capacity,
                   COUNT(g.id) AS signed,
                   CASE WHEN s.capacity IS NULL OR s.capacity = 0 THEN 0
                        ELSE ROUND(COUNT(g.id) * 100.0 / s.capacity, 2) END AS conversion_rate,
                   RANK() OVER (ORDER BY COUNT(g.id) DESC) AS rank_no
            FROM activity_session s
            JOIN activity a ON a.id = s.activity_id AND a.deleted = 0
            LEFT JOIN activity_signup g ON g.session_id = s.id AND g.status IN (1, 2)
            GROUP BY a.id, a.title, s.id, s.name, s.capacity
            ORDER BY conversion_rate DESC, s.id ASC
            LIMIT #{limit}
            """)
    List<StatsRows.ConversionRow> activityConversion(@Param("limit") int limit);
}
