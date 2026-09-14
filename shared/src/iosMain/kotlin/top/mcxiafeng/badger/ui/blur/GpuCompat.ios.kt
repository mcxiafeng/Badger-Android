package top.mcxiafeng.badger.ui.blur

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIDevice

private const val TAG = "GpuCompat.ios"

actual object GpuCompat {

    @OptIn(ExperimentalForeignApi::class)
    actual fun isAdvancedBlurSupported(): Boolean {
        readCachedBlurSupport()?.let { return it }
        val majorOs = platform.Foundation.NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }
        val device = UIDevice.currentDevice.model
        
        val result = majorOs >= 16 || device.contains("Simulator", ignoreCase = true)
        writeCachedBlurSupport(result)
        return result
    }

    actual fun clearCache() = clearBlurSupportCache()
}
