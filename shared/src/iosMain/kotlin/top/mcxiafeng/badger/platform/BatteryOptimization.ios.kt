package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "BatteryOptimization.ios"

actual object BatteryOptimization {

    actual fun isIgnoring(): Boolean = true

    actual fun openRequestSettings() {
        BadgerLog.d(TAG, "openRequestSettings: iOS no-op（无电池优化机制）")
    }
}
