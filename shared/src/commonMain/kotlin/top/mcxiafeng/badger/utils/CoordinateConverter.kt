package top.mcxiafeng.badger.utils

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * WGS-84 → GCJ-02（火星坐标）转换，纯 Kotlin（双端可用，JVM 单测覆盖）。
 *
 * 坐标系背景（高德开放平台坐标系文档）：
 * - 高德（GCJ-02）与系统 GPS（WGS-84）在中国大陆存在非线性偏移，境外两者重合；
 * - Android `LocationManager` 上报 WGS-84，交给高德 Web 服务前必须转换；
 * - iOS `CoreLocation` 在中国大陆由系统直接给出 GCJ-02（Apple 官方偏移），不得二次转换。
 *
 * 精度说明：官方加密算法未公开，此处为业界通行的近似实现，误差通常 <1~2 米，
 * 满足"选个位置"场景；不适用于测绘/导航级用途。
 */
object CoordinateConverter {

    private const val A = 6378245.0                 // 克拉索夫斯基椭球长半轴
    private const val EE = 0.00669342162296594323   // 第一偏心率平方
    /** 中国大陆粗判据框（境外无偏移，直通返回）。 */
    private const val CHINA_MIN_LNG = 72.004
    private const val CHINA_MAX_LNG = 137.8347
    private const val CHINA_MIN_LAT = 0.8293
    private const val CHINA_MAX_LAT = 55.8271

    /** WGS-84 经纬度 → GCJ-02 经纬度。境外（粗判据框外）原样返回。 */
    fun wgs84ToGcj02(wgsLng: Double, wgsLat: Double): Pair<Double, Double> {
        if (outOfChina(wgsLng, wgsLat)) return wgsLng to wgsLat
        val dLat = transformLat(wgsLng - 105.0, wgsLat - 35.0)
        val dLng = transformLng(wgsLng - 105.0, wgsLat - 35.0)
        val radLat = wgsLat / 180.0 * kotlin.math.PI
        var magic = kotlin.math.sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val adjustedLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * kotlin.math.PI)
        val adjustedLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * kotlin.math.PI)
        val gcjLat = wgsLat + adjustedLat
        val gcjLng = wgsLng + adjustedLng
        return gcjLng to gcjLat
    }

    private fun outOfChina(lng: Double, lat: Double): Boolean =
        lng < CHINA_MIN_LNG || lng > CHINA_MAX_LNG || lat < CHINA_MIN_LAT || lat > CHINA_MAX_LAT

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * kotlin.math.sin(6.0 * x * kotlin.math.PI) + 20.0 * kotlin.math.sin(2.0 * x * kotlin.math.PI)) * 2.0 / 3.0
        ret += (20.0 * kotlin.math.sin(y * kotlin.math.PI) + 40.0 * kotlin.math.sin(y / 3.0 * kotlin.math.PI)) * 2.0 / 3.0
        ret += (160.0 * kotlin.math.sin(y / 12.0 * kotlin.math.PI) + 320.0 * kotlin.math.sin(y * kotlin.math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * kotlin.math.sin(6.0 * x * kotlin.math.PI) + 20.0 * kotlin.math.sin(2.0 * x * kotlin.math.PI)) * 2.0 / 3.0
        ret += (20.0 * kotlin.math.sin(x * kotlin.math.PI) + 40.0 * kotlin.math.sin(x / 3.0 * kotlin.math.PI)) * 2.0 / 3.0
        ret += (150.0 * kotlin.math.sin(x / 12.0 * kotlin.math.PI) + 300.0 * kotlin.math.sin(x / 30.0 * kotlin.math.PI)) * 2.0 / 3.0
        return ret
    }
}
