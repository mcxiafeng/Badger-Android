package top.mcxiafeng.badger.platform

import qrcode.QRCode
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "QrCodeGenerator"
private const val QR_CELL_SIZE_DIVISOR = 33
private const val QR_MIN_CELL_SIZE = 4

object QrCodeGenerator {

    /**
     * 生成二维码 PNG 字节数组。
     * @param content 二维码内容
     * @param sizePx 期望目标边长像素
     * @param foregroundColor 前景色 (ARGB Int)
     * @param backgroundColor 背景色 (ARGB Int，默认 0 为透明)
     */
    fun generateBytes(
        content: String,
        sizePx: Int = 512,
        foregroundColor: Int,
        backgroundColor: Int = 0x00000000,
    ): ByteArray? {
        if (sizePx <= 0 || content.isBlank()) return null
        return try {
            val cellSize = (sizePx / QR_CELL_SIZE_DIVISOR).coerceAtLeast(QR_MIN_CELL_SIZE)
            val pngBytes = QRCode.ofSquares()
                .withColor(foregroundColor)
                .withBackgroundColor(backgroundColor)
                .withSize(cellSize)
                .build(content)
                .render()
                .getBytes()
            BadgerLog.d(TAG, "generateBytes: QR 生成成功 size=$sizePx cellSize=$cellSize pngBytes=${pngBytes.size}")
            pngBytes
        } catch (e: Exception) {
            BadgerLog.e(TAG, "QR 生成失败 (size=$sizePx)", e)
            null
        }
    }
}
