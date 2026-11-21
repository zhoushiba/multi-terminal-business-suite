package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 地理点位（文旅资源点 / 遥感地块）。
 *
 * <p>查询模式：先按经纬度边界框（走 {@code idx_geo_bbox} 复合索引）粗筛，
 * 再在内存里做 Haversine 精算距离——比直接 {@code ST_Distance} 更可控，
 * 且 H2/MySQL 行为一致。</p>
 */
@Data
@TableName("geo_point")
public class GeoPoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long regionId;

    /** scenic 景区 / heritage 文物 / plot 遥感地块 / service 服务设施。 */
    private String type;

    private Double lng;

    private Double lat;

    private String address;

    private String coverUrl;

    private String summary;

    /** 1 上架 / 0 下架。 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
}
