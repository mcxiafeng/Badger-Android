package top.mcxiafeng.badger.platform

import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "PlatformPermissions.ios"

/**
 * [KMP K13c] iOS actual 骨架：AVCaptureDevice 授权状态查询可用；
 * 主动请求弹窗（requestAccess）经 suspendCancellableCoroutine 接线在真机验证（K17）。
 */
actual object PlatformPermissions {

    actual fun isCameraGranted(): Boolean =
        AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized

    actual suspend fun requestCamera(): Boolean {
        // K17: AVCaptureDevice.requestAccessForMediaType + suspendCancellableCoroutine 包装
        BadgerLog.w(TAG, "requestCamera: iOS 主动请求骨架，未接线（K17 真机验证）")
        return isCameraGranted()
    }
}
