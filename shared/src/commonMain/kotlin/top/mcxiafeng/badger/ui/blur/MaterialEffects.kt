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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "MaterialEffects"

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f

private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

data class RefractionParams(
    val heightPx: Float,
    val amountPx: Float,
    
    val chromaticAberration: Float = 0f,
    
    val depthEffect: Boolean = false,
)

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

internal fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount == 0f) return

    val absAmount = abs(refractionAmount)
    if (padding < absAmount) {
        padding = absAmount
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
        setFloatUniform("refractionAmount", scaledRefractionAmount)
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
    float2 dispersionOffset = d * grad * chromaticAberration;

    half r = content.eval(refractedCoord + dispersionOffset).r;
    half g = content.eval(refractedCoord).g;
    half b = content.eval(refractedCoord - dispersionOffset).b;
    half a = content.eval(refractedCoord).a;

    return half4(r, g, b, a);
}
"""
