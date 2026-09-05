package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 电池优化白名单边界（SyncStatusViewModel 读、SyncStatusPage 引导跳转）。
 *
 * Android actual = PowerManager.isIgnoringBatteryOptimizations + ACTION_REQUEST_IGNORE；
 * iOS actual = 恒 true（无电池优化白名单机制），跳转 no-op。
 */
expect object BatteryOptimization {

    /** 当前 App 是否已被用户豁免电池优化（iOS 恒 true）。 */
    fun isIgnoring(): Boolean

    /** 拉起系统设置引导用户豁免（iOS no-op）。 */
    fun openRequestSettings()
}
