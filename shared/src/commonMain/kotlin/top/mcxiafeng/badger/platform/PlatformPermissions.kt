package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 运行时权限平台边界（当前唯一消费方：扫码页相机权限）。
 *
 * Android actual = ContextCompat.checkSelfPermission + Activity requestPermissions
 * （Activity 经 ActivityHost 注册表获取，与 NfcActivityHost 同模式）；
 * iOS actual = AVCaptureDevice.authorizationStatus 骨架（真机接线 K17）。
 */
expect object PlatformPermissions {

    /** 相机权限是否已授予。 */
    fun isCameraGranted(): Boolean

    /** 请求相机权限；用户拒绝/永久拒绝返回 false。 */
    suspend fun requestCamera(): Boolean
}
