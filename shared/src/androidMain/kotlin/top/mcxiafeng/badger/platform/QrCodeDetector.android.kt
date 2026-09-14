package top.mcxiafeng.badger.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.king.wechat.qrcode.WeChatQRCodeDetector
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.math.max
import org.opencv.core.Mat

private const val TAG = "QrCodeDetector"

private const val QR_DETECT_MAX_DIM = 1000

actual class QrCodeDetector {

    

    actual fun detectContents(image: PlatformImage): List<String> {
        val bitmap = image.bitmap
        val workBitmap = QrImagePreprocessor.fitToMax(bitmap, QR_DETECT_MAX_DIM)
        val needRecycleWork = workBitmap !== bitmap

        try {
            val results = WeChatQRCodeDetector.detectAndDecode(workBitmap)
            val filtered = results.filter { it.isNotEmpty() }
            Log.d(TAG, "WeChatQRCode detected ${filtered.size} codes")
            return filtered
        } catch (e: Exception) {
            Log.d(TAG, "WeChatQRCode detection failed: ${e.message}")
            return emptyList()
        } finally {
            if (needRecycleWork) workBitmap.recycle()
        }
    }

    

    actual fun detectWithBounds(image: PlatformImage): List<QrDetection> {
        val bitmap = image.bitmap
        val workBitmap = QrImagePreprocessor.fitToMax(bitmap, QR_DETECT_MAX_DIM)
        val needRecycleWork = workBitmap !== bitmap
        val scaleX = if (needRecycleWork) bitmap.width.toFloat() / workBitmap.width else 1f
        val scaleY = if (needRecycleWork) bitmap.height.toFloat() / workBitmap.height else 1f

        val points = mutableListOf<Mat>()
        try {
            val results = WeChatQRCodeDetector.detectAndDecode(workBitmap, points)
            val filtered = results.mapIndexedNotNull { index, text ->
                if (text.isEmpty()) null
                else {
                    val corners = if (index < points.size) {
                        extractCornersFromMat(points[index]).map { offset ->
                            QrPoint(offset.x * scaleX, offset.y * scaleY)
                        }
                    } else emptyList()
                    QrDetection(text, corners)
                }
            }
            return filtered
        } catch (e: Exception) {
            Log.d(TAG, "WeChatQRCode detection with bounds failed: ${e.message}")
            return emptyList()
        } finally {
            
            points.forEach { it.release() }
            if (needRecycleWork) workBitmap.recycle()
        }
    }

    

    actual fun maskQrRegions(
        image: PlatformImage,
        detections: List<QrDetection>,
        paddingPx: Int
    ): PlatformImage {
        if (detections.isEmpty()) return image

        val bitmap = image.bitmap
        val masked = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(masked)
        val paint = Paint().apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }

        for (qr in detections) {
            if (qr.corners.size < 4) continue
            val minX = (qr.corners.minOf { it.x } - paddingPx).coerceAtLeast(0f)
            val minY = (qr.corners.minOf { it.y } - paddingPx).coerceAtLeast(0f)
            val maxX = (qr.corners.maxOf { it.x } + paddingPx).coerceAtMost(bitmap.width.toFloat())
            val maxY = (qr.corners.maxOf { it.y } + paddingPx).coerceAtMost(bitmap.height.toFloat())
            canvas.drawRect(minX, minY, maxX, maxY, paint)
        }
        Log.d(TAG, "maskQrRegions: masked ${detections.size} QR regions")
        return PlatformImage(masked)
    }
}

internal fun extractCornersFromMat(mat: Mat): List<QrPoint> {
    val corners = mutableListOf<QrPoint>()
    for (i in 0 until 4) {
        val x = mat.get(i, 0)[0].toFloat()
        val y = mat.get(i, 1)[0].toFloat()
        corners.add(QrPoint(x, y))
    }
    return corners
}

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
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun applyExifRotation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
        val degrees = when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                val matrix = Matrix().apply { postScale(-1f, 1f) }
                val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                
                if (result !== bitmap) bitmap.recycle()
                return result
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                val matrix = Matrix().apply { postScale(1f, -1f) }
                val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (result !== bitmap) bitmap.recycle()
                return result
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                val matrix = Matrix().apply { postScale(-1f, 1f); postRotate(90f) }
                val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (result !== bitmap) bitmap.recycle()
                return result
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                val matrix = Matrix().apply { postScale(-1f, 1f); postRotate(270f) }
                val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (result !== bitmap) bitmap.recycle()
                return result
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

    
    fun rotateBitmapFromBytes(bitmap: Bitmap, bytes: ByteArray): Bitmap =
        rotateFromExifStream(bitmap) { ByteArrayInputStream(bytes) }

    fun rotateFromExifStream(bitmap: Bitmap, inputStreamFactory: () -> InputStream?): Bitmap {
        val stream = inputStreamFactory() ?: return bitmap
        val exif = try { ExifInterface(stream) } catch (e: Exception) { Log.e(TAG, "ExifInterface创建失败", e); return bitmap } finally { stream.close() }
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        Log.d(TAG, "EXIF orientation=$orientation from stream")
        return applyExifRotation(bitmap, orientation)
    }
}
