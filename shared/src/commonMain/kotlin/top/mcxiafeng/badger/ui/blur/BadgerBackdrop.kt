package top.mcxiafeng.badger.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "BadgerBackdrop"

/**
 * L1 背景采样源（特效规格 §3）——一屏唯一。
 *
 * App 顶层创建一次，浮层（导航栏等）经 [Modifier.badgerSurface] 共享同源采样。
 */
@Composable
fun rememberBadgerBackdrop(): LayerBackdrop {
    BadgerLog.d(TAG, "rememberBadgerBackdrop")
    return rememberLayerBackdrop()
}

/**
 * 标记内容区为采样源。backdrop 为 null（效果关闭/经典形态）时是零开销 no-op。
 */
fun Modifier.badgerBackdropSource(backdrop: LayerBackdrop?): Modifier {
    if (backdrop == null) return this
    BadgerLog.d(TAG, "badgerBackdropSource attached")
    return this.layerBackdrop(backdrop)
}

/**
 * 双源合成 Backdrop：先画 [first]（页面内容）再画 [second]（导航栏 Tab 内容）。
 *
 * 水滴指示器用同一源折射「页面 + Tab 图标/文字」，实现 iOS 26 水滴融合观感。
 * Adapted from Kyant0/AndroidLiquidGlass CombinedBackdrop (Apache 2.0)，
 * 经 miuix example 同名组件转写。
 */
@Stable
class CombinedBackdrop(
    val first: Backdrop,
    val second: Backdrop,
) : Backdrop {

    override val isCoordinatesDependent: Boolean =
        first.isCoordinatesDependent || second.isCoordinatesDependent

    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Composable
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }
