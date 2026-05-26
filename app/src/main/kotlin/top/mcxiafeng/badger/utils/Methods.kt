package top.mcxiafeng.badger.utils

import android.content.ClipData
import android.content.ClipData.newPlainText
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object Methods {

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(newPlainText(label, text))
    }

    fun copyToClipboard(context: Context, text: String, snackbarHostState: SnackbarHostState) {
        copyToClipboard(context, "copied text", text)
    }

    /** 头像目标尺寸（px），保存时缩放到此尺寸 */
    const val AVATAR_SIZE = 256
    /** WebP 压缩质量（0~100），头像用 60 足够，体积比 JPEG 小 25~35% */
    const val AVATAR_QUALITY = 60

    /**
     * 缩放 Bitmap 到指定最大边长（保持宽高比）
     */
    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        if (bitmap.width <= maxSize && bitmap.height <= maxSize) return bitmap
        val scale = maxSize.toFloat() / min(bitmap.width, bitmap.height)
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    fun saveBitmapAsAvatar(context: Context, bitmap: Bitmap, fileName: String): File {
        val scaled = scaleBitmap(bitmap, AVATAR_SIZE)
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { output ->
            scaled.compress(Bitmap.CompressFormat.WEBP, AVATAR_QUALITY, output)
        }
        if (scaled !== bitmap) scaled.recycle()
        return file
    }

    /**
     * 保存 Bitmap 到系统相册（Pictures/Badger）
     * @return 是否保存成功
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Badger")
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            true
        } catch (e: Exception) {
            Log.d("Methods", "saveBitmapToGallery failed: ${e.message}")
            false
        }
    }

    suspend fun saveUriAsAvatar(context: Context, uri: Uri, fileName: String): File? {
        // 先用 inSampleSize 降采样解码，避免 OOM
        val options = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            opts
        } ?: return null

        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, AVATAR_SIZE)
        val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } ?: return null

        val file = saveBitmapAsAvatar(context, bitmap, fileName)
        bitmap.recycle()
        return file
    }

    /**
     * 计算降采样倍数，使解码后图片尺寸接近但不超过 targetSize
     */
    private fun calculateSampleSize(width: Int, height: Int, targetSize: Int): Int {
        val minDim = min(width, height)
        if (minDim <= targetSize) return 1
        var sample = 1
        while (minDim / (sample * 2) >= targetSize) {
            sample *= 2
        }
        return sample
    }

    suspend fun loadAvatarBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return withContext(Dispatchers.IO) {
            // 降采样：头像显示最大 80dp ≈ 240px，2x 屏幕也只需 480px
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, opts)
            val sampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, 480)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(path, decodeOpts)
        }
    }

    fun deleteAvatarFile(avatarPath: String?) {
        if (avatarPath.isNullOrBlank()) return
        val file = File(avatarPath)
        if (file.exists()) file.delete()
    }

    /** 背景图目标宽度（px），保存时缩放到此尺寸 */
    const val COLLECTION_BG_SIZE = 1080
    /** 背景图 WebP 压缩质量 */
    const val COLLECTION_BG_QUALITY = 75

    fun saveBitmapAsCollectionBg(context: Context, bitmap: Bitmap, fileName: String): File {
        val scaled = scaleBitmap(bitmap, COLLECTION_BG_SIZE)
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { output ->
            scaled.compress(Bitmap.CompressFormat.WEBP, COLLECTION_BG_QUALITY, output)
        }
        if (scaled !== bitmap) scaled.recycle()
        return file
    }

    suspend fun loadBackgroundBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            val sampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, 800)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(path, decodeOpts)
        }
    }

    fun deleteFileIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.exists()) file.delete()
    }

    val qrColors = listOf(
        Color(0xFF000000),
        Color(0xFF3482FF),
        Color(0xFFE91E63),
        Color(0xFF4CAF50),
        Color(0xFFFF9800),
        Color(0xFF9C27B0)
    )

    fun generateQRCode(content: String, size: Int, color: Int, backgroundColor: Int = AndroidColor.WHITE): Bitmap {
        val hints = mutableMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) color else backgroundColor)
            }
        }
        return bitmap
    }
}
