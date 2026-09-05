package top.mcxiafeng.badger.platform

/**
 * [KMP K10] 平台图像句柄（expect）：扫码/OCR 引擎的统一图像输入。
 *
 * - Android actual：零拷贝包装 `android.graphics.Bitmap`（引擎内部 WeChatQRCode / ML Kit
 *   均直接消费 Bitmap，包装不产生像素拷贝，保证相机帧热路径零回归）。
 * - iOS actual：包装 `platform.UIKit.UIImage`（CoreImage / Vision 从 UIImage 构建检测输入）。
 *
 * 所有权语义与原 Android 代码一致：谁创建谁持有，接收方不得隐式 recycle；
 * [close] 由持有方在生命周期终点调用（Android=Bitmap.recycle，iOS=无操作，ARC 自管理）。
 */
expect class PlatformImage {
    val width: Int
    val height: Int

    /** 释放底层像素资源。仅允许持有方调用（Android=recycle，iOS=no-op）。 */
    fun close()
}
