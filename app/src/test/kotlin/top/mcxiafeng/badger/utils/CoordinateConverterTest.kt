package top.mcxiafeng.badger.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * WGS-84 → GCJ-02 转换纯逻辑面：
 * 境外直通、境内偏移在合理量级（百米级 ≈ 0.001~0.01 度）、确定性。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CoordinateConverterTest {

    /** 境外坐标（纽约）：粗判据框外，无偏移直通。 */
    @Test
    fun outOfChinaReturnsAsIs() {
        val (lng, lat) = CoordinateConverter.wgs84ToGcj02(-74.0060, 40.7128)
        assertThat(lng).isEqualTo(-74.0060)
        assertThat(lat).isEqualTo(40.7128)
    }

    /** 境内坐标（北京天安门一带）：产生偏移，量级在已知算法区间内。 */
    @Test
    fun inChinaAppliesBoundedOffset() {
        val wgsLng = 116.3912
        val wgsLat = 39.9066
        val (gcjLng, gcjLat) = CoordinateConverter.wgs84ToGcj02(wgsLng, wgsLat)
        // 偏移应存在（≠0）且在百米级（约 0.001~0.01 度）
        val dLng = abs(gcjLng - wgsLng)
        val dLat = abs(gcjLat - wgsLat)
        assertThat(dLng).isGreaterThan(0.0005)
        assertThat(dLng).isLessThan(0.01)
        assertThat(dLat).isGreaterThan(0.0005)
        assertThat(dLat).isLessThan(0.01)
    }

    /** 确定性：同输入两次转换结果一致。 */
    @Test
    fun deterministic() {
        val a = CoordinateConverter.wgs84ToGcj02(121.4737, 31.2304)
        val b = CoordinateConverter.wgs84ToGcj02(121.4737, 31.2304)
        assertThat(a).isEqualTo(b)
    }

    /** 框边缘外侧原样、内侧偏移（防越界误判）。 */
    @Test
    fun boundaryBehavior() {
        // 框外（经度 < 72.004）
        val outside = CoordinateConverter.wgs84ToGcj02(70.0, 40.0)
        assertThat(outside.first).isEqualTo(70.0)
        // 框内（北京）
        val inside = CoordinateConverter.wgs84ToGcj02(116.4, 39.9)
        assertThat(inside.first).isNotEqualTo(116.4)
    }
}
