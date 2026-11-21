package com.painuo.suite.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 地理点位与区域树出入参。 */
public final class GeoDtos {

    private GeoDtos() {
    }

    /** 新增/编辑点位。 */
    public record PointSaveRequest(
            @NotBlank(message = "点位名称不能为空")
            @Size(max = 100, message = "点位名称最长 100 字")
            String name,

            @NotNull(message = "所属区域不能为空")
            Long regionId,

            @NotBlank(message = "点位类型不能为空")
            @Pattern(regexp = "scenic|heritage|plot|service", message = "点位类型只能是 scenic/heritage/plot/service")
            String type,

            @NotNull(message = "经度不能为空")
            @DecimalMin(value = "-180.0", message = "经度范围 -180~180")
            @DecimalMax(value = "180.0", message = "经度范围 -180~180")
            Double lng,

            @NotNull(message = "纬度不能为空")
            @DecimalMin(value = "-90.0", message = "纬度范围 -90~90")
            @DecimalMax(value = "90.0", message = "纬度范围 -90~90")
            Double lat,

            @Size(max = 200, message = "地址最长 200 字")
            String address,

            @Size(max = 500, message = "封面地址过长")
            String coverUrl,

            @Size(max = 1000, message = "简介最长 1000 字")
            String summary,

            Integer status
    ) {
    }

    /** 点位视图（含距离，用于小程序"附近"列表）。 */
    public record PointVO(
            Long id,
            String name,
            Long regionId,
            String regionName,
            String type,
            String typeText,
            Double lng,
            Double lat,
            String address,
            String coverUrl,
            String summary,
            Integer status,
            Double distanceMeters,
            String distanceText,
            LocalDateTime createdAt
    ) {
    }

    /** 区域树节点。 */
    public record RegionVO(
            Long id,
            String name,
            String code,
            Integer level,
            Double centerLng,
            Double centerLat,
            List<RegionVO> children
    ) {
    }
}
