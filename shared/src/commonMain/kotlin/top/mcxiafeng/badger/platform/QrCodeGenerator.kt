package top.mcxiafeng.badger.platform

import qrcode.QRCode
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "QrCodeGenerator"
private const val QR_CELL_SIZE_DIVISOR = 33
private const val QR_MIN_CELL_SIZE = 4

/**
 * [KMP K16] 纯 Kotlin QR 生成（双端复用）。
 *
 * 使用 qrcode-kotlin 库（io.github.g0dkar:qrcode-kotlin 4.5.0）在 commonMain 生成 PNG 字节，
 * 经 [ImageCodec.decode] 转为 [PlatformImage]——Android=Bitmap / iOS=UIImage。
 *
 * 替代旧方案：Android ZXing QRCodeWriter（平台绑定）+ iOS CIFilter 骨架（K/N klib 缺类方法）。
 * 纯 Kotlin 路径消除了 expect/actual，双端 QR 生成行为一致。
 *
 * @param content QR 码内容（URL/文本）
 * @param sizePx 目标图片像素边长（近似——cellSize = sizePx / 33 估算 QR 模块数）
 * @param foregroundColor 前景色 ARGB Int（0xAARRGGBB，与 Android Color 一致）
 * @param backgroundColor 背景色 ARGB Int
 * @return PlatformImage（Android=Bitmap / iOS=UIImage），失败返回 null
 */
object QrCodeGenerator {

    fun generate(
        content: String,
        sizePx: Int,
        foregroundColor: Int,
        backgroundColor: Int,
    ): PlatformImage? {
        if (sizePx <= 0) return null
        return try {
            // cellSize 估算：典型 QR 25-41 模块，取 33 作为均值
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
