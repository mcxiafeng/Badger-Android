package top.mcxiafeng.badger.utils

import android.content.ClipData.newPlainText
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
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
     * 缩放 Bitmap 到指定最大边长（保持宽高比）。
     */
    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        if (maxSize <= 0) return bitmap
        val largestDimension = maxOf(bitmap.width, bitmap.height)
        if (largestDimension <= maxSize) return bitmap

        val scale = maxSize.toFloat() / largestDimension.toFloat()
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    fun saveBitmapAsAvatar(context: Context, bitmap: Bitmap, fileName: String): File {
        val scaled = scaleBitmap(bitmap, AVATAR_SIZE)
        val file = File(context.filesDir, fileName)
        try {
            FileOutputStream(file).use { output ->
                check(scaled.compress(Bitmap.CompressFormat.WEBP, AVATAR_QUALITY, output)) {
                    "Failed to compress avatar bitmap"
                }
            }
            return file
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }
    }

    /**
     * 保存 Bitmap 到系统相册（Pictures/Badger）。
     * @return 是否保存成功
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Badger")
        }

        val resolver = context.contentResolver
        val uri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("Methods", "saveBitmapToGallery insert failed", e)
            return false
        } ?: return false

        return try {
            resolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Failed to compress PNG bitmap"
                }
            } ?: error("Unable to open gallery output stream")
            true
        } catch (e: Exception) {
            // Remove the partially-created MediaStore entry so a failed export does not
            // leave an empty/corrupt image behind in the gallery.
            runCatching { resolver.delete(uri, null, null) }
            Log.e("Methods", "saveBitmapToGallery failed", e)
            false
        }
    }

    suspend fun saveUriAsAvatar(context: Context, uri: Uri, fileName: String): File? {
        // 先用 inSampleSize 降采样解码，避免 OOM
        val options = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            opts
        } ?: return null

        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, AVATAR_SIZE)
        val bitmap = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } ?: return null

        return try {
            saveBitmapAsAvatar(context, bitmap, fileName)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /**
     * 计算降采样倍数，让最长边在解码后尽量接近但不超过 targetSize。
     */
    private fun calculateSampleSize(width: Int, height: Int, targetSize: Int): Int {
        val largestDimension = maxOf(width, height)
        if (largestDimension <= targetSize || targetSize <= 0) return 1
        var sample = 1
        while (largestDimension / (sample * 2) >= targetSize) {
            sample *= 2
        }
        return sample
    }

    fun deleteAvatarFile(avatarPath: String?) {
        if (avatarPath.isNullOrBlank()) return
        val file = File(avatarPath)
        if (file.exists()) file.delete()
    }

    suspend fun loadAvatarBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)
        }
    }

    /** 背景图目标宽度（px），保存时缩放到此尺寸 */
    const val COLLECTION_BG_SIZE = 1080
    /** 背景图 WebP 压缩质量 */
    const val COLLECTION_BG_QUALITY = 75

    fun saveBitmapAsCollectionBg(context: Context, bitmap: Bitmap, fileName: String): File {
        val scaled = scaleBitmap(bitmap, COLLECTION_BG_SIZE)
        val file = File(context.filesDir, fileName)
        try {
            FileOutputStream(file).use { output ->
                check(scaled.compress(Bitmap.CompressFormat.WEBP, COLLECTION_BG_QUALITY, output)) {
                    "Failed to compress collection background bitmap"
                }
            }
            return file
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }
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

    /**
     * ISO 字符串或 epoch millis → `yyyy-MM-dd HH:mm` 格式化。
     *
     * @param raw 原始时间字符串（ISO 格式或 epoch millis）
     * @param fallbackOnFailure 解析失败时的回退值（默认返回 null）
     * @return 格式化后的时间字符串，解析失败返回 [fallbackOnFailure]
     */
    fun formatDateTime(raw: String?, fallbackOnFailure: String? = null): String? {
        if (raw.isNullOrBlank()) return fallbackOnFailure
        raw.toLongOrNull()?.let { epoch ->
            return runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(epoch))
            }.getOrNull() ?: fallbackOnFailure
        }
        return runCatching {
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            val trimmed = raw.substringBefore('.').substringBefore('+').substringBefore('Z')
            val date = parser.parse(trimmed) ?: return fallbackOnFailure
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(date)
        }.getOrNull() ?: fallbackOnFailure
    }
}
