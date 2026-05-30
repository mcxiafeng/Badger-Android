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
 * 核心思路：PreviewView FILL_CENTER 会将 Preview surface 等比缩放铺满 view，
 * 映射需要经过两步：
 * 1. bitmap → Preview surface：等比缩放（两者 FOV 相同，只是分辨率不同）
 * 2. Preview surface → PreviewView：FILL_CENTER 缩放（含裁剪/偏移）
 *
 * @param bitmapSize ImageAnalysis 旋转后的 bitmap 尺寸
 * @param surfaceSize PreviewView 内部 surface 的实际尺寸
 * @param viewSize PreviewView 的 layout 尺寸
 */
fun buildBitmapToComposeMapper(
    bitmapSize: Size,
    surfaceSize: Size,
    viewSize: Size
): (Offset) -> Offset {
    if (bitmapSize.width <= 0f || bitmapSize.height <= 0f) return { Offset.Zero }
    if (viewSize.width <= 0f || viewSize.height <= 0f) return { Offset.Zero }

    // 若 surface 尺寸未获取到，回退为假设 surface = bitmap
    val effectiveSurfaceSize = if (surfaceSize.width > 0f && surfaceSize.height > 0f) surfaceSize else bitmapSize

    // Step 1: bitmap → surface 的等比缩放
    // 两者 FOV 相同（CameraX 保证同 camera 的 use cases 共享 sensor crop），
    // 只是分辨率可能不同。缩放比 = surface / bitmap。
    val bitmapToSurfaceScaleX = effectiveSurfaceSize.width / bitmapSize.width
    val bitmapToSurfaceScaleY = effectiveSurfaceSize.height / bitmapSize.height

    // Step 2: surface → view 的 FILL_CENTER 缩放
    // 与 PreviewView 的 FILL_CENTER 逻辑完全一致
    val fillScale = maxOf(
        viewSize.width / effectiveSurfaceSize.width,
        viewSize.height / effectiveSurfaceSize.height
    )
    val fillOffsetX = (viewSize.width - effectiveSurfaceSize.width * fillScale) / 2f
    val fillOffsetY = (viewSize.height - effectiveSurfaceSize.height * fillScale) / 2f

    // 合并两步变换：bitmap → surface → view
    val totalScaleX = bitmapToSurfaceScaleX * fillScale
    val totalScaleY = bitmapToSurfaceScaleY * fillScale
    // bitmap(0,0) → surface(0,0) → view(fillOffsetX, fillOffsetY)
    val totalOffsetX = fillOffsetX
    val totalOffsetY = fillOffsetY

    Log.d("QrCoordinateMapper", "buildMapper: bitmap=$bitmapSize, surface=$effectiveSurfaceSize, view=$viewSize, " +
            "b2s=(${bitmapToSurfaceScaleX.format(3)},${bitmapToSurfaceScaleY.format(3)}), " +
            "fillScale=${fillScale.format(3)}, fillOffset=(${fillOffsetX.format(1)},${fillOffsetY.format(1)}), " +
            "total=(${totalScaleX.format(3)},${totalScaleY.format(3)})+(${totalOffsetX.format(1)},${totalOffsetY.format(1)})")

    return { offset ->
        Offset(offset.x * totalScaleX + totalOffsetX, offset.y * totalScaleY + totalOffsetY)
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
 * 顺序不固定，需要通过 sortCorners 归一化。
 */
fun extractCornersFromMat(mat: Mat): List<Offset> {
    val corners = mutableListOf<Offset>()
    for (i in 0 until 4) {
        val x = mat.get(i, 0)[0].toFloat()
        val y = mat.get(i, 1)[0].toFloat()
        corners.add(Offset(x, y))
    }
    return sortCorners(corners)
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
