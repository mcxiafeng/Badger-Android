package top.mcxiafeng.badger.ui.blur

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "BlurHelper"

/**
 * 模糊强度预设
 */
enum class BlurIntensity {
    /** 轻薄：微弱模糊 + 高透明 */
    THIN,
    /** 标准：平衡模糊 + 半透明 */
    THICK,
    /** Apple Dock 风格：强模糊 + 低透明 + 大圆角 */
    APPLE_DOCK
}

/**
 * 将 BlurIntensity 映射到 HazeStyle。
 * 不依赖 haze-materials 附加库，直接构造 HazeStyle。
 */
fun BlurIntensity.toHazeStyle(): HazeStyle {
    val (blurRadius, tintAlpha, bgAlpha) = when (this) {
        BlurIntensity.THIN -> Triple(6.dp, 0.08f, 0.04f)
        BlurIntensity.THICK -> Triple(12.dp, 0.12f, 0.06f)
        BlurIntensity.APPLE_DOCK -> Triple(20.dp, 0.18f, 0.10f)
    }
    BadgerLog.d(TAG, "BlurHelper: toHazeStyle, intensity=${this.name}, blurRadius=$blurRadius, tintAlpha=$tintAlpha, bgAlpha=$bgAlpha")
    return HazeStyle(
        blurRadius = blurRadius,
        tint = HazeTint(
            color = Color.White.copy(alpha = tintAlpha),
        ),
        backgroundColor = Color.White.copy(alpha = bgAlpha),
    )
}

/**
 * 将 BlurIntensity 映射到背景色 alpha
 */
fun BlurIntensity.toBackgroundAlpha(): Float = when (this) {
    BlurIntensity.THIN -> 0.38f
    BlurIntensity.THICK -> 0.48f
    BlurIntensity.APPLE_DOCK -> 0.32f
}

/**
 * 内容区域：标记为 Haze 源（被采样的背景内容）。
 */
fun Modifier.applyBlurSource(hazeState: HazeState): Modifier =
    this.hazeSource(hazeState)

/**
 * 内容区域：LayerBackdrop 注册，用于 GPU 路径的 drawBackdrop/lens 折射采样。
 */
fun Modifier.applyLayerBackdrop(backdrop: LayerBackdrop?): Modifier {
    BadgerLog.d(TAG, "BlurHelper: applyLayerBackdrop, backdrop=${backdrop != null}")
    return if (backdrop != null) this.layerBackdrop(backdrop) else this
}

/**
 * 导航栏/模糊区域：应用 Haze 模糊效果。
 *
 * 使用方式：modifier.applyBlurBehind(hazeState, shape, backgroundColor, blurIntensity)
 *
 * @param hazeState Haze 状态
 * @param shape 裁剪形状
 * @param backgroundColor 基础背景色
 * @param blurIntensity 模糊强度
 */
fun Modifier.applyBlurBehind(
    hazeState: HazeState,
    shape: Shape,
    blurIntensity: BlurIntensity,
): Modifier {
    BadgerLog.d(TAG, "BlurHelper: applying Haze blur, intensity=$blurIntensity, blurEnabled=${hazeState.blurEnabled}")
    return this
        .clip(shape)
        .hazeEffect(
            state = hazeState,
            style = blurIntensity.toHazeStyle(),
        )
}