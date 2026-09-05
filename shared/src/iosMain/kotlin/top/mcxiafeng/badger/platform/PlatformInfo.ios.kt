package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice

/**
 * [KMP K13c] iOS actual：NSProcessInfo / UIDevice。
 * apiLevel 语义 = iOS 主版本号（GpuCompat 降级阶梯按设备档次判断，见 docs/effect-visual-spec.md §6）。
 */
actual object PlatformInfo {
    @OptIn(ExperimentalForeignApi::class)
    actual val apiLevel: Int
        get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion.toInt() }

    actual val deviceModel: String
        get() = UIDevice.currentDevice.model
}
