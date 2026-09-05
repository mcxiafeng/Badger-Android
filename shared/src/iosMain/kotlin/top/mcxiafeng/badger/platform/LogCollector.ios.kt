package top.mcxiafeng.badger.platform

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** [KMP K13c] iOS actual 骨架：os_log 捕获 K16 接线，当前返回空日志。 */
actual object LogCollector {

    actual fun collectRecentLogs(): String = ""

    actual fun cacheDirPath(): String {
        val dirs = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        return dirs.firstOrNull() as? String ?: error("NSCachesDirectory 不可用")
    }

    actual fun deviceAbiLine(): String = "Device: ${PlatformInfo.deviceModel} (iOS ${PlatformInfo.apiLevel})"
}
