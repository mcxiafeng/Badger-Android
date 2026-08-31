package top.mcxiafeng.badger.utils

import android.content.Context
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val QR_SIZE = 512

/** 生成二维码并保存到相册。 */
suspend fun saveQrToGallery(context: Context, content: String): Boolean = withContext(Dispatchers.IO) {
    val bitmap = try {
        Methods.generateQRCode(content, QR_SIZE, Color.BLACK)
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
