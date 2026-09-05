package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "BatteryOptimization.ios"

/** [KMP K13c] iOS actual：无电池优化白名单机制，恒豁免。 */
actual object BatteryOptimization {

    actual fun isIgnoring(): Boolean = true

    actual fun openRequestSettings() {
        BadgerLog.d(TAG, "openRequestSettings: iOS no-op（无电池优化机制）")
    }
}
