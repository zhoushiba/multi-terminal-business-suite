package com.painuo.suite.controller;

import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.Result;
import com.painuo.suite.dto.GeoDtos;
import com.painuo.suite.security.RequirePerm;
import com.painuo.suite.service.GeoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地理点位与区域树。
 *
 * <p>点位是"文旅内容 + 遥感地块"两类业务的公共载体：景区景点用于内容导流，
 * 遥感地块用于数据服务展示，两者共用一张 geo_point 表，用 type 区分。</p>
 */
@RestController
public class GeoController {

    private final GeoService geoService;

    public GeoController(GeoService geoService) {
        this.geoService = geoService;
    }

    // ------------------------------------------------------------------ 后台

    @GetMapping("/api/points")
    @RequirePerm("geo:list")
    public Result<PageResult<GeoDtos.PointVO>> page(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long regionId,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return Result.ok(geoService.pageForAdmin(keyword, regionId, type, PageQuery.of(page, size, null)));
    }

    @PostMapping("/api/points")
    @RequirePerm("geo:write")
    public Result<GeoDtos.PointVO> create(@Valid @RequestBody GeoDtos.PointSaveRequest request) {
        return Result.ok(geoService.create(request), "点位已创建");
    }

    @PutMapping("/api/points/{id}")
    @RequirePerm("geo:write")
    public Result<GeoDtos.PointVO> update(@PathVariable Long id,
                                          @Valid @RequestBody GeoDtos.PointSaveRequest request) {
        return Result.ok(geoService.update(id, request), "点位已更新");
    }

    @DeleteMapping("/api/points/{id}")
    @RequirePerm("geo:write")
    public Result<Void> delete(@PathVariable Long id) {
        geoService.delete(id);
        return Result.ok(null, "点位已删除");
    }

    // ------------------------------------------------------------------ C 端（免鉴权）

    @GetMapping("/api/public/regions")
    public Result<List<GeoDtos.RegionVO>> regions() {
        return Result.ok(geoService.regionTree());
    }

    /**
     * 附近点位：小程序首页"周边"用。
     *
     * @param lng    当前经度
     * @param lat    当前纬度
     * @param radius 半径（米），默认 5000
     */
    @GetMapping("/api/public/points/nearby")
    public Result<List<GeoDtos.PointVO>> nearby(@RequestParam Double lng,
                                                @RequestParam Double lat,
                                                @RequestParam(required = false) Double radius,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) Integer limit) {
        return Result.ok(geoService.nearby(lng, lat, radius, type, limit));
    }

    /** 地图打点（运营后台点位地图 / 小程序地图页）。 */
    @GetMapping("/api/public/points/map")
    public Result<List<GeoDtos.PointVO>> map(@RequestParam(required = false) String type,
                                             @RequestParam(required = false) Long regionId) {
        return Result.ok(geoService.mapPoints(type, regionId));
    }
}
