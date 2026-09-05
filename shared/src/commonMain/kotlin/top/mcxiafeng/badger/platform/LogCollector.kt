package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 日志收集边界（LogViewerPage 消费）。
 *
 * Android actual：logcat -d -v time（原 LogViewerPage 实现）+ cacheDir；
 * iOS actual：K16 接 os_log 捕获（当前返回空串）。
 */
expect object LogCollector {

    /** 拉取近期应用日志全文；失败返回空串。 */
    fun collectRecentLogs(): String

    /** 平台缓存目录（打包 zip 的落点）。 */
    fun cacheDirPath(): String

    /** 平台 ABI/型号描述行（诊断信息）。 */
    fun deviceAbiLine(): String
}
