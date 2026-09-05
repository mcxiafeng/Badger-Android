package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 平台信息边界（替代 common 代码里的 android.os.Build 直读）。
 *
 * Android actual = Build.VERSION.SDK_INT / Build.MODEL；
 * iOS actual = NSProcessInfo.systemVersion + uname machine（apiLevel 语义映射为 iOS 主版本号，
 * 供 GpuCompat 降级阶梯按"设备档次"判断，见 docs/effect-visual-spec.md §6）。
 */
expect object PlatformInfo {
    /** Android=SDK_INT；iOS=系统主版本号。 */
    val apiLevel: Int

    /** Android=Build.MODEL；iOS=uname machine（如 iPhone15,2）。 */
    val deviceModel: String
}
