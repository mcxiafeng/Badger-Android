package top.mcxiafeng.badger.ui.glass

import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressiveTopBlurGeometryTest {
    @Test
    fun screenTopIsStrongestAndFadeEndsAfterStatusInsetAndActionRow() {
        val geometry = ProgressiveTopBlurGeometry(Size(1080f, 24f + 52f), screenTop = 0f, radius = 32f)

        assertThat(geometry.fadeStartLocalY).isWithin(0.001f).of(0f)
        assertThat(geometry.fadeEndScreenY).isEqualTo(76f)
        assertThat(geometry.strengthAt(0f)).isEqualTo(1f)
        assertThat(geometry.strengthAt(38f)).isEqualTo(0.5f)
        assertThat(geometry.strengthAt(76f)).isEqualTo(0f)
        assertThat(geometry.strengthAt(90f)).isEqualTo(0f)
    }

    @Test
    fun translatedContainerKeepsMaximumAtScreenZeroRatherThanRestartingAtItsTop() {
        val geometry = ProgressiveTopBlurGeometry(Size(1080f, 76f), screenTop = 24f, radius = 32f)

        assertThat(geometry.fadeStartLocalY).isEqualTo(-24f)
        assertThat(geometry.fadeEndScreenY).isEqualTo(100f)
        assertThat(geometry.strengthAt(-24f)).isEqualTo(1f)
        assertThat(geometry.strengthAt(0f)).isLessThan(1f)
        assertThat(geometry.strengthAt(26f)).isEqualTo(0.5f)
        assertThat(geometry.strengthAt(76f)).isEqualTo(0f)
    }

    @Test
    fun partiallyOffscreenContainerStillFadesAtItsMeasuredBottom() {
        val geometry = ProgressiveTopBlurGeometry(Size(1080f, 76f), screenTop = -24f, radius = 32f)

        assertThat(geometry.fadeStartLocalY).isEqualTo(24f)
        assertThat(geometry.fadeEndScreenY).isEqualTo(52f)
        assertThat(geometry.strengthAt(24f)).isEqualTo(1f)
        assertThat(geometry.strengthAt(50f)).isEqualTo(0.5f)
        assertThat(geometry.strengthAt(76f)).isEqualTo(0f)
    }

    @Test
    fun shaderCoordinatesUndoMarginAndSamplingAtBothProfileBoundaries() {
        listOf(1f, 1.5f, 2.625f, 3f).forEach { density ->
            val geometry = ProgressiveTopBlurGeometry(Size(393f * density, 76f * density),
                screenTop = 7.5f * density, radius = 32f * density)
            val sampleScale = 0.25f
            val screenStart = geometry.shaderTop / sampleScale - geometry.margin + geometry.screenTop
            val screenEnd = (geometry.shaderTop + geometry.shaderHeight) / sampleScale - geometry.margin + geometry.screenTop

            assertThat(screenStart).isWithin(0.001f).of(0f)
            assertThat(screenEnd).isWithin(0.001f).of(geometry.fadeEndScreenY)
            assertThat(geometry.shaderRadius / sampleScale).isEqualTo(32f * density)
        }
    }

    @Test
    fun samplingBoundsIncludeThreeSigmaOnEverySideEvenWithFractionalPixels() {
        val geometry = ProgressiveTopBlurGeometry(Size(1031.625f, 199.5f), screenTop = 0f, radius = 84f)
        val sampleScale = 0.25f
        val requiredMargin = 3f * geometry.radius

        assertThat(geometry.margin).isAtLeast(requiredMargin)
        assertThat(geometry.sourceSize.width / sampleScale).isAtLeast(geometry.size.width + 2f * requiredMargin)
        assertThat(geometry.sourceSize.height / sampleScale).isAtLeast(geometry.size.height + 2f * requiredMargin)
        assertThat(geometry.shaderTop).isAtLeast(3f * geometry.shaderRadius)
    }

    @Test
    fun doublingBlurRadiusDoesNotMoveTheVisibleFadeBoundaries() {
        val previous = ProgressiveTopBlurGeometry(Size(1080f, 76f), screenTop = 0f, radius = 16f)
        val stronger = previous.copy(radius = 32f)

        assertThat(stronger.fadeStartLocalY).isEqualTo(previous.fadeStartLocalY)
        assertThat(stronger.fadeEndLocalY).isEqualTo(previous.fadeEndLocalY)
        assertThat(stronger.shaderRadius).isEqualTo(previous.shaderRadius * 2f)
        assertThat(stronger.margin).isEqualTo(previous.margin * 2f)
        assertThat(stronger.strengthAt(38f)).isEqualTo(previous.strengthAt(38f))
    }

    @Test
    fun profileIsMonotoneClampedAndApproachesBothEdgesWithZeroSlope() {
        val strengths = (0..100).map { progressiveTopBlurStrength(it / 100f) }
        strengths.zipWithNext().forEach { (before, after) -> assertThat(after).isAtMost(before) }
        assertThat(progressiveTopBlurStrength(-1f)).isEqualTo(1f)
        assertThat(progressiveTopBlurStrength(2f)).isEqualTo(0f)
        assertThat(1f - progressiveTopBlurStrength(0.001f)).isLessThan(0.000004f)
        assertThat(progressiveTopBlurStrength(0.999f)).isLessThan(0.000004f)
    }
}
