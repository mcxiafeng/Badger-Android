package top.mcxiafeng.badger.platform

/**
 * 设备定位服务（commonMain 契约，expect 函数按平台提供 actual 实现）。
 *
 * 坐标系约定：[LocationService.currentPosition] 一律返回 **GCJ-02**（高德坐标系）——
 * Android actual 内部做 WGS-84→GCJ-02 转换（[top.mcxiafeng.badger.utils.CoordinateConverter]）；
 * iOS actual 直用系统 CoreLocation（中国大陆原生即 GCJ-02，境外两者重合）。
 *
 * 权限不含在本接口：调用前先经 [PlatformPermissions.requestLocation] 拿授权。
 * Koin 注册在 commonAppStateModule（`single { platformLocationService() }`），
 * VM/测试经接口注入可 mock。
 */
interface LocationService {

    /** 设备是否具备定位硬件/能力（不含运行时权限判断）。 */
    fun isSupported(): Boolean

    /**
     * 获取当前位置（一次性，GCJ-02）。
     *
     * @param timeoutMs 最长等待时长；超时/失败/权限缺失返回 null（不抛异常，选点 UI 自行提示）。
     */
    suspend fun currentPosition(timeoutMs: Long = DEFAULT_TIMEOUT_MS): GeoPoint?

    companion object {
        /** 默认定位超时：GNSS 冷启动可能 10s+，10s 是可用性与电量的折中。 */
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}

/** 大地坐标点（GCJ-02）。 */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    /** "经度,纬度"（高德 Web 服务参数顺序：经度在前）。 */
    fun toLngLatString(): String = "$longitude,$latitude"

    companion object {
        fun fromLngLatString(raw: String?): GeoPoint? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(",")
            if (parts.size != 2) return null
            val lng = parts[0].trim().toDoubleOrNull() ?: return null
            val lat = parts[1].trim().toDoubleOrNull() ?: return null
            if (kotlin.math.abs(lng) > 180.0 || kotlin.math.abs(lat) > 90.0) return null
            return GeoPoint(latitude = lat, longitude = lng)
        }
    }
}

/** 平台实现入口（Android=LocationManager / iOS=CoreLocation）。 */
expect fun platformLocationService(): LocationService
