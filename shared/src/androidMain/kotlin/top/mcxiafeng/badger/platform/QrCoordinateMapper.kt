package top.mcxiafeng.badger.platform

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * [KMP K10] 相机预览坐标映射工具（原 `pages/scanner/QrCoordinateMapper.kt` 主体迁移）。
 * QR/文字块检测结果（bitmap 像素空间）→ Compose 坐标系的 FILL_CENTER 等比映射。
 */

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

    return { offset ->
        Offset(offset.x * fillScale + fillOffsetX, offset.y * fillScale + fillOffsetY)
    }
}
