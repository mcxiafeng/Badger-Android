package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.network.AmapGeoPoint
import top.mcxiafeng.badger.network.AmapMapConfig
import top.mcxiafeng.badger.network.AmapPoiPage
import top.mcxiafeng.badger.network.RegeoResult
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.platform.GeoPoint
import top.mcxiafeng.badger.platform.LocationService
import top.mcxiafeng.badger.platform.PlatformPermissions
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * 位置域数据仓库：高德代理 API（Key 服务端托管）+ 设备定位 + 定位权限的统一门面。
 *
 * deep module 边界：VM 只面对本接口；ServerApi 的阻塞式网络调用统一收口在
 * [BadgerDispatchers.io]（ServerApi 线程不变量），定位/权限走 [LocationService]/
 * [PlatformPermissions] 平台边界。
 */
interface LocationRepository {

    /** 代理可用性 + JS API 配置（/config，登录即可读）。 */
    suspend fun config(): AmapMapConfig

    /** POI 关键字搜索。 */
    suspend fun searchKeyword(
        keywords: String,
        region: String? = null,
        cityLimit: Boolean = false,
        pageNum: Int = 1,
        pageSize: Int = 10,
    ): AmapPoiPage

    /** POI 周边搜索（围绕 [point]，服务端按距离排序）。 */
    suspend fun searchAround(
        point: GeoPoint,
        keywords: String? = null,
        radiusMeters: Int? = null,
        pageNum: Int = 1,
        pageSize: Int = 10,
    ): AmapPoiPage

    /** 坐标 → 地址 + 附近 POI。 */
    suspend fun reverseGeocode(point: GeoPoint, radiusMeters: Int? = null): RegeoResult

    /** 结构化地址 → 坐标。 */
    suspend fun geocode(address: String, city: String? = null): AmapGeoPoint

    /** 设备当前定位（GCJ-02）；超时/失败返回 null。 */
    suspend fun currentPosition(timeoutMs: Long = LocationService.DEFAULT_TIMEOUT_MS): GeoPoint?

    /** 设备是否具备定位能力（不含权限）。 */
    fun isLocationSupported(): Boolean

    /** 定位权限是否已授予。 */
    fun isLocationPermissionGranted(): Boolean

    /** 发起定位权限请求。 */
    suspend fun requestLocationPermission(): Boolean
}

class LocationRepositoryImpl(
    private val serverApi: ServerApi,
    private val locationService: LocationService,
) : LocationRepository {

    override suspend fun config(): AmapMapConfig = withContext(BadgerDispatchers.io) {
        serverApi.amapConfig()
    }

    override suspend fun searchKeyword(
        keywords: String,
        region: String?,
        cityLimit: Boolean,
        pageNum: Int,
        pageSize: Int,
    ): AmapPoiPage = withContext(BadgerDispatchers.io) {
        serverApi.amapPoiText(keywords, region, cityLimit, pageNum, pageSize)
    }

    override suspend fun searchAround(
        point: GeoPoint,
        keywords: String?,
        radiusMeters: Int?,
        pageNum: Int,
        pageSize: Int,
    ): AmapPoiPage = withContext(BadgerDispatchers.io) {
        serverApi.amapPoiAround(point.toLngLatString(), keywords, radiusMeters, pageNum, pageSize)
    }

    override suspend fun reverseGeocode(point: GeoPoint, radiusMeters: Int?): RegeoResult =
        withContext(BadgerDispatchers.io) {
            serverApi.amapRegeo(point.toLngLatString(), radiusMeters)
        }

    override suspend fun geocode(address: String, city: String?): AmapGeoPoint =
        withContext(BadgerDispatchers.io) {
            serverApi.amapGeocode(address, city)
        }

    override suspend fun currentPosition(timeoutMs: Long): GeoPoint? {
        val point = locationService.currentPosition(timeoutMs)
        if (point == null) BadgerLog.w(TAG, "currentPosition: 定位失败/超时 timeout=$timeoutMs")
        return point
    }

    override fun isLocationSupported(): Boolean = locationService.isSupported()

    override fun isLocationPermissionGranted(): Boolean = PlatformPermissions.isLocationGranted()

    override suspend fun requestLocationPermission(): Boolean = PlatformPermissions.requestLocation()

    companion object {
        private const val TAG = "LocationRepository"
    }
}
