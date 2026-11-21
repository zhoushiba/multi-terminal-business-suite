package com.painuo.suite.common;

/**
 * 经纬度计算工具。
 *
 * <p>为什么不用 MySQL 的 {@code ST_Distance_Sphere}？两个原因：</p>
 * <ol>
 *   <li>测试环境是 H2，空间函数与 MySQL 不完全一致，会让"测试通过但线上不对"；</li>
 *   <li>边界框查询已经把候选集压到几十个点，Java 里算 Haversine 的开销可以忽略。</li>
 * </ol>
 *
 * <p>精度：Haversine 用球面近似，地球半径取 6371km，在几公里范围内误差小于 0.5%，
 * 对"附近有哪些景区"足够；真要高精度需换 Vincenty 或 PostGIS。</p>
 */
public final class GeoUtils {

    /** 地球平均半径（米）。 */
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private GeoUtils() {
    }

    /**
     * 两点间球面距离。
     *
     * @return 米
     */
    public static double distanceMeters(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double deltaLat = radLat2 - radLat1;
        double deltaLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(deltaLng / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1d, Math.sqrt(a)));
    }

    /**
     * 按"中心点 + 半径"反推边界框，用于前端地图视口查询。
     *
     * @param radiusMeters 半径（米）
     * @return {@code [minLng, maxLng, minLat, maxLat]}
     */
    public static double[] boundingBox(double centerLng, double centerLat, double radiusMeters) {
        double latDelta = Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS);
        double lngDelta = latDelta / Math.max(0.01d, Math.cos(Math.toRadians(centerLat)));
        return new double[]{centerLng - lngDelta, centerLng + lngDelta, centerLat - latDelta, centerLat + latDelta};
    }

    /** 距离的人类可读格式：{@code 350m} / {@code 1.2km}。 */
    public static String humanize(double meters) {
        if (meters < 1000) {
            return Math.round(meters) + "m";
        }
        return String.format("%.1fkm", meters / 1000);
    }
}
