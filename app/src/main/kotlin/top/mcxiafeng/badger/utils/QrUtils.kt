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

/**
 * 生成二维码并保存到相册
 */
suspend fun saveQrToGallery(context: Context, content: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, 512, 512, hints
            )
            for (x in 0 until 512) {
                for (y in 0 until 512) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            Methods.saveBitmapToGallery(context, bitmap, "wechat_qr_${System.currentTimeMillis()}.png")
        } catch (e: Exception) {
            Log.e("QrUtils", "生成二维码失败", e)
            false
        }
    }
}
