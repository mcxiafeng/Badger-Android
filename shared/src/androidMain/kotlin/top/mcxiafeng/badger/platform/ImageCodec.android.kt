package top.mcxiafeng.badger.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** [KMP K13c] Android actual：BitmapFactory / Bitmap.compress（WEBP 与原 Methods 压缩一致）。 */
actual object ImageCodec {

    actual val DEFAULT_WEBP_QUALITY: Int = 90

    actual fun decode(bytes: ByteArray): PlatformImage? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return PlatformImage(bitmap)
    }

    actual fun encodeWebp(image: PlatformImage, quality: Int): ByteArray? {
        if (image.bitmap.isRecycled) return null
        val out = ByteArrayOutputStream()
        val ok = image.bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
        return if (ok) out.toByteArray() else null
    }

    actual fun encodePng(image: PlatformImage): ByteArray? {
        if (image.bitmap.isRecycled) return null
        val out = ByteArrayOutputStream()
        val ok = image.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return if (ok) out.toByteArray() else null
    }

    actual fun scaleToMaxSide(image: PlatformImage, maxSide: Int): PlatformImage {
        val bitmap = image.bitmap
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSide) return image
        val scale = maxSide.toFloat() / largest.toFloat()
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return PlatformImage(Bitmap.createScaledBitmap(bitmap, w, h, true))
    }

    actual val AVATAR_SIZE: Int = 256
    actual val COLLECTION_BG_SIZE: Int = 1080
}
