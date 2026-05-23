package top.mcxiafeng.badger.pages.scanner

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.opencv.core.Mat

/**
 * 将位图像素坐标映射到Compose UI坐标
 *
 * PreviewView 使用 FILL_CENTER 缩放：图片等比缩放填满视图，可能裁剪一条边。
 * 缩放比例取 max，保证画面无边框铺满屏幕。
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