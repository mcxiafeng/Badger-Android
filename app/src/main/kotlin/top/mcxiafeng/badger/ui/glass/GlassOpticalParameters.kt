package top.mcxiafeng.badger.ui.glass

import androidx.compose.runtime.Immutable

internal const val DEFAULT_GLASS_DEPTH = 79f
internal const val DEFAULT_GLASS_REFRACTION = 130f
internal const val DEFAULT_GLASS_DISPERSION = 50f

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
