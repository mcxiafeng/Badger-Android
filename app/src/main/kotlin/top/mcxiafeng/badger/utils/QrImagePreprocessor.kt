package top.mcxiafeng.badger.utils

import android.graphics.Bitmap
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import kotlin.math.max

object QrImagePreprocessor {

    private const val TAG = "QrPreprocess"

    fun toGrayscale(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return toGrayscale(pixels, w, h)
    }

    fun toGrayscale(pixels: IntArray, w: Int, h: Int): IntArray {
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        return gray
    }

    fun grayscaleToBitmap(gray: IntArray, w: Int, h: Int): Bitmap {
        val pixels = IntArray(w * h)
        for (i in gray.indices) {
            val v = gray[i].coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }

    fun fitToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val scale = maxDim.toFloat() / max(bitmap.width, bitmap.height)
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun applyExifRotation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
        val degrees = when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                val matrix = android.graphics.Matrix().apply { postScale(-1f, 1f) }
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                val matrix = android.graphics.Matrix().apply { postScale(1f, -1f) }
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                val matrix = android.graphics.Matrix().apply { postScale(-1f, 1f); postRotate(90f) }
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                val matrix = android.graphics.Matrix().apply { postScale(-1f, 1f); postRotate(270f) }
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            else -> 0
        }
        if (degrees == 0) return bitmap
        val rotated = rotateBitmap(bitmap, degrees)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    fun rotateFromExifFile(bitmap: Bitmap, filePath: String): Bitmap {
        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        Log.d(TAG, "EXIF orientation=$orientation for $filePath")
        return applyExifRotation(bitmap, orientation)
    }

    fun rotateFromExifStream(bitmap: Bitmap, inputStreamFactory: () -> InputStream?): Bitmap {
        val stream = inputStreamFactory() ?: return bitmap
        val exif = try { ExifInterface(stream) } catch (_: Exception) { return bitmap } finally { stream.close() }
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        Log.d(TAG, "EXIF orientation=$orientation from stream")
        return applyExifRotation(bitmap, orientation)
    }
}
