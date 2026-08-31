package top.mcxiafeng.badger.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val QR_SIZE = 512

/**
 * 生成二维码并保存到相册。
 */
suspend fun saveQrToGallery(context: Context, content: String): Boolean = withContext(Dispatchers.IO) {
    val bitmap = try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            QR_SIZE,
            QR_SIZE,
            hints,
        )
        Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888).also { generated ->
            for (x in 0 until QR_SIZE) {
                for (y in 0 until QR_SIZE) {
                    generated.setPixel(
                        x,
                        y,
                        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE,
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.e("QrUtils", "生成二维码失败", e)
        return@withContext false
    }

    try {
        Methods.saveBitmapToGallery(
            context,
            bitmap,
            "wechat_qr_${System.currentTimeMillis()}.png",
        )
    } catch (e: Exception) {
        Log.e("QrUtils", "保存二维码失败", e)
        false
    } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}
