@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package top.mcxiafeng.badger.platform

import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.coroutines.resume

private const val TAG = "LocationService.ios"

/**
 * iOS actual：系统 CoreLocation（一次性 requestLocation）。
 *
 * 坐标系：中国大陆由系统直接给出 GCJ-02（Apple 官方偏移），**不做二次转换**；
 * 境外 WGS-84 与 GCJ-02 重合，出参语义保持一致。
 *
 * 线程模型：CLLocationManager 须挂在带 RunLoop 的线程——manager/delegate 的创建与
 * 请求统一 dispatch 到主队列；回调 resume 回协程侧（跨线程 resume 安全）。
 * manager/delegate 由 [IosLocationBridge] 长持（防异步等待期间被 GC）。
 */
internal object IosLocationService : LocationService {

    override fun isSupported(): Boolean =
        runCatching { CLLocationManager.locationServicesEnabled() }.getOrDefault(false)

    override suspend fun currentPosition(timeoutMs: Long): GeoPoint? = withContext(BadgerDispatchers.io) {
        if (!PlatformPermissions.isLocationGranted()) {
            BadgerLog.w(TAG, "currentPosition: 定位权限未授予")
            return@withContext null
        }
        BadgerLog.d(TAG, "currentPosition: 请求一次性定位 timeout=${timeoutMs}ms")
        val fix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                IosLocationBridge.requestLocation { point ->
                    if (cont.isActive) cont.resume(point)
                }
            }
        }
        if (fix == null) BadgerLog.w(TAG, "currentPosition: 定位超时或失败 timeout=${timeoutMs}ms")
        fix
    }
}

/** CoreLocation 无 completion handler，授权/定位结果统一经 delegate 桥接为回调。 */
internal object IosLocationBridge {

    private val holder: LocationHolder by lazy { LocationHolder() }

    /** PlatformPermissions.requestLocation 入口：仅授权结果。 */
    fun requestAuthorization(callback: (Boolean) -> Unit) {
        holder.delegate.authCallback = callback
        dispatch_async(dispatch_get_main_queue()) {
            holder.manager.requestWhenInUseAuthorization()
        }
    }

    /** LocationService.currentPosition 入口：一次性定位。 */
    fun requestLocation(callback: (GeoPoint?) -> Unit) {
        holder.delegate.locationCallback = callback
        dispatch_async(dispatch_get_main_queue()) {
            holder.manager.requestLocation()
        }
    }
}

/** manager + delegate 长持（lazy 单例，异步等待期间不被回收）。 */
private class LocationHolder {
    val delegate = IosLocationDelegate()
    val manager = CLLocationManager().apply { delegate = this@LocationHolder.delegate }
}

private class IosLocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {

    var authCallback: ((Boolean) -> Unit)? = null
    var locationCallback: ((GeoPoint?) -> Unit)? = null
    private var pendingStartAfterAuth = false

    /** iOS 14+ 授权状态变化（含首次弹窗结果）。 */
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val granted = manager.authorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse ||
            manager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways
        BadgerLog.d(TAG, "didChangeAuthorization: granted=$granted")
        authCallback?.invoke(granted)
        authCallback = null
        if (granted && pendingStartAfterAuth) {
            pendingStartAfterAuth = false
            manager.requestLocation()
        }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        val point = location?.let { loc ->
            loc.coordinate.useContents { GeoPoint(latitude = latitude, longitude = longitude) }
        }
        if (point == null) {
            BadgerLog.w(TAG, "didUpdateLocations: 空定位结果")
        } else {
            BadgerLog.d(TAG, "didUpdateLocations: lat=${point.latitude} lng=${point.longitude}")
        }
        locationCallback?.invoke(point)
        locationCallback = null
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        BadgerLog.e(TAG, "didFailWithError: domain=${didFailWithError.domain} code=${didFailWithError.code}")
        locationCallback?.invoke(null)
        locationCallback = null
    }
}

actual fun platformLocationService(): LocationService = IosLocationService
