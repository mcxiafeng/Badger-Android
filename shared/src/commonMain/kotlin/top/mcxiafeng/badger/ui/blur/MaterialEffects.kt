package top.mcxiafeng.badger.ui.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.textureBlurEffect
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.mcxiafeng.badger.ui.designsystem.BadgerGlassSpec
import top.mcxiafeng.badger.ui.designsystem.BadgerMaterialSpec
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "MaterialEffects"

// Mirrors HighlightStyle.kt LIGHT_REF（勿改，与 miuix shader 内参考系一致）
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f

/** 重力视为静止的阈值平方（|g_xy| > 0.1 ≈ 6° 倾斜） */
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

/**
 * L4 折射参数（像素值，调用方按密度从 [BadgerGlassSpec] 换算）。
 */
data class RefractionParams(
    val heightPx: Float,
    val amountPx: Float,
    /** 色散强度。0 = 无色散 shader（省 fillrate）；典型 0.3–0.5 */
    val chromaticAberration: Float = 0f,
    /** 深度方向位移（水滴类小控件开启） */
    val depthEffect: Boolean = false,
)

/**
 * 材质语义统一入口（K14，特效规格 §3 L1–L5）。
 *
 * 组件 API 面向「材质语义」而非「渲染路径」：调用方给 [BadgerMaterialSpec] token +
 * 可选折射参数，不感知底层是 RenderEffect 还是 Skia RuntimeEffect。
 *
 * 分层：L2 磨砂+饱和度（textureBlurEffect）→ L3 色调 tint（blendColors）→
 * L4 折射（可选，lens 在链最外层——miuix 校准顺序，uniform 缩放才正确）→
 * L5 边缘光学（[highlight]）。
 *
 * @param enabled 上层门控（效果档位 + 前后台）。false 或 backdrop 为 null 或平台不支持
 *   RuntimeShader 时回落 [containerColor] 纯色底（L3 tint 底，首帧绝不白屏/黑块）。
 * @param containerColor 采样不可用时的 fallback 底色
 * @param tint L3 色调层（磨砂路径 blendColors 进 shader；调用方按明暗从 token 取）
 */
fun Modifier.badgerSurface(
    material: BadgerMaterialSpec,
    shape: Shape,
    backdrop: Backdrop?,
    containerColor: Color,
    tint: Color,
    enabled: Boolean = true,
    refraction: RefractionParams? = null,
    highlight: Highlight? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
): Modifier {
    val canRender = enabled && backdrop != null && isRuntimeShaderSupported()
    if (!canRender) {
        if (enabled && backdrop == null) {
            BadgerLog.d(TAG, "badgerSurface fallback=tint-only (no backdrop), blur=${material.blurRadius}")
        }
        return this.background(containerColor, shape)
    }
    val nonNullBackdrop = backdrop ?: return this
    return this.drawBackdrop(
        backdrop = nonNullBackdrop,
        shape = { shape },
        effects = {
            // 链顺序（miuix 校准）：饱和度/模糊/着色先行，折射最后（最外层）
            textureBlurEffect(
                blurRadiusX = material.blurRadius.value,
                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                colors = BlurColors(
                    blendColors = listOf(BlendColorEntry(tint, BlurBlendMode.SrcOver)),
                    saturation = material.saturation,
                ),
            )
            if (refraction != null) {
                lens(
                    refractionHeight = refraction.heightPx,
                    refractionAmount = refraction.amountPx,
                    depthEffect = refraction.depthEffect,
                    chromaticAberration = refraction.chromaticAberration,
                )
            }
        },
        highlight = highlight?.let { h -> { h } },
        layerBlock = layerBlock,
        onDrawSurface = onDrawSurface,
    )
}

/**
 * L5/L6 边缘高光（特效规格 F3）：静态 BloomStroke 预设，[followTilt]=true 时主光源
 * 随设备重力旋转（TiltLight，倾斜设备光斑移动）。
 */
@Composable
fun rememberBadgerEdgeHighlight(
    isDark: Boolean,
    followTilt: Boolean = true,
    extraDegrees: Float = 0f,
    alpha: Float = 1f,
): Highlight {
    val preset = if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
    if (!followTilt) return preset.copy(alpha = alpha)
    val baseStyle = preset.style as BloomStroke
    val tilt by rememberDeviceTilt()
    val rotatedPrimary = remember(tilt, baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val gMagSq = gx * gx + gy * gy
        val (lx0, ly0) = if (gMagSq > GRAVITY_DIR_THRESHOLD_SQ) {
            val invMag = 1f / sqrt(gMagSq)
            (gx * invMag) to (gy * invMag)
        } else {
            0f to -1f
        }
        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        basePrimary.copy(
            position = LightPosition(
                x = LIGHT_REF_X + (c * lx0 - s * ly0),
                y = LIGHT_REF_Y + (s * lx0 + c * ly0),
                z = basePrimary.position.z,
            ),
        )
    }
    return remember(preset, rotatedPrimary, alpha) {
        preset.copy(style = baseStyle.copy(primaryLight = rotatedPrimary), alpha = alpha)
    }
}

/**
 * 水滴指示器专用玻璃元素（K14，特效规格 §4 水滴重写）。
 *
 * 按压驱动折射浮现：静息 = 纯 tint 表面（与磨砂底融为一体），
 * 按压中折射/色散随 [RefractionParams]（由 press 进度与边缘距离驱动）增强，
 * 高光随按压增强（按压实变）。拖拽/速度形变由调用方 graphicsLayer 负责。
 *
 * @param surfaceTint 静息表面色（调用方已按按压进度调制 alpha——按压变实）
 */
fun Modifier.badgerLiquidIndicator(
    backdrop: Backdrop,
    shape: Shape,
    surfaceTint: Color,
    refraction: RefractionParams?,
    highlight: Highlight?,
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        refraction?.let { r ->
            lens(
                refractionHeight = r.heightPx,
                refractionAmount = r.amountPx,
                depthEffect = r.depthEffect,
                chromaticAberration = r.chromaticAberration,
            )
        }
    },
    highlight = highlight?.let { h -> { h } },
    onDrawSurface = { drawRect(surfaceTint) },
)

/**
 * 圆角矩形边缘折射（L4）。Adapted from miuix example Lens.kt —
 * https://github.com/YuKongA/miuix (Apache 2.0)。
 *
 * SkSL 与 AGSL 同源（spec §8）：Android 走 AGSL RuntimeShader，iOS/Skiko 走
 * Skia RuntimeEffect，单一 shader 源双端复用。只有圆角弧段参与位移（直边零位移，
 * 控制填充率，spec §3）。
 */
internal fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding < refractionAmount) {
        padding = refractionAmount
    }

    val radii = roundedRectCornerRadii() ?: return

    val dispersionEnabled = chromaticAberration > 0f
    val shaderString =
        if (dispersionEnabled) {
            ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER
        } else {
            ROUNDED_RECT_REFRACTION_SHADER
        }
    val key = if (dispersionEnabled) "LiquidGlassLensDispersion" else "LiquidGlassLens"

    val sf = downscaleFactor.coerceAtLeast(1).toFloat()
    val scaledSizeW = size.width / sf
    val scaledSizeH = size.height / sf
    val scaledPadding = padding / sf
    val scaledRefractionHeight = refractionHeight / sf
    val scaledRefractionAmount = refractionAmount / sf
    val scaledRadii = FloatArray(radii.size) { radii[it] / sf }

    runtimeShaderEffect(
        key = key,
        shaderString = shaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", scaledSizeW, scaledSizeH)
        setFloatUniform("offset", -scaledPadding, -scaledPadding)
        setFloatUniform("cornerRadii", scaledRadii)
        setFloatUniform("refractionHeight", scaledRefractionHeight)
        setFloatUniform("refractionAmount", -scaledRefractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (dispersionEnabled) {
            setFloatUniform("chromaticAberration", chromaticAberration)
        }
    }
}

private fun BackdropEffectScope.roundedRectCornerRadii(): FloatArray? {
    val cornerShape = shape as? CornerBasedShape ?: return null
    val sizePx = size
    val maxRadius = sizePx.minDimension / 2f
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft = if (isLtr) cornerShape.topStart.toPx(sizePx, this) else cornerShape.topEnd.toPx(sizePx, this)
    val topRight = if (isLtr) cornerShape.topEnd.toPx(sizePx, this) else cornerShape.topStart.toPx(sizePx, this)
    val bottomRight = if (isLtr) cornerShape.bottomEnd.toPx(sizePx, this) else cornerShape.bottomStart.toPx(sizePx, this)
    val bottomLeft = if (isLtr) cornerShape.bottomStart.toPx(sizePx, this) else cornerShape.bottomEnd.toPx(sizePx, this)
    return floatArrayOf(
        topLeft.fastCoerceAtMost(maxRadius),
        topRight.fastCoerceAtMost(maxRadius),
        bottomRight.fastCoerceAtMost(maxRadius),
        bottomLeft.fastCoerceAtMost(maxRadius),
    )
}

private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

private const val ROUNDED_RECT_REFRACTION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

private const val ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""
