package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 运行时权限平台边界（消费方：扫码页相机权限、位置选择器定位权限）。
 *
 * Android actual = ContextCompat.checkSelfPermission + Activity requestPermissions
 * （Activity 经 ActivityHost 注册表获取，与 NfcActivityHost 同模式）；
 * iOS actual = AVCaptureDevice / CoreLocation 授权状态查询 + 主动请求弹窗。
 */
expect object PlatformPermissions {

    /** 相机权限是否已授予。 */
    fun isCameraGranted(): Boolean

    /** 请求相机权限；用户拒绝/永久拒绝返回 false。 */
    suspend fun requestCamera(): Boolean

    /** 定位权限是否已授予（Android=FINE/COARSE 任一；iOS=WhenInUse/Always）。 */
    fun isLocationGranted(): Boolean

    /** 请求定位权限；用户拒绝/永久拒绝返回 false。 */
    suspend fun requestLocation(): Boolean

    /**
     * 打开系统设置中本应用详情页（用户拒绝后手动开启权限的出口）。
     * iOS 骨架（K17 实接）。
     */
    fun openAppSettings()
}
