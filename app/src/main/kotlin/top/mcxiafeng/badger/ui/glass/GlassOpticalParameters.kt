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

/**
 * Compact controls use the thin profile: a tighter edge band and a sharper rim, and small
 * rim-heavy surfaces may keep the fuller dispersion.
 */
internal const val ICON_GLASS_DEPTH = 30f
internal const val ICON_GLASS_DISPERSION = 16f

/**
 * Long horizontal capsules use the soft profile: lower mid-band force and much lower dispersion —
 * full dispersion across a wide bar reads as rainbow striping over grid or photographic content.
 */
internal const val BAR_GLASS_REFRACTION = 40f
internal const val BAR_GLASS_DISPERSION = 6f

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

        /** Thin lens for compact pills, icon buttons and FABs. */
        val Icon = GlassOpticalParameters(depth = ICON_GLASS_DEPTH, dispersion = ICON_GLASS_DISPERSION)

        /** Soft lens for long capsules: the bottom bar and grouped toolbar pills. */
        val Bar = GlassOpticalParameters(refraction = BAR_GLASS_REFRACTION, dispersion = BAR_GLASS_DISPERSION)
    }
}
