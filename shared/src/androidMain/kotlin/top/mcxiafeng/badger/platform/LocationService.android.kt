package top.mcxiafeng.badger.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.mcxiafeng.badger.shared.db.SpikeContextHolder
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.CoordinateConverter
import kotlin.coroutines.resume

private const val TAG = "LocationService.android"

/**
 * Android actual：系统 [LocationManager]（不引高德/Play Services 定位 SDK，规避 GMS 依赖）。
 *
 * 坐标系：LocationManager 上报 WGS-84，出参前统一经 [CoordinateConverter] 转 GCJ-02。
 * 策略：先取 30s 内的最新 lastKnown（免等待），不新鲜则向首个可用 provider 请求一次更新，
 * 第一个 fix 即返回（ NETWORK 在室内更快、GPS 室外更准；定位精度满足"选个位置"即可）。
 */
internal object AndroidLocationService : LocationService {

    /** lastKnown 视为"够新"的窗口：太旧的缓存点会逆地理到错误街道。 */
    private const val FRESH_WINDOW_MS = 30_000L

    private val prioritizedProviders = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )

    private fun locationManager(): LocationManager? =
        SpikeContextHolder.appContext?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private fun hasPermission(): Boolean {
        val context = SpikeContextHolder.appContext ?: return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun isSupported(): Boolean {
        val lm = locationManager() ?: return false
        return prioritizedProviders.any { provider ->
            runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    override suspend fun currentPosition(timeoutMs: Long): GeoPoint? = withContext(BadgerDispatchers.io) {
        if (!hasPermission()) {
            BadgerLog.w(TAG, "currentPosition: 定位权限未授予")
            return@withContext null
        }
        val lm = locationManager() ?: run {
            BadgerLog.w(TAG, "currentPosition: LocationManager 不可用")
            return@withContext null
        }
        lastKnownFresh(lm)?.let { location ->
            BadgerLog.d(TAG, "currentPosition: lastKnown 命中 age=${SystemClock.elapsedRealtime() - location.time}ms")
            return@withContext toGcj02(location)
        }
        val provider = prioritizedProviders.firstOrNull { p ->
            runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) && p != LocationManager.PASSIVE_PROVIDER
        } ?: run {
            BadgerLog.w(TAG, "currentPosition: 无可用定位 provider")
            return@withContext null
        }
        BadgerLog.d(TAG, "currentPosition: 请求实时定位 provider=$provider timeout=${timeoutMs}ms")
        val fix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                // object 表达式：方法体内 `this` 即 listener，首个 fix 后即注销（连续更新路径）
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { lm.removeUpdates(this) }
                        BadgerLog.d(TAG, "currentPosition: 收到 fix provider=${location.provider}")
                        if (cont.isActive) cont.resume(toGcj02(location))
                    }
                }
                cont.invokeOnCancellation {
                    runCatching { lm.removeUpdates(listener) }
                }
                try {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                } catch (e: Exception) {
                    BadgerLog.e(TAG, "currentPosition: requestLocationUpdates 失败 provider=$provider", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (fix == null) BadgerLog.w(TAG, "currentPosition: 定位超时 timeout=${timeoutMs}ms")
        fix
    }

    private fun lastKnownFresh(lm: LocationManager): Location? =
        prioritizedProviders
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { SystemClock.elapsedRealtime() - it.time <= FRESH_WINDOW_MS }

    /** WGS-84 → GCJ-02（境外原样）。 */
    private fun toGcj02(location: Location): GeoPoint {
        val (lng, lat) = CoordinateConverter.wgs84ToGcj02(location.longitude, location.latitude)
        return GeoPoint(latitude = lat, longitude = lng)
    }
}

actual fun platformLocationService(): LocationService = AndroidLocationService
