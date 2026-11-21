package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行政区划（区域树）。
 *
 * <p>{@code centerLng/centerLat} 让"点位地图"在没加载点位前就能定位到区域中心，
 * 也用于计算"某区域半径内有多少点位"。</p>
 */
@Data
@TableName("geo_region")
public class GeoRegion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 国标行政区划代码，如 520103（云岩区）。 */
    private String code;

    private Long parentId;

    /** 1 省 / 2 市 / 3 区县。 */
    private Integer level;

    private Double centerLng;

    private Double centerLat;

    private LocalDateTime createdAt;
}
