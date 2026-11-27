package top.mcxiafeng.badger.ui.glass

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.G2CornerCubic
import top.mcxiafeng.badger.ui.designsystem.G2CornerGeometry
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class G2CornerGeometryTest {
    @Test
    fun curveEndpointsMatchStraightEdgesWithZeroCurvatureAndNonzeroTangents() {
        val first = G2CornerGeometry.segments.first()
        val last = G2CornerGeometry.segments.last()
        assertThat(first.start).isEqualTo(Offset(1f, 0f))
        assertThat(last.end).isEqualTo(Offset(0f, 1f))
        assertThat(abs(curvature(first, 0f))).isWithin(0.000001).of(0.0)
        assertThat(abs(curvature(last, 1f))).isWithin(0.000001).of(0.0)
        assertThat(tangent(first, 0f).getDistance()).isGreaterThan(0f)
        assertThat(tangent(last, 1f).getDistance()).isGreaterThan(0f)
    }

    @Test
    fun twoCornerSegmentsSharePositionTangentAndCurvatureAtTheMiddle() {
        val first = G2CornerGeometry.segments.first()
        val last = G2CornerGeometry.segments.last()
        assertThat(first.end).isEqualTo(last.start)
        assertThat(tangent(first, 1f)).isEqualTo(tangent(last, 0f))
        assertThat(curvature(first, 1f)).isWithin(0.00001).of(curvature(last, 0f))
    }

    @Test
    fun ellipticalScalingAlsoKeepsTheJointCurvatureContinuous() {
        fun scaled(curve: G2CornerCubic): G2CornerCubic {
            fun point(value: Offset) = Offset(value.x * 17f, value.y * 29f)
            return G2CornerCubic(point(curve.start), point(curve.control1), point(curve.control2), point(curve.end))
        }
        val first = scaled(G2CornerGeometry.segments.first())
        val last = scaled(G2CornerGeometry.segments.last())
        assertThat(curvature(first, 0f)).isWithin(0.000001).of(0.0)
        assertThat(curvature(last, 1f)).isWithin(0.000001).of(0.0)
        assertThat(curvature(first, 1f)).isWithin(0.000001).of(curvature(last, 0f))
    }

    @Test
    fun theWholeCornerIsRegularConvexAndFinite() {
        G2CornerGeometry.segments.forEach { curve ->
            (0..100).forEach { step ->
                val progress = step / 100f
                val value = curvature(curve, progress)
                assertThat(value.isFinite()).isTrue()
                assertThat(value).isAtMost(0.000001)
                assertThat(tangent(curve, progress).getDistance()).isGreaterThan(0f)
            }
        }
    }

    @Test
    fun sharedShapesUseContinuousPathsWithoutMovingBoundsAtAnyDensityOrDirection() {
        val size = Size(400f, 80f)
        listOf(BadgerShapes.Surface, BadgerShapes.Capsule, BadgerShapes.rounded(8.dp),
            BadgerShapes.corners(0.dp, 0.dp, BadgerRadius.surface, BadgerRadius.surface)).forEach { shape ->
            listOf(1f, 2.625f, 3.5f).forEach { density ->
                LayoutDirection.entries.forEach { direction ->
                    val outline = shape.createOutline(size, direction, Density(density))
                    assertThat(outline).isInstanceOf(Outline.Generic::class.java)
                    assertThat((outline as Outline.Generic).path.getBounds()).isEqualTo(Rect(0f, 0f, 400f, 80f))
                }
            }
        }
    }

    @Test
    fun theSurfaceRadiusRepresents73DesignPixelsRatherThan73Dp() {
        assertThat(BadgerRadius.surface.value).isWithin(0.0001f).of(20.857143f)
        assertThat(BadgerRadius.surface.value * 3.5f).isWithin(0.0001f).of(73f)
    }


    @Test
    fun nativeColorTrackCircleCannotCutTheInsetContinuousCurve() {
        val nativeRadius = BadgerSize.colorSliderTrackHeight.value / 2f
        val inset = BadgerSize.colorSliderClipInset.value
        val radius = nativeRadius - inset
        var pieces = G2CornerGeometry.segments
        repeat(5) { pieces = pieces.flatMap(::subdivide) }
        // Each Bezier lies in its control polygon; a polygon inside the convex circle proves containment.
        pieces.flatMap { listOf(it.start, it.control1, it.control2, it.end) }.forEach { point ->
            val physical = Offset(inset + point.x * radius, inset + point.y * radius)
            assertThat((physical - Offset(nativeRadius, nativeRadius)).getDistance()).isLessThan(nativeRadius)
        }
    }

    private fun subdivide(curve: G2CornerCubic): List<G2CornerCubic> {
        val a = (curve.start + curve.control1) / 2f
        val b = (curve.control1 + curve.control2) / 2f
        val c = (curve.control2 + curve.end) / 2f
        val d = (a + b) / 2f
        val e = (b + c) / 2f
        val middle = (d + e) / 2f
        return listOf(G2CornerCubic(curve.start, a, d, middle), G2CornerCubic(middle, e, c, curve.end))
    }

    private fun tangent(curve: G2CornerCubic, t: Float): Offset {
        val inverse = 1f - t
        return (curve.control1 - curve.start) * (3f * inverse * inverse) +
            (curve.control2 - curve.control1) * (6f * inverse * t) +
            (curve.end - curve.control2) * (3f * t * t)
    }

    private fun curvature(curve: G2CornerCubic, t: Float): Double {
        val first = tangent(curve, t)
        val second = (curve.control2 - curve.control1 * 2f + curve.start) * (6f * (1f - t)) +
            (curve.end - curve.control2 * 2f + curve.control1) * (6f * t)
        val cross = first.x.toDouble() * second.y - first.y.toDouble() * second.x
        return cross / (first.x.toDouble().pow(2) + first.y.toDouble().pow(2)).pow(1.5)
    }
}
