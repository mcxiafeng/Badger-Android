package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice

actual object PlatformInfo {
    @OptIn(ExperimentalForeignApi::class)
    actual val apiLevel: Int
        get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion.toInt() }

    actual val deviceModel: String
        get() = UIDevice.currentDevice.model
}
