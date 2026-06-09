package top.mcxiafeng.badger.pages.scanner

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.opencv.core.Mat

/**
 * 从 PreviewView 内部获取实际渲染的 surface 分辨率
 *
 * PreviewView FILL_CENTER 会基于 Preview surface 分辨率做缩放，
 * 而非 ImageAnalysis 的 bitmap 分辨率。两者可能不同导致坐标映射偏差。
 */
fun PreviewView.getSurfaceSize(): Size {
    // PreviewView 内部第一个子 View 是 SurfaceView 或 TextureView
    val child = (this as? ViewGroup)?.getChildAt(0) ?: return Size.Zero
    return Size(child.width.toFloat(), child.height.toFloat())
}

/**
 * 构建从 bitmap 坐标系到 Compose PreviewView 坐标系的映射函数
 *
 * PreviewView 的 scaleType = FILL_CENTER 会将 preview 等比缩放铺满整个 view，
 * 短边对齐 view 短边、长边溢出被裁，所以映射只需一次等比 FILL_CENTER。
 *
 * 重要：不再分两步走（bitmap → surface → view）。CameraX 同 camera 的
 * Preview / ImageAnalysis 共享 sensor crop，FOV 一致；分两步缩放时若 bitmap
 * 和 surface 长宽比不一致（比如 1920×1080 vs 1280×960），scaleX / scaleY
 * 不等就会把正方形二维码横向/纵向拉伸成平行四边形。
 *
 * @param bitmapSize ImageAnalysis 旋转后的 bitmap 尺寸
 * @param viewSize PreviewView 的 layout 尺寸
 */
fun buildBitmapToComposeMapper(
    bitmapSize: Size,
    viewSize: Size
): (Offset) -> Offset {
    if (bitmapSize.width <= 0f || bitmapSize.height <= 0f) return { Offset.Zero }
    if (viewSize.width <= 0f || viewSize.height <= 0f) return { Offset.Zero }

    // FILL_CENTER：等比缩放，取较大缩放比让短边刚好贴齐 view 短边（长边溢出被裁掉）
    val fillScale = maxOf(
        viewSize.width / bitmapSize.width,
        viewSize.height / bitmapSize.height
    )
    val fillOffsetX = (viewSize.width - bitmapSize.width * fillScale) / 2f
    val fillOffsetY = (viewSize.height - bitmapSize.height * fillScale) / 2f

    Log.d("QrCoordinateMapper", "buildMapper: bitmap=$bitmapSize, view=$viewSize, " +
            "fillScale=${fillScale.format(3)}, fillOffset=(${fillOffsetX.format(1)},${fillOffsetY.format(1)})")

    return { offset ->
        Offset(offset.x * fillScale + fillOffsetX, offset.y * fillScale + fillOffsetY)
    }
}

private fun Float.format(decimals: Int): String = String.format("%.${decimals}f", this)

/**
 * 将位图像素坐标映射到 Compose UI 坐标（旧版，回退用）
 *
 * 仅使用 bitmap 和 preview 尺寸，假设 Preview surface 与 bitmap 尺寸相同。
 * 在 surface 分辨率与 bitmap 一致时结果正确，否则会有偏差。
 */
fun mapBitmapToCompose(
    bitmapX: Float,
    bitmapY: Float,
    bitmapSize: Size,
    previewSize: Size
): Offset {
    if (bitmapSize.width <= 0f || bitmapSize.height <= 0f) return Offset.Zero
    if (previewSize.width <= 0f || previewSize.height <= 0f) return Offset.Zero
    val scale = maxOf(
        previewSize.width / bitmapSize.width,
        previewSize.height / bitmapSize.height
    )
    val offsetX = (previewSize.width - bitmapSize.width * scale) / 2f
    val offsetY = (previewSize.height - bitmapSize.height * scale) / 2f
    return Offset(bitmapX * scale + offsetX, bitmapY * scale + offsetY)
}

/**
 * 从 WeChatQRCodeDetector 返回的 Mat 提取4个角点
 *
 * 每个 Mat 为 4行x2列 CV_32FC1：
 *   row0=(x0,y0), row1=(x1,y1), row2=(x2,y2), row3=(x3,y3)
 * WeChatQRCodeDetector 返回的角点已经是正确的顺时针顺序，直接使用，不做额外排序。
 * 之前 sortCorners 按 Y/X 重排会在二维码旋转时破坏原始顺序导致框偏移。
 */
fun extractCornersFromMat(mat: Mat): List<Offset> {
    val corners = mutableListOf<Offset>()
    for (i in 0 until 4) {
        val x = mat.get(i, 0)[0].toFloat()
        val y = mat.get(i, 1)[0].toFloat()
        corners.add(Offset(x, y))
    }
    return corners
}

/**
 * 将4个角点归一化为固定顺序：左上, 右上, 右下, 左下
 *
 * WeChatQRCodeDetector / OpenCV 返回的角点顺序不固定，
 * 直接使用会导致框选四角与实际位置不对应。
 * 按 Y 分上下两组，再按 X 排左右，得到 TL/TR/BL/BR，最后重排为 TL/TR/BR/BL。
 */
fun sortCorners(corners: List<Offset>): List<Offset> {
    if (corners.size != 4) return corners
    val sortedByY = corners.sortedBy { it.y }
    val topTwo = sortedByY.take(2).sortedBy { it.x }
    val bottomTwo = sortedByY.drop(2).sortedBy { it.x }
    // TL=topTwo[0], TR=topTwo[1], BR=bottomTwo[1], BL=bottomTwo[0]
    return listOf(topTwo[0], topTwo[1], bottomTwo[1], bottomTwo[0])
}
