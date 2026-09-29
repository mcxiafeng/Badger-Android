package top.mcxiafeng.badger.platform

import qrcode.QRCode
import qrcode.raw.ErrorCorrectionLevel
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "QrCodeGenerator"

object QrCodeGenerator {

    /**
     * 生成标准方块二维码 PNG 字节数组。
     *
     * @param content 二维码内容
     * @param sizePx 期望二维码边长像素
     * @param foregroundColor 前景色 (ARGB Int)
     * @param backgroundColor 背景色 (ARGB Int，默认白色)
     */
    fun generateBytes(
        content: String,
        sizePx: Int = 512,
        foregroundColor: Int = 0xFF000000.toInt(),
        backgroundColor: Int = 0xFFFFFFFF.toInt(),
    ): ByteArray? {
        if (sizePx <= 0 || content.isBlank()) return null

        return try {
            // QRCode-Kotlin 的 withSize() 是每个二维码模块的尺寸，
            // 不是最终图片尺寸，因此这里使用固定的模块尺寸。
            val cellSize = 10

            val pngBytes = QRCode.ofSquares()
                .withErrorCorrectionLevel(ErrorCorrectionLevel.MEDIUM)
                .withInnerSpacing(0)
                .withColor(foregroundColor)
                .withBackgroundColor(backgroundColor)
//                .withSize(cellSize)
                .build(content)
                .fitIntoArea(sizePx, sizePx)
                .render()
                .getBytes()

            BadgerLog.d(
                TAG,
                "generateBytes: QR 生成成功 " +
                        "targetSize=$sizePx cellSize=$cellSize pngBytes=${pngBytes.size}"
            )

            pngBytes
        } catch (e: Exception) {
            BadgerLog.e(TAG, "QR 生成失败 (size=$sizePx)", e)
            null
        }
    }
}
