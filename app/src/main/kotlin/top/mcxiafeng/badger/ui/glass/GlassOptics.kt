package top.mcxiafeng.badger.ui.glass

import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import top.mcxiafeng.badger.ui.designsystem.BadgerDesignUnits
import top.mcxiafeng.badger.ui.designsystem.G2_CORNER_CONTROL
import top.mcxiafeng.badger.ui.designsystem.G2_CORNER_MIDPOINT
import kotlin.math.ceil

private const val SAMPLE_SCALE = 0.25f
private const val DESIGN_UNITS_PER_DP = BadgerDesignUnits.PER_DP
private const val PERCENT_UNITS = 100f
private const val FROST_SIGMA_DP = 9f / DESIGN_UNITS_PER_DP
private const val REFRACTION_UNIT_DP = 18f
private const val DISPERSION_UNIT_DP = 6f
private const val SAMPLE_MARGIN_DP = REFRACTION_UNIT_DP * DEFAULT_GLASS_REFRACTION / PERCENT_UNITS +
    DISPERSION_UNIT_DP * DEFAULT_GLASS_DISPERSION / PERCENT_UNITS + FROST_SIGMA_DP * 4f

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlassOptics {
    private val lens = RuntimeShader(LENS_SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val source by lazy { RenderNode("Badger glass backdrop") }
    private var gaussian: RenderEffect? = null
    private var gaussianDensity = 0f
    private var gaussianSaturation = 0f
    private var solidColor = Color.Unspecified
    private var effect: RenderEffect? = null
    private var effectSize = Size.Unspecified
    private var effectTopLeft = CornerRadius(-1f)
    private var effectTopRight = CornerRadius(-1f)
    private var effectBottomRight = CornerRadius(-1f)
    private var effectBottomLeft = CornerRadius(-1f)
    private var effectTint = Color.Unspecified
    private var effectDensity = 0f
    private var effectFrosted = false
    private var effectSaturation = 0f
    private var effectParameters = GlassOpticalParameters.Default

    fun draw(
        scope: DrawScope,
        panel: RoundRect,
        backdrop: GlassBackdrop?,
        offset: Offset?,
        underlay: Color,
        tint: Color,
        frosted: Boolean,
        saturation: Float,
        opticalParameters: GlassOpticalParameters,
    ) = with(scope) {
        if (backdrop == null || offset == null) {
            if (frosted) drawSolid(panel, underlay, tint, opticalParameters)
        } else {
            drawBackdrop(panel, backdrop, offset, underlay, tint, frosted, saturation, opticalParameters)
        }
    }

    private fun DrawScope.drawSolid(panel: RoundRect, color: Color, tint: Color, opticalParameters: GlassOpticalParameters) {
        if (solidColor != color) {
            solidColor = color
            val argb = color.toArgb()
            lens.setInputShader("backdrop", LinearGradient(0f, 0f, 1f, 0f, argb, argb, Shader.TileMode.CLAMP))
        }
        setLens(panel, Offset(panel.left + panel.width / 2f, panel.top + panel.height / 2f),
            1f, tint, opticalParameters)
        paint.shader = lens
        drawContext.canvas.nativeCanvas.drawRect(panel.left, panel.top, panel.right, panel.bottom, paint)
    }

    private fun DrawScope.drawBackdrop(
        panel: RoundRect,
        backdrop: GlassBackdrop,
        offset: Offset,
        underlay: Color,
        tint: Color,
        frosted: Boolean,
        saturation: Float,
        opticalParameters: GlassOpticalParameters,
    ) {
        // Limit the Gaussian pass to the lens and its sampling margin. The source stays on the GPU.
        val scale = if (frosted) SAMPLE_SCALE else 1f
        val margin = SAMPLE_MARGIN_DP * density
        val origin = Offset(panel.left - margin, panel.top - margin)
        val width = ceil((panel.width + 2 * margin) * scale).toInt()
        val height = ceil((panel.height + 2 * margin) * scale).toInt()
        val currentEffect = backdropEffect(panel, margin, scale, tint, frosted, saturation, opticalParameters)
        recordPatch(backdrop, offset - origin, underlay, width, height, scale)
        source.setRenderEffect(currentEffect)
        val canvas = drawContext.canvas.nativeCanvas
        val saved = canvas.save()
        canvas.translate(origin.x, origin.y)
        canvas.scale(1f / scale, 1f / scale)
        canvas.drawRenderNode(source)
        canvas.restoreToCount(saved)
    }

    private fun DrawScope.recordPatch(
        backdrop: GlassBackdrop,
        offset: Offset,
        underlay: Color,
        width: Int,
        height: Int,
        scale: Float,
    ) {
        source.setPosition(0, 0, width, height)
        val recording = source.beginRecording(width, height)
        try {
            recording.drawColor(if (underlay == Color.Transparent) AndroidColor.TRANSPARENT else underlay.toArgb())
            recording.scale(scale, scale)
            draw(this, layoutDirection, Canvas(recording), Size(width / scale, height / scale),
                drawContext.graphicsLayer) {
                translate(left = offset.x, top = offset.y) { drawLayer(backdrop.layer) }
            }
        } finally {
            source.endRecording()
        }
    }

    private fun DrawScope.frost(saturation: Float): RenderEffect {
        if (gaussianDensity != density || gaussianSaturation != saturation || gaussian == null) {
            gaussianDensity = density
            gaussianSaturation = saturation
            val sigma = FROST_SIGMA_DP * density * SAMPLE_SCALE
            // The blur averages the backdrop, which desaturates it; the chained matrix puts the
            // chroma back and lifts each channel so the frosted pane stays glass, not milk.
            val blur = RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)
            val restore = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(glassBlurColorMatrix(saturation)))
            gaussian = RenderEffect.createChainEffect(restore, blur)
        }
        return checkNotNull(gaussian)
    }

    private fun DrawScope.backdropEffect(
        panel: RoundRect, margin: Float, scale: Float, tint: Color, frosted: Boolean,
        saturation: Float, opticalParameters: GlassOpticalParameters,
    ): RenderEffect {
        val size = Size(panel.width, panel.height)
        val cached = effect
        if (cached != null && effectSize == size && effectTopLeft == panel.topLeftCornerRadius &&
            effectTopRight == panel.topRightCornerRadius && effectBottomRight == panel.bottomRightCornerRadius &&
            effectBottomLeft == panel.bottomLeftCornerRadius && effectParameters == opticalParameters &&
            effectTint == tint && effectDensity == density && effectFrosted == frosted &&
            effectSaturation == saturation) return cached

        setLens(panel, Offset(margin + panel.width / 2f, margin + panel.height / 2f), scale, tint, opticalParameters)
        val optics = RenderEffect.createRuntimeShaderEffect(lens, "backdrop")
        val result = if (frosted) RenderEffect.createChainEffect(optics, frost(saturation)) else optics
        if (effect == null) Log.d(GLASS_LOG_TAG, "GPU backdrop effect enabled; frosted=$frosted; sampleScale=$scale; cornerProfile=G2; optics=$opticalParameters; blurSaturation=$saturation")
        effect = result
        effectSize = size
        effectTopLeft = panel.topLeftCornerRadius
        effectTopRight = panel.topRightCornerRadius
        effectBottomRight = panel.bottomRightCornerRadius
        effectBottomLeft = panel.bottomLeftCornerRadius
        effectTint = tint
        effectDensity = density
        effectFrosted = frosted
        effectSaturation = saturation
        effectParameters = opticalParameters
        return result
    }

    private fun DrawScope.setLens(
        panel: RoundRect, center: Offset, scale: Float, tint: Color, opticalParameters: GlassOpticalParameters,
    ) {
        fun radius(corner: CornerRadius) = minOf(corner.x, corner.y, panel.width / 2f, panel.height / 2f).coerceAtLeast(0f)
        val scaledDensity = density * scale
        lens.setFloatUniform("center", center.x * scale, center.y * scale)
        lens.setFloatUniform("halfSize", panel.width * scale / 2f, panel.height * scale / 2f)
        lens.setFloatUniform("corners", radius(panel.topLeftCornerRadius) * scale, radius(panel.topRightCornerRadius) * scale,
            radius(panel.bottomRightCornerRadius) * scale, radius(panel.bottomLeftCornerRadius) * scale)
        lens.setFloatUniform("edgeWidth", opticalParameters.depth / DESIGN_UNITS_PER_DP * scaledDensity)
        lens.setFloatUniform("displacement", REFRACTION_UNIT_DP * opticalParameters.refraction / PERCENT_UNITS * scaledDensity)
        lens.setFloatUniform("dispersion", DISPERSION_UNIT_DP * opticalParameters.dispersion / PERCENT_UNITS * scaledDensity)
        lens.setColorUniform("veil", tint.toArgb())
    }
}

private const val G2_COARSE_INTERVALS = 4
private const val G2_NEWTON_ITERATIONS = 5
private const val LENS_SHADER = """
    uniform shader backdrop;
    uniform float2 center;
    uniform float2 halfSize;
    uniform float4 corners;
    uniform float edgeWidth;
    uniform float displacement;
    uniform float dispersion;
    layout(color) uniform half4 veil;
    const float mid = $G2_CORNER_MIDPOINT;
    const float control = $G2_CORNER_CONTROL;
    const float a = -1.0 + 3.0 * control - 5.0 * mid;
    const float b = 3.0 - 6.0 * control + 6.0 * mid;
    const float c = -3.0 + 3.0 * control;

    float2 g2Point(float t) {
        return float2(1.0 + t * (c + t * (b + t * a)), mid * t * t * t);
    }
    float2 g2Tangent(float t) {
        return float2(c + t * (2.0 * b + 3.0 * a * t), 3.0 * mid * t * t);
    }
    float2 g2Acceleration(float t) {
        return float2(2.0 * b + 6.0 * a * t, 6.0 * mid * t);
    }
    float3 g2Edge(float2 point) {
        float2 inset = halfSize - abs(point);
        float radius = point.x >= 0.0 ? (point.y >= 0.0 ? corners.z : corners.y) :
            (point.y >= 0.0 ? corners.w : corners.x);
        if (radius <= 0.0 || inset.x >= radius || inset.y >= radius) {
            float2 normal = inset.x < inset.y ? float2(sign(point.x), 0.0) : float2(0.0, sign(point.y));
            return float3(normal, max(min(inset.x, inset.y), 0.0));
        }
        float2 local = inset / radius;
        bool swapped = local.x < local.y;
        float2 probePoint = swapped ? local.yx : local;
        float closestT = 0.0;
        float bestDistance = 1e30;
        for (int probe = 0; probe <= $G2_COARSE_INTERVALS; probe++) {
            float t = float(probe) / float($G2_COARSE_INTERVALS);
            float2 delta = g2Point(t) - probePoint;
            float candidate = dot(delta, delta);
            if (candidate < bestDistance) { bestDistance = candidate; closestT = t; }
        }
        for (int iteration = 0; iteration < $G2_NEWTON_ITERATIONS; iteration++) {
            float2 delta = g2Point(closestT) - probePoint;
            float2 tangent = g2Tangent(closestT);
            float denominator = dot(tangent, tangent) + dot(delta, g2Acceleration(closestT));
            if (denominator > 0.000001) {
                float t = clamp(closestT - dot(delta, tangent) / denominator, 0.0, 1.0);
                float2 nextDelta = g2Point(t) - probePoint;
                float candidate = dot(nextDelta, nextDelta);
                if (candidate < bestDistance) { bestDistance = candidate; closestT = t; }
            }
        }
        float2 tangent = g2Tangent(closestT);
        float2 normal = normalize(float2(tangent.y, -tangent.x));
        float inside = max(dot(probePoint - g2Point(closestT), normal) * radius, 0.0);
        normal = swapped ? normal.yx : normal;
        return float3(normal * sign(point), inside);
    }

    half4 main(float2 p) {
        float2 point = p - center;
        if (abs(point.x) > halfSize.x || abs(point.y) > halfSize.y) return backdrop.eval(p);
        float3 edge = g2Edge(point);
        float inside = edge.z;
        float2 normal = edge.xy;
        float band = edgeWidth > 0.0 ? 1.0 - smoothstep(0.0, edgeWidth, inside) : 0.0;
        float bend = band * band;
        float2 lightPath = p - normal * displacement * bend;
        float2 fringe = normal * dispersion * bend;
        half3 color = half3(backdrop.eval(lightPath + fringe).r,
            backdrop.eval(lightPath).g, backdrop.eval(lightPath - fringe).b);
        color = mix(color, veil.rgb, veil.a);
        // Additive light on both horizontal edges; no darkening pass on the lower edge.
        float rim = exp(-inside * 1.4) * band;
        float horizontalLight = abs(normal.y);
        color += half3(rim * (0.08 + horizontalLight * 0.44));
        return half4(color, 1.0);
    }
"""
