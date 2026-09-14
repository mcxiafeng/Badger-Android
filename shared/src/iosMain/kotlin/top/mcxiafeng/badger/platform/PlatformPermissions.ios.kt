package top.mcxiafeng.badger.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.requestAccessForMediaType
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.coroutines.resume

private const val TAG = "PlatformPermissions.ios"

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

    actual fun openAppSettings() {
        
        BadgerLog.w(TAG, "openAppSettings: iOS 待 K17 实接")
    }
}
