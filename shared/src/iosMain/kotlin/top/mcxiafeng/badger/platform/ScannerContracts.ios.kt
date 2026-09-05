package top.mcxiafeng.badger.platform

/** [KMP K13c] iOS actual：无 EXIF 方向问题，直接解码。 */
actual suspend fun loadOrientedImage(bytes: ByteArray): PlatformImage? =
    ImageCodec.decode(bytes)

/** [KMP K13c] iOS actual：no-op（K16 相机会话生命周期管理）。 */
actual fun notifyScannerDialogDismissed() {
}

actual object QrEngineBootstrap {
    actual suspend fun ensureReady() {
        // AVFoundation 免初始化（K16 相机会话按需构建）
    }
}
