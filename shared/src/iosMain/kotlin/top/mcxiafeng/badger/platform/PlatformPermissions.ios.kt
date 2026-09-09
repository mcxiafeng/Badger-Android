package top.mcxiafeng.badger.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.coroutines.resume

private const val TAG = "PlatformPermissions.ios"

/**
 * [KMP K13c→K16] iOS actual：AVCaptureDevice / CoreLocation 授权状态查询 + 主动请求弹窗。
 * requestAccess 经 suspendCancellableCoroutine 包装 Obj-C completion handler。
 */
actual object PlatformPermissions {

    actual fun isCameraGranted(): Boolean =
        AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized

    actual suspend fun requestCamera(): Boolean {
        if (isCameraGranted()) return true
        return suspendCancellableCoroutine { continuation ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                BadgerLog.d(TAG, "requestCamera: 授权结果=$granted")
                if (continuation.isActive) {
                    continuation.resume(granted)
                }
            }
        }
    }

    actual fun isLocationGranted(): Boolean {
        val status = CLLocationManager.authorizationStatus()
        return status == kCLAuthorizationStatusAuthorizedWhenInUse ||
            status == kCLAuthorizationStatusAuthorizedAlways
    }

    actual suspend fun requestLocation(): Boolean {
        if (isLocationGranted()) return true
        return suspendCancellableCoroutine { continuation ->
            // 授权回调经 LocationServiceHolder 的 delegate 桥接（CoreLocation 无 completion handler）
            IosLocationBridge.requestAuthorization { granted ->
                BadgerLog.d(TAG, "requestLocation: 授权结果=$granted")
                if (continuation.isActive) continuation.resume(granted)
            }
        }
    }

    actual fun openAppSettings() {
        // [K17 待真机验收] 打开 UIApplication.openSettingsURLString 需主线程 + 前台活跃态
        BadgerLog.w(TAG, "openAppSettings: iOS 待 K17 实接")
    }
}
