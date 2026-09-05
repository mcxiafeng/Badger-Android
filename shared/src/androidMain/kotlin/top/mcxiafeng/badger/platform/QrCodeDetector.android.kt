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

/** 检测前缩放的最大边长（原 QrCodeUtils fitToMax 参数） */
private const val QR_DETECT_MAX_DIM = 1000

/**
 * [KMP K10] QR 检测引擎 Android actual：WeChatQRCodeDetector（OpenCV native）。
 *
 * 逻辑自原 `pages/scanner/QrCodeUtils.kt` + `QrImagePreprocessor.kt` 原样迁移，
 * 仅把 Bitmap 入参换成 [PlatformImage] 包装。
 */
actual class QrCodeDetector {

    /**
     * 从图像中识别二维码内容，WeChatQRCodeDetector 自带预处理。
     */
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

    /**
     * 识别二维码并返回角点坐标，缩放后坐标按比例还原到原图空间。
     */
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
            // 释放已创建的 Mat，防止 native 内存泄漏
            points.forEach { it.release() }
            if (needRecycleWork) workBitmap.recycle()
        }
    }

    /**
     * 将二维码区域用白色遮盖，避免 OCR 误识别 QR 像素为文字。
     *
     * 无 QR 码时返回原 [image] 实例（调用方据实例同一性判断是否需释放）。
     */
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

/**
 * 从 WeChatQRCodeDetector 返回的 Mat 提取4个角点
 *
 * 每个 Mat 为 4行x2列 CV_32FC1：
 *   row0=(x0,y0), row1=(x1,y1), row2=(x2,y2), row3=(x3,y3)
 * WeChatQRCodeDetector 返回的角点已经是正确的顺时针顺序，直接使用，不做额外排序。
 * 之前 sortCorners 按 Y/X 重排会在二维码旋转时破坏原始顺序导致框偏移。
 */
internal fun extractCornersFromMat(mat: Mat): List<QrPoint> {
    val corners = mutableListOf<QrPoint>()
    for (i in 0 until 4) {
        val x = mat.get(i, 0)[0].toFloat()
        val y = mat.get(i, 1)[0].toFloat()
        corners.add(QrPoint(x, y))
    }
    return corners
}

/**
 * [KMP K10] 图像预处理工具（原 `pages/scanner/QrImagePreprocessor.kt` 原样迁移）：
 * 缩放 / 旋转 / EXIF 方向校正，供 QR 检测引擎与拍照/相册路径共用。
 */
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
                // flip/transpose 新图 !== 旧图，回收原 Bitmap
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

    /** [KMP K13c] 字节流变体：相册选图（GetContent → bytes）后直接校正 EXIF 方向。 */
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
