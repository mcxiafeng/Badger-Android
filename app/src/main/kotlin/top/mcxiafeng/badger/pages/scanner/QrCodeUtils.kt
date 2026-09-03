package top.mcxiafeng.badger.pages.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.king.wechat.qrcode.WeChatQRCodeDetector
import top.mcxiafeng.badger.pages.scanner.QrCodeWithBounds
import top.mcxiafeng.badger.pages.scanner.extractCornersFromMat

/**
 * 从 Bitmap 中识别二维码，WeChatQRCodeDetector 自带预处理。
 */
fun detectQrCodesFromBitmap(context: Context, bitmap: Bitmap): List<String> {
    val maxDim = 1000
    val workBitmap = QrImagePreprocessor.fitToMax(bitmap, maxDim)
    val needRecycleWork = workBitmap !== bitmap

    try {
        val results = WeChatQRCodeDetector.detectAndDecode(workBitmap)
        val filtered = results.filter { it.isNotEmpty() }
        Log.d("QrCodeUtils", "WeChatQRCode detected ${filtered.size} codes")
        return filtered
    } catch (e: Exception) {
        Log.d("QrCodeUtils", "WeChatQRCode detection failed: ${e.message}")
        return emptyList()
    } finally {
        if (needRecycleWork) workBitmap.recycle()
    }
}

/**
 * 识别二维码并返回角点坐标，缩放后坐标按比例还原到原图空间。
 */
fun detectQrCodesWithBounds(bitmap: Bitmap): List<QrCodeWithBounds> {
    val maxDim = 1000
    val workBitmap = QrImagePreprocessor.fitToMax(bitmap, maxDim)
    val needRecycleWork = workBitmap !== bitmap
    val scaleX = if (needRecycleWork) bitmap.width.toFloat() / workBitmap.width else 1f
    val scaleY = if (needRecycleWork) bitmap.height.toFloat() / workBitmap.height else 1f

    val points = mutableListOf<org.opencv.core.Mat>()
    try {
        val results = WeChatQRCodeDetector.detectAndDecode(workBitmap, points)
        val filtered = results.mapIndexedNotNull { index, text ->
            if (text.isEmpty()) null
            else {
                val matCorners = if (index < points.size) {
                    extractCornersFromMat(points[index]).map { offset ->
                        Offset(offset.x * scaleX, offset.y * scaleY)
                    }
                } else emptyList()
                QrCodeWithBounds(text, matCorners)
            }
        }
        return filtered
    } catch (e: Exception) {
        Log.d("QrCodeUtils", "WeChatQRCode detection with bounds failed: ${e.message}")
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
 * @param bitmap 原始图片（不会被修改）
 * @param qrBounds QR 码角点列表，每个码 4 个角点
 * @param paddingPx 遮盖框额外向外扩展的像素，防止边缘残留
 * @return 新 Bitmap，QR 区域已遮盖；无 QR 码时返回原图
 */
fun maskQrRegions(bitmap: Bitmap, qrBounds: List<QrCodeWithBounds>, paddingPx: Int = 16): Bitmap {
    if (qrBounds.isEmpty()) return bitmap

    val masked = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(masked)
    val paint = Paint().apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }

    for (qr in qrBounds) {
        if (qr.corners.size < 4) continue
        val minX = (qr.corners.minOf { it.x } - paddingPx).coerceAtLeast(0f)
        val minY = (qr.corners.minOf { it.y } - paddingPx).coerceAtLeast(0f)
        val maxX = (qr.corners.maxOf { it.x } + paddingPx).coerceAtMost(bitmap.width.toFloat())
        val maxY = (qr.corners.maxOf { it.y } + paddingPx).coerceAtMost(bitmap.height.toFloat())
        canvas.drawRect(minX, minY, maxX, maxY, paint)
    }
    Log.d("QrCodeUtils", "maskQrRegions: masked ${qrBounds.size} QR regions")
    return masked
}
