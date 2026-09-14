package top.mcxiafeng.badger.platform

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual object LogCollector {

    actual fun collectRecentLogs(): String = ""

    actual fun cacheDirPath(): String {
        val dirs = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        return dirs.firstOrNull() as? String ?: error("NSCachesDirectory 不可用")
    }

    actual fun deviceAbiLine(): String = "Device: ${PlatformInfo.deviceModel} (iOS ${PlatformInfo.apiLevel})"
}
