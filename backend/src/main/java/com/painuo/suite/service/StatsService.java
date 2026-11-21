package com.painuo.suite.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.painuo.suite.cache.CacheKeys;
import com.painuo.suite.cache.CacheService;
import com.painuo.suite.common.ContentStatus;
import com.painuo.suite.dto.StatsDtos;
import com.painuo.suite.dto.StatsRows;
import com.painuo.suite.entity.Activity;
import com.painuo.suite.entity.ActivitySignup;
import com.painuo.suite.entity.CmsContent;
import com.painuo.suite.entity.GeoPoint;
import com.painuo.suite.entity.SysUser;
import com.painuo.suite.mapper.ActivityMapper;
import com.painuo.suite.mapper.ActivitySignupMapper;
import com.painuo.suite.mapper.CmsContentMapper;
import com.painuo.suite.mapper.GeoPointMapper;
import com.painuo.suite.mapper.StatsMapper;
import com.painuo.suite.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营看板。
 *
 * <p>看板是典型的"读多写少、允许秒级延迟"场景，所以整体走缓存：
 * 整个 DashboardVO 序列化后缓存 {@code app.cache.default-ttl-seconds} 秒（默认 5 分钟，
 * 且带 ±比例抖动，避免整点同时过期）。真要做实时数字应该换成 30 秒 TTL 或前端轮询增量接口。</p>
 */
@Service
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);

    /** 趋势图默认展示最近 14 天。 */
    private static final int TREND_DAYS = 14;
    /** 转化率榜单最多返回 8 条，避免看板一屏塞满。 */
    private static final int CONVERSION_LIMIT = 8;
    private static final String DASHBOARD_CACHE_KEY = "dashboard";

    private final StatsMapper statsMapper;
    private final CmsContentMapper contentMapper;
    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final GeoPointMapper pointMapper;
    private final SysUserMapper userMapper;
    private final CacheService cache;
    private final ObjectMapper objectMapper;

    public StatsService(StatsMapper statsMapper,
                        CmsContentMapper contentMapper,
                        ActivityMapper activityMapper,
                        ActivitySignupMapper signupMapper,
                        GeoPointMapper pointMapper,
                        SysUserMapper userMapper,
                        CacheService cache,
                        ObjectMapper objectMapper) {
        this.statsMapper = statsMapper;
        this.contentMapper = contentMapper;
        this.activityMapper = activityMapper;
        this.signupMapper = signupMapper;
        this.pointMapper = pointMapper;
        this.userMapper = userMapper;
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    /** 看板全量数据（一次请求返回 5 个区块，前端只发一个请求）。 */
    public StatsDtos.DashboardVO dashboard(Integer days) {
        int trendDays = days == null || days <= 0 ? TREND_DAYS : Math.min(days, 90);
        String key = CacheKeys.stats(DASHBOARD_CACHE_KEY + ":" + trendDays);
        String cached = cache.get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, StatsDtos.DashboardVO.class);
            } catch (JsonProcessingException ex) {
                log.warn("看板缓存反序列化失败，回源统计", ex);
                cache.delete(key);
            }
        }
        StatsDtos.DashboardVO dashboard = buildDashboard(trendDays);
        try {
            cache.set(key, objectMapper.writeValueAsString(dashboard), 120);
        } catch (JsonProcessingException ex) {
            log.warn("看板数据序列化失败，跳过缓存", ex);
        }
        return dashboard;
    }

    private StatsDtos.DashboardVO buildDashboard(int trendDays) {
        StatsDtos.TotalsVO totals = totals();
        List<StatsDtos.TrendPoint> trend = fillTrend(statsMapper.contentTrend(
                LocalDateTime.now().minusDays(trendDays - 1L).toLocalDate().atStartOfDay()), trendDays);
        List<StatsDtos.RankItem> categoryRank = statsMapper.categoryRank().stream()
                .map(row -> new StatsDtos.RankItem(row.getName(), row.getTotal()))
                .toList();
        List<StatsDtos.RankItem> regionDistribution = statsMapper.regionDistribution().stream()
                .map(row -> new StatsDtos.RankItem(row.getName(), row.getTotal()))
                .toList();
        List<StatsDtos.ConversionVO> conversions = statsMapper.activityConversion(CONVERSION_LIMIT).stream()
                .map(this::toConversion)
                .toList();
        return new StatsDtos.DashboardVO(totals, trend, categoryRank, regionDistribution, conversions);
    }

    /** 顶部指标卡：8 个 count + 1 个聚合。 */
    public StatsDtos.TotalsVO totals() {
        long contentTotal = count(contentMapper.selectCount(Wrappers.<CmsContent>lambdaQuery()));
        long published = count(contentMapper.selectCount(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getStatus, ContentStatus.PUBLISHED.code())));
        long pending = count(contentMapper.selectCount(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getStatus, ContentStatus.PENDING.code())));
        long activityTotal = count(activityMapper.selectCount(Wrappers.<Activity>lambdaQuery()));
        long signupTotal = count(signupMapper.selectCount(Wrappers.<ActivitySignup>lambdaQuery()));
        long verified = count(signupMapper.selectCount(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getStatus, 2)));
        long pointTotal = count(pointMapper.selectCount(Wrappers.<GeoPoint>lambdaQuery()));
        long userTotal = count(userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()));
        Long views = statsMapper.totalViews();
        return new StatsDtos.TotalsVO(contentTotal, published, pending, activityTotal, signupTotal,
                verified, pointTotal, userTotal, views == null ? 0L : views);
    }

    /**
     * 补齐没有数据的日期。
     *
     * <p>SQL 只返回"有发布记录的日期"，直接丢给 ECharts 会出现断点；
     * 这里按自然日补齐 0，保证折线连续。</p>
     */
    private List<StatsDtos.TrendPoint> fillTrend(List<StatsRows.DateCountRow> rows, int days) {
        Map<LocalDate, Long> byDate = new HashMap<>();
        for (StatsRows.DateCountRow row : rows) {
            if (row.getStatDate() != null) {
                byDate.put(row.getStatDate(), row.getTotal());
            }
        }
        List<StatsDtos.TrendPoint> points = new ArrayList<>(days);
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            points.add(new StatsDtos.TrendPoint(date.toString(), byDate.getOrDefault(date, 0L)));
        }
        return points;
    }

    private StatsDtos.ConversionVO toConversion(StatsRows.ConversionRow row) {
        return new StatsDtos.ConversionVO(row.getActivityTitle(), row.getSessionName(),
                row.getCapacity() == null ? 0 : row.getCapacity(),
                row.getSigned() == null ? 0 : row.getSigned().intValue(),
                row.getConversionRate());
    }

    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
