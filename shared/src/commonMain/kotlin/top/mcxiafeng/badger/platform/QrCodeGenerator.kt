package top.mcxiafeng.badger.platform

import qrcode.QRCode
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "QrCodeGenerator"
private const val QR_CELL_SIZE_DIVISOR = 33
private const val QR_MIN_CELL_SIZE = 4

object QrCodeGenerator {

    fun generate(
        content: String,
        sizePx: Int,
        foregroundColor: Int,
        backgroundColor: Int,
    ): PlatformImage? {
        if (sizePx <= 0) return null
        return try {
            
            val cellSize = (sizePx / QR_CELL_SIZE_DIVISOR).coerceAtLeast(QR_MIN_CELL_SIZE)
            val pngBytes = QRCode.ofSquares()
                .withColor(foregroundColor)
                .withBackgroundColor(backgroundColor)
                .withSize(cellSize)
                .build(content)
                .render()
                .getBytes()
            val image = ImageCodec.decode(pngBytes)
            if (image == null) {
                BadgerLog.e(TAG, "generate: PNG 解码失败 (size=$sizePx, cellSize=$cellSize)")
            } else {
                BadgerLog.d(TAG, "generate: QR 生成成功 size=$sizePx cellSize=$cellSize pngBytes=${pngBytes.size}")
            }
            image
        } catch (e: Exception) {
            BadgerLog.e(TAG, "QR 生成失败 (size=$sizePx)", e)
            null
        }
    }
}
