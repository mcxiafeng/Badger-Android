package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.utils.BadgerLog
import android.graphics.BitmapFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.shared.db.SpikeContextHolder

private const val TAG = "ScannerContracts.android"

/** [KMP K13c] Android actual：CameraPreviewSlot 内的节流状态复位（K10 语义）。 */
actual fun notifyScannerDialogDismissed() = notifyScannerDialogDismissedAndroid()

/**
 * [KMP K13c] Android actual：BitmapFactory 解码 + EXIF 方向校正（相机/相册 JPEG 语义）。
 */
actual suspend fun loadOrientedImage(bytes: ByteArray): PlatformImage? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run {
        BadgerLog.w(TAG, "loadOrientedImage: 解码失败 (${bytes.size} bytes)")
        return null
    }
    val rotated = QrImagePreprocessor.rotateBitmapFromBytes(bitmap, bytes)
    return PlatformImage(rotated)
}

actual object QrEngineBootstrap {
    private val initMutex = Mutex()
    private var initialized = false

    actual suspend fun ensureReady() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            try {
                org.opencv.OpenCV.initOpenCV()
                com.king.wechat.qrcode.WeChatQRCodeDetector.init(
                    SpikeContextHolder.appContext
                        ?: error("QrEngineBootstrap: appContext 未初始化")
                )
            } catch (e: IllegalStateException) {
                BadgerLog.w(TAG, "WeChatQRCode 懒加载跳过（Application 未就绪，可能是测试环境）", e)
            } catch (e: UnsatisfiedLinkError) {
                BadgerLog.w(TAG, "WeChatQRCode 懒加载跳过（native 库未加载，可能是测试环境）", e)
            }
            initialized = true
        }
    }
}
