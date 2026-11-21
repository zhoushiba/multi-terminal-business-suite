package com.painuo.suite.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 地理计算单元测试。
 *
 * <p>距离用真实坐标校验数量级：贵阳到黄果树约 118km，误差允许 10%，
 * 既能拦住"经纬度顺序写反"这类低级错误，又不会因 Haversine 球面近似而误报。</p>
 */
@DisplayName("地理计算工具")
class GeoUtilsTest {

    private static final double GUIYANG_LNG = 106.6302;
    private static final double GUIYANG_LAT = 26.6477;
    private static final double HUANGGUOSHU_LNG = 105.6670;
    private static final double HUANGGUOSHU_LAT = 25.9960;

    @Test
    @DisplayName("同一点距离为 0")
    void samePointIsZero() {
        assertThat(GeoUtils.distanceMeters(GUIYANG_LNG, GUIYANG_LAT, GUIYANG_LNG, GUIYANG_LAT)).isZero();
    }

    @Test
    @DisplayName("贵阳到黄果树约 118 公里（允许 10% 误差）")
    void distanceBetweenCities() {
        double meters = GeoUtils.distanceMeters(GUIYANG_LNG, GUIYANG_LAT, HUANGGUOSHU_LNG, HUANGGUOSHU_LAT);
        assertThat(meters).isBetween(106_000d, 130_000d);
    }

    @Test
    @DisplayName("距离计算对称，且不会因为经纬度写反而算成 0")
    void distanceIsSymmetric() {
        double forward = GeoUtils.distanceMeters(GUIYANG_LNG, GUIYANG_LAT, HUANGGUOSHU_LNG, HUANGGUOSHU_LAT);
        double backward = GeoUtils.distanceMeters(HUANGGUOSHU_LNG, HUANGGUOSHU_LAT, GUIYANG_LNG, GUIYANG_LAT);
        assertThat(backward).isCloseTo(forward, org.assertj.core.data.Offset.offset(0.001d));
    }

    @Test
    @DisplayName("边界框包含中心点，半径越大框越大")
    void boundingBoxContainsCenter() {
        double[] small = GeoUtils.boundingBox(GUIYANG_LNG, GUIYANG_LAT, 1_000d);
        double[] large = GeoUtils.boundingBox(GUIYANG_LNG, GUIYANG_LAT, 50_000d);

        assertThat(small[0]).isLessThan(GUIYANG_LNG);
        assertThat(small[1]).isGreaterThan(GUIYANG_LNG);
        assertThat(small[2]).isLessThan(GUIYANG_LAT);
        assertThat(small[3]).isGreaterThan(GUIYANG_LAT);

        double smallWidth = small[1] - small[0];
        double largeWidth = large[1] - large[0];
        assertThat(largeWidth).isGreaterThan(smallWidth);
    }

    @Test
    @DisplayName("距离可读化：米与公里")
    void humanizeDistance() {
        assertThat(GeoUtils.humanize(350)).isEqualTo("350m");
        assertThat(GeoUtils.humanize(1_500)).isEqualTo("1.5km");
    }
}
