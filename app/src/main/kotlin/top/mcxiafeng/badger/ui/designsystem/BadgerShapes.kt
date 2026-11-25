package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip as nativeClip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

internal const val G2_CORNER_MIDPOINT = 0.29289323f
internal const val G2_CORNER_CONTROL = 0.9f

@Immutable
internal data class G2CornerCubic(val start: Offset, val control1: Offset, val control2: Offset, val end: Offset)

/** Normalized corner, from (1,0) to (0,1). Each line join has exactly zero curvature. */
internal object G2CornerGeometry {
    private const val MID_TANGENT = 2f * G2_CORNER_MIDPOINT
    val segments = listOf(
        G2CornerCubic(Offset(1f, 0f), Offset(G2_CORNER_CONTROL, 0f), Offset(MID_TANGENT, 0f),
            Offset(G2_CORNER_MIDPOINT, G2_CORNER_MIDPOINT)),
        G2CornerCubic(Offset(G2_CORNER_MIDPOINT, G2_CORNER_MIDPOINT), Offset(0f, MID_TANGENT),
            Offset(0f, G2_CORNER_CONTROL), Offset(0f, 1f)),
    )
}

/** Content uses one design radius; controls can retain their own radius while sharing the G2 profile. */
internal object BadgerShapes {
    val Surface: Shape = G2RoundedCornerShape(CornerSize(BadgerRadius.surface))
    val Capsule: Shape = G2RoundedCornerShape(CornerSize(50))
    fun rounded(radius: Dp): Shape = if (radius == BadgerRadius.surface) Surface else G2RoundedCornerShape(CornerSize(radius))
    fun corners(topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp): Shape = G2RoundedCornerShape(
        CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart))
}

@Immutable
private data class G2RoundedCornerShape(
    private val topStart: CornerSize,
    private val topEnd: CornerSize = topStart,
    private val bottomEnd: CornerSize = topStart,
    private val bottomStart: CornerSize = topStart,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        fun radius(corner: CornerSize) = CornerRadius(corner.toPx(size, density).coerceAtLeast(0f))
        val rtl = layoutDirection == LayoutDirection.Rtl
        val bounds = RoundRect(0f, 0f, size.width, size.height,
            radius(if (rtl) topEnd else topStart), radius(if (rtl) topStart else topEnd),
            radius(if (rtl) bottomStart else bottomEnd), radius(if (rtl) bottomEnd else bottomStart))
        return Outline.Generic(Path().apply { addG2RoundRect(bounds) })
    }
}

/** Keep visual corner clipping independent of the rectangular accessibility/touch target. */
internal fun Modifier.continuousClip(shape: Shape): Modifier {
    if (shape !is G2RoundedCornerShape) return nativeClip(shape)
    return drawWithCache {
        val path = (shape.createOutline(size, layoutDirection, this) as Outline.Generic).path
        onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
    }
}

private enum class Corner { TopRight, BottomRight, BottomLeft, TopLeft }

/** Shared by content clipping, glass rims, shadow outlines and the moving accent mask. */
internal fun Path.addG2RoundRect(bounds: RoundRect) {
    if (bounds.width <= 0f || bounds.height <= 0f) return
    fun radius(corner: CornerRadius) = CornerRadius(
        minOf(minOf(corner.x, corner.y), bounds.width / 2f, bounds.height / 2f).coerceAtLeast(0f))
    val topLeft = radius(bounds.topLeftCornerRadius)
    val topRight = radius(bounds.topRightCornerRadius)
    val bottomRight = radius(bounds.bottomRightCornerRadius)
    val bottomLeft = radius(bounds.bottomLeftCornerRadius)
    moveTo(bounds.left + topLeft.x, bounds.top)
    lineTo(bounds.right - topRight.x, bounds.top)
    addCorner(bounds, topRight, Corner.TopRight)
    lineTo(bounds.right, bounds.bottom - bottomRight.y)
    addCorner(bounds, bottomRight, Corner.BottomRight)
    lineTo(bounds.left + bottomLeft.x, bounds.bottom)
    addCorner(bounds, bottomLeft, Corner.BottomLeft)
    lineTo(bounds.left, bounds.top + topLeft.y)
    addCorner(bounds, topLeft, Corner.TopLeft)
    close()
}

private fun Path.addCorner(bounds: RoundRect, radius: CornerRadius, corner: Corner) {
    fun transform(point: Offset): Offset = when (corner) {
        Corner.TopRight -> Offset(bounds.right - point.x * radius.x, bounds.top + point.y * radius.y)
        Corner.BottomRight -> Offset(bounds.right - point.y * radius.x, bounds.bottom - point.x * radius.y)
        Corner.BottomLeft -> Offset(bounds.left + point.x * radius.x, bounds.bottom - point.y * radius.y)
        Corner.TopLeft -> Offset(bounds.left + point.y * radius.x, bounds.top + point.x * radius.y)
    }
    G2CornerGeometry.segments.forEach { segment ->
        val first = transform(segment.control1)
        val second = transform(segment.control2)
        val end = transform(segment.end)
        cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
    }
}
