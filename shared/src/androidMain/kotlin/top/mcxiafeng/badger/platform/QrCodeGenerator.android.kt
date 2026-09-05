package top.mcxiafeng.badger.platform

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "QrCodeGenerator"

/** [KMP K13c] Android actual：ZXing QRCodeWriter（逐行对齐原 Methods.generateQRCode）。 */
actual object QrCodeGenerator {

    actual fun generate(
        content: String,
        sizePx: Int,
        foregroundColor: Int,
        backgroundColor: Int,
    ): PlatformImage? {
        return try {
            val hints = mutableMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) foregroundColor else backgroundColor)
                }
            }
            PlatformImage(bitmap)
        } catch (e: Exception) {
            BadgerLog.e(TAG, "QR 生成失败 (size=$sizePx)", e)
            null
        }
    }
}
