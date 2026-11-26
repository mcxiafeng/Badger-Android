package top.mcxiafeng.badger.ui.glass

import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressiveTopBlurGeometryTest {
    @Test
    fun rendererClipCannotExtendIntoTheLowerViewportWhenItsLayerIsOversized() {
        val geometry = ProgressiveTopBlurGeometry(Size(1080f, 2400f), 0f, 52f, TopEffectRegion(439f))
        assertThat(geometry.effectTopLocalY).isWithin(0.001f).of(0f)
        assertThat(geometry.effectBottomLocalY).isEqualTo(439f)
        val shifted = ProgressiveTopBlurGeometry(Size(1080f, 2400f), -30f, 52f, TopEffectRegion(439f))
        assertThat(shifted.effectTopLocalY).isEqualTo(30f)
        assertThat(shifted.effectBottomLocalY).isEqualTo(469f)
        val below = ProgressiveTopBlurGeometry(Size(1080f, 2400f), 600f, 52f, TopEffectRegion(439f))
        assertThat(below.effectTopLocalY).isEqualTo(below.effectBottomLocalY)
    }

    @Test
    fun fadeAndGaussianRadiusUseTheSamePhysicalScreenRegion() {
        val region = TopEffectRegion(463f)
        listOf(-30f, 0f, 63f).forEach { origin ->
            val geometry = ProgressiveTopBlurGeometry(Size(1080f, 463f - origin), origin, 52f, region)
            assertThat(geometry.fadeStartLocalY + origin).isEqualTo(0f)
            assertThat(geometry.fadeEndLocalY + origin).isEqualTo(463f)
            assertThat(region.contentAlpha(0f)).isEqualTo(0f)
            assertThat(region.contentAlpha(463f)).isEqualTo(1f)
            assertThat(geometry.radiusAt(-origin)).isEqualTo(52f)
            assertThat(geometry.radiusAt(463f - origin)).isEqualTo(0f)
        }
    }

    @Test
    fun statusBarOrViewportOffsetDoesNotRestartEitherRamp() {
        val region = TopEffectRegion(400f)
        val fullScreen = ProgressiveTopBlurGeometry(Size(1080f, 400f), 0f, 52f, region)
        val insetViewport = ProgressiveTopBlurGeometry(Size(1080f, 320f), 80f, 52f, region)
        assertThat(fullScreen.radiusAt(200f)).isEqualTo(insetViewport.radiusAt(120f))
        assertThat(region.contentAlpha(80f)).isWithin(0.001f).of(0.2f)
        assertThat(region.localTop(80f)).isEqualTo(-80f)
        assertThat(region.localBottom(80f)).isEqualTo(320f)
    }

    @Test
    fun gaussianRadiusIncreasesTowardsTheTopAcrossTheWholeRegion() {
        val geometry = ProgressiveTopBlurGeometry(Size(1080f, 400f), 0f, 52f)
        val radii = (0..100).map { geometry.radiusAt(it * 4f) }
        radii.zipWithNext().forEach { (above, below) -> assertThat(above).isAtLeast(below) }
        assertThat(geometry.radiusAt(100f)).isLessThan(geometry.radiusAt(0f))
        assertThat(geometry.radiusAt(200f)).isWithin(0.001f).of(26f)
    }

    @Test
    fun gaussianCrossfadeWeightsCoverEachRowExactlyOnce() {
        (0..1000).forEach { sample ->
            val strength = progressiveTopBlurStrength(sample / 1000f)
            val alphas = (0 until GAUSSIAN_LEVEL_COUNT).map { gaussianLevelAlpha(it, strength) }
            assertThat(alphas.sum()).isEqualTo(255)
            assertThat(alphas.count { it > 0 }).isAtMost(2)
        }
        assertThat(gaussianLevelAlpha(0, 0f)).isEqualTo(255)
        assertThat(gaussianLevelAlpha(GAUSSIAN_LEVEL_COUNT - 1, 1f)).isEqualTo(255)
    }

    @Test
    fun samplingKeepsFullResolutionAndThreeSigmaMargins() {
        val geometry = ProgressiveTopBlurGeometry(Size(1080f, 463f), 0f, 52f)
        assertThat(geometry.sourceSize.width).isEqualTo(1392)
        assertThat(geometry.sourceSize.height).isEqualTo(775)
        assertThat(geometry.samplingMargin).isEqualTo(156f)
    }

    @Test
    fun bothProfilesClampBeyondTheSharedEdges() {
        val region = TopEffectRegion(400f)
        assertThat(region.contentAlpha(-20f)).isEqualTo(0f)
        assertThat(region.contentAlpha(420f)).isEqualTo(1f)
        assertThat(progressiveTopBlurStrength(-1f)).isEqualTo(1f)
        assertThat(progressiveTopBlurStrength(2f)).isEqualTo(0f)
    }
}
