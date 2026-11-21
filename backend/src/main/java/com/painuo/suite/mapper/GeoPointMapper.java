package com.painuo.suite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.painuo.suite.entity.GeoPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GeoPointMapper extends BaseMapper<GeoPoint> {

    /**
     * 边界框（bounding box）粗筛。
     *
     * <p>移动地图时前端把视口四个角传过来，先用
     * {@code idx_geo_bbox(lng, lat)} 走范围扫描把候选点压到几十个，
     * 再在应用层算 Haversine 距离做精筛与排序——
     * 这比每行调用一次空间函数快，也不依赖 MySQL 空间扩展。</p>
     *
     * <p>注意：自定义 SQL 不会自动追加 {@code deleted = 0}，必须手写（本项目已写）。</p>
     */
    @Select("""
            SELECT * FROM geo_point
             WHERE deleted = 0 AND status = 1
               AND lng BETWEEN #{minLng} AND #{maxLng}
               AND lat BETWEEN #{minLat} AND #{maxLat}
             ORDER BY id DESC
             LIMIT #{limit}
            """)
    List<GeoPoint> selectByBbox(@Param("minLng") double minLng,
                                @Param("maxLng") double maxLng,
                                @Param("minLat") double minLat,
                                @Param("maxLat") double maxLat,
                                @Param("limit") int limit);
}
