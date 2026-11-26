package top.mcxiafeng.badger.ui.glass

import androidx.compose.runtime.Immutable

/**
 * Glass lighting is deliberately restrained: the previous depth/refraction/dispersion values read
 * as a thick bevel with visible colour fringing on every surface. These weaker defaults keep the
 * lens readable as glass without drawing attention to the effect itself.
 */
internal const val DEFAULT_GLASS_DEPTH = 38f
internal const val DEFAULT_GLASS_REFRACTION = 52f
internal const val DEFAULT_GLASS_DISPERSION = 14f

/** Optical settings use the same design units as the glass sample, before density conversion. */
@Immutable
internal data class GlassOpticalParameters(
    val depth: Float = DEFAULT_GLASS_DEPTH,
    val refraction: Float = DEFAULT_GLASS_REFRACTION,
    val dispersion: Float = DEFAULT_GLASS_DISPERSION,
) {
    fun faded(progress: Float): GlassOpticalParameters {
        val fraction = progress.coerceIn(0f, 1f)
        return GlassOpticalParameters(depth * fraction, refraction * fraction, dispersion * fraction)
    }

    companion object {
        val Default = GlassOpticalParameters()
    }
}
