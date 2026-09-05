package top.mcxiafeng.badger.ui.blur

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIDevice

private const val TAG = "GpuCompat.ios"

/**
 * [KMP K13c] iOS actual 骨架：按设备芯片档次映射降级阶梯（docs/effect-visual-spec.md §6）。
 * 规则：A12 芯片（iPhone XS/XR，2018）以下 → 标准磨砂。当前以系统主版本号做粗档
 * （iOS 16+ 视为现代设备），精确 mach 硬件型号解析在 K16/K17 真机验证时落地。
 */
actual object GpuCompat {

    @OptIn(ExperimentalForeignApi::class)
    actual fun isAdvancedBlurSupported(): Boolean {
        readCachedBlurSupport()?.let { return it }
        val majorOs = platform.Foundation.NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }
        val device = UIDevice.currentDevice.model
        // 模拟器/现代设备默认支持；真机档次精确判定 K17
        val result = majorOs >= 16 || device.contains("Simulator", ignoreCase = true)
        writeCachedBlurSupport(result)
        return result
    }

    actual fun clearCache() = clearBlurSupportCache()
}
