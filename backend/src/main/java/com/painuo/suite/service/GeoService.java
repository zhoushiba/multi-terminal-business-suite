package com.painuo.suite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.painuo.suite.cache.CacheKeys;
import com.painuo.suite.cache.CacheService;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.GeoUtils;
import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.config.AppProperties;
import com.painuo.suite.dto.GeoDtos;
import com.painuo.suite.entity.GeoPoint;
import com.painuo.suite.entity.GeoRegion;
import com.painuo.suite.mapper.GeoPointMapper;
import com.painuo.suite.mapper.GeoRegionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 地理点位与区域树。
 *
 * <p>没有引入 PostGIS / Redis GEO，而是用"<b>边界框先粗筛 + Haversine 再精确算距离</b>"：
 * 先用 lng/lat 的 BETWEEN 走索引把候选集压到很小，再在内存里算球面距离并排序。
 * 数据量到十万级再换 R 树或 PG 的 GiST 索引，成本可控。</p>
 */
@Service
public class GeoService {

    private static final Logger log = LoggerFactory.getLogger(GeoService.class);

    /** 点位类型 → 中文名，前端不用再维护一份字典。 */
    private static final Map<String, String> TYPE_TEXT = Map.of(
            "scenic", "景区景点",
            "heritage", "人文遗迹",
            "plot", "遥感地块",
            "service", "服务设施");

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    /** 单次查询允许的最大半径（米），防止"传个 1000 万米扫全表"。 */
    private static final double MAX_RADIUS_METERS = 200_000;

    private final GeoPointMapper pointMapper;
    private final GeoRegionMapper regionMapper;
    private final CacheService cache;
    private final ObjectMapper objectMapper;
    private final AppProperties.Cache cacheProps;

    public GeoService(GeoPointMapper pointMapper,
                      GeoRegionMapper regionMapper,
                      CacheService cache,
                      ObjectMapper objectMapper,
                      AppProperties appProperties) {
        this.pointMapper = pointMapper;
        this.regionMapper = regionMapper;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.cacheProps = appProperties.cache();
    }

    // ------------------------------------------------------------------ 区域树

    /** 区域树（省 → 市 → 区县，层数由数据决定）。 */
    public List<GeoDtos.RegionVO> regionTree() {
        List<GeoRegion> all = regionMapper.selectList(Wrappers.<GeoRegion>lambdaQuery()
                .orderByAsc(GeoRegion::getLevel).orderByAsc(GeoRegion::getId));
        Map<Long, List<GeoRegion>> byParent = new LinkedHashMap<>();
        for (GeoRegion region : all) {
            byParent.computeIfAbsent(region.getParentId() == null ? 0L : region.getParentId(),
                    key -> new ArrayList<>()).add(region);
        }
        return buildTree(byParent, 0L);
    }

    // ------------------------------------------------------------------ 附近点位

    /**
     * 附近点位：边界框粗筛 + 球面距离精排，结果带 TTL 抖动缓存。
     *
     * @param lng         中心经度
     * @param lat         中心纬度
     * @param radiusMeters 半径（米），默认 5km
     * @param type        可选类型过滤
     */
    public List<GeoDtos.PointVO> nearby(Double lng, Double lat, Double radiusMeters, String type, Integer limit) {
        if (lng == null || lat == null) {
            throw BizException.param("经纬度不能为空");
        }
        if (Math.abs(lng) > 180 || Math.abs(lat) > 90) {
            throw BizException.param("经纬度超出合法范围");
        }
        double radius = radiusMeters == null || radiusMeters <= 0 ? 5_000 : radiusMeters;
        if (radius > MAX_RADIUS_METERS) {
            throw BizException.param("查询半径不能超过 " + (int) MAX_RADIUS_METERS + " 米");
        }
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        // 坐标保留 4 位小数（约 11 米）做缓存键，避免每个 GPS 抖动都打穿缓存
        String cacheKey = CacheKeys.pointList(String.format("%s|%.4f|%.4f|%.0f|%d",
                type == null ? "all" : type, lng, lat, radius, size));
        List<GeoDtos.PointVO> cached = readListCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        double[] box = GeoUtils.boundingBox(lng, lat, radius);
        List<GeoPoint> candidates = pointMapper.selectByBbox(box[0], box[1], box[2], box[3], MAX_LIMIT);
        Map<Long, String> regionNames = regionNameMap(candidates);

        List<GeoDtos.PointVO> result = candidates.stream()
                .filter(point -> type == null || type.isBlank() || type.equals(point.getType()))
                .map(point -> {
                    double meters = GeoUtils.distanceMeters(lng, lat, point.getLng(), point.getLat());
                    return new DistanceHolder(point, meters);
                })
                .filter(holder -> holder.meters() <= radius)
                .sorted(Comparator.comparingDouble(DistanceHolder::meters))
                .limit(size)
                .map(holder -> toVO(holder.point(), regionNames.get(holder.point().getRegionId()), holder.meters()))
                .toList();

        writeListCache(cacheKey, result);
        return result;
    }

    /** 后台点位分页列表。 */
    public PageResult<GeoDtos.PointVO> pageForAdmin(String keyword, Long regionId, String type, PageQuery pageQuery) {
        LambdaQueryWrapper<GeoPoint> wrapper = Wrappers.<GeoPoint>lambdaQuery()
                .like(hasText(keyword), GeoPoint::getName, keyword)
                .eq(regionId != null, GeoPoint::getRegionId, regionId)
                .eq(hasText(type), GeoPoint::getType, type)
                .orderByDesc(GeoPoint::getId);
        Page<GeoPoint> page = pointMapper.selectPage(pageQuery.toPage(), wrapper);
        Map<Long, String> regionNames = regionNameMap(page.getRecords());
        List<GeoDtos.PointVO> items = page.getRecords().stream()
                .map(point -> toVO(point, regionNames.get(point.getRegionId()), null))
                .toList();
        return PageResult.of(items, page.getCurrent(), page.getSize(), page.getTotal());
    }

    /** 地图打点用的轻量列表（只返回需要画的字段，一次最多 500 个）。 */
    public List<GeoDtos.PointVO> mapPoints(String type, Long regionId) {
        List<GeoPoint> points = pointMapper.selectList(Wrappers.<GeoPoint>lambdaQuery()
                .eq(hasText(type), GeoPoint::getType, type)
                .eq(regionId != null, GeoPoint::getRegionId, regionId)
                .eq(GeoPoint::getStatus, 1)
                .orderByDesc(GeoPoint::getId)
                .last("LIMIT 500"));
        Map<Long, String> regionNames = regionNameMap(points);
        return points.stream().map(point -> toVO(point, regionNames.get(point.getRegionId()), null)).toList();
    }

    // ------------------------------------------------------------------ 写操作

    @Transactional
    public GeoDtos.PointVO create(GeoDtos.PointSaveRequest request) {
        requireRegion(request.regionId());
        GeoPoint point = new GeoPoint();
        applyRequest(point, request);
        pointMapper.insert(point);
        evictList();
        return toVO(point, regionName(point.getRegionId()), null);
    }

    @Transactional
    public GeoDtos.PointVO update(Long id, GeoDtos.PointSaveRequest request) {
        GeoPoint existing = requirePoint(id);
        requireRegion(request.regionId());
        applyRequest(existing, request);
        existing.setId(id);
        pointMapper.updateById(existing);
        evictList();
        return toVO(existing, regionName(existing.getRegionId()), null);
    }

    @Transactional
    public void delete(Long id) {
        requirePoint(id);
        pointMapper.deleteById(id);
        evictList();
    }

    // ------------------------------------------------------------------ 内部方法

    private void applyRequest(GeoPoint point, GeoDtos.PointSaveRequest request) {
        point.setName(request.name());
        point.setRegionId(request.regionId());
        point.setType(request.type());
        point.setLng(request.lng());
        point.setLat(request.lat());
        point.setAddress(request.address());
        point.setCoverUrl(request.coverUrl());
        point.setSummary(request.summary());
        point.setStatus(request.status() == null ? 1 : request.status());
    }

    private Map<Long, String> regionNameMap(List<GeoPoint> points) {
        List<Long> ids = points.stream()
                .map(GeoPoint::getRegionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<GeoRegion> regions = regionMapper.selectBatchIds(ids);
        Map<Long, String> map = new HashMap<>(regions.size());
        for (GeoRegion region : regions) {
            map.put(region.getId(), region.getName());
        }
        return map;
    }

    private String regionName(Long regionId) {
        GeoRegion region = regionId == null ? null : regionMapper.selectById(regionId);
        return region == null ? null : region.getName();
    }

    private GeoPoint requirePoint(Long id) {
        GeoPoint point = pointMapper.selectById(id);
        if (point == null) {
            throw BizException.notFound("点位");
        }
        return point;
    }

    private void requireRegion(Long regionId) {
        if (regionId == null || regionMapper.selectById(regionId) == null) {
            throw BizException.param("区域不存在: " + regionId);
        }
    }

    private GeoDtos.PointVO toVO(GeoPoint point, String regionName, Double distanceMeters) {
        return new GeoDtos.PointVO(point.getId(), point.getName(), point.getRegionId(), regionName,
                point.getType(), TYPE_TEXT.getOrDefault(point.getType(), point.getType()),
                point.getLng(), point.getLat(), point.getAddress(), point.getCoverUrl(), point.getSummary(),
                point.getStatus(), distanceMeters,
                distanceMeters == null ? null : GeoUtils.humanize(distanceMeters),
                point.getCreatedAt());
    }

    private List<GeoDtos.RegionVO> buildTree(Map<Long, List<GeoRegion>> byParent, Long parentId) {
        List<GeoRegion> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        return children.stream()
                .map(region -> new GeoDtos.RegionVO(region.getId(), region.getName(), region.getCode(),
                        region.getLevel(), region.getCenterLng(), region.getCenterLat(),
                        buildTree(byParent, region.getId())))
                .toList();
    }

    /** 点位列表变化时清掉所有附近查询缓存（按前缀扫，内存实现与 Redis 都支持不了的场景退化为只清自己）。 */
    private void evictList() {
        // 内存缓存实现无前缀扫描，这里退化为依赖 TTL 自然过期；
        // Redis 实现可通过 SCAN 清理，见 docs/architecture.md 的说明。
        log.debug("点位数据变更，附近查询缓存将在 TTL 到期后自然失效");
    }

    private List<GeoDtos.PointVO> readListCache(String key) {
        String json = cache.get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<GeoDtos.PointVO>>() {
            });
        } catch (JsonProcessingException ex) {
            log.warn("点位缓存反序列化失败，忽略缓存", ex);
            cache.delete(key);
            return null;
        }
    }

    private void writeListCache(String key, List<GeoDtos.PointVO> value) {
        try {
            cache.set(key, objectMapper.writeValueAsString(value), cacheProps.defaultTtlSeconds());
        } catch (JsonProcessingException ex) {
            log.warn("点位缓存序列化失败，跳过缓存", ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 排序辅助：保留原始实体与计算出的距离。 */
    private record DistanceHolder(GeoPoint point, double meters) {
    }
}
