package top.mcxiafeng.badger.ui.glass

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.pages.social.qrPanelBounds
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

class MenuSurfaceGeometryTest {
    private val provider = MenuPopupPositionProvider(shadowOutsetPx = SHADOW_OUTSET)
    private val window = IntRect(0, 0, 1080, 1920)
    private val margin = IntRect(0, 8, 0, 8)
    private val outerSize = IntSize(224, 324)

    @Test
    fun shadowGuardDoesNotConsumeTheEightDpGapBelowTheButton() {
        val anchor = IntRect(200, 100, 248, 148)
        val offset = provider.calculatePosition(anchor, window, LayoutDirection.Ltr, outerSize, margin,
            PopupPositionProvider.Align.TopEnd)
        assertThat(offset.y + SHADOW_OUTSET - anchor.bottom).isEqualTo(8)
        assertThat(offset.x + outerSize.width - SHADOW_OUTSET).isEqualTo(anchor.right)
    }

    @Test
    fun menuFlipsAboveTheButtonWithTheSameGapAndKeepsItsShadowOnscreen() {
        val anchor = IntRect(1000, 1800, 1048, 1848)
        val offset = provider.calculatePosition(anchor, window, LayoutDirection.Ltr, outerSize, margin,
            PopupPositionProvider.Align.End)
        assertThat(anchor.top - (offset.y + outerSize.height - SHADOW_OUTSET)).isEqualTo(8)
        assertThat(offset.x).isAtLeast(window.left)
        assertThat(offset.x + outerSize.width).isAtMost(window.right)
    }

    @Test
    fun endAlignmentMirrorsInRtlIncludingFormerContextMenuAliases() {
        val anchor = IntRect(200, 100, 248, 148)
        val offset = provider.calculatePosition(anchor, window, LayoutDirection.Rtl, outerSize, margin,
            PopupPositionProvider.Align.TopEnd)
        assertThat(offset.x + SHADOW_OUTSET).isEqualTo(anchor.left)
        assertThat(offset.y + SHADOW_OUTSET - anchor.bottom).isEqualTo(8)
    }

    @Test
    fun former139PixelRadiusIs123PixelsAtTheOriginalReferenceDensity() {
        val radius = with(Density(2.625f)) { BadgerRadius.menu.toPx() }
        assertThat(radius).isWithin(0.001f).of(123f)
        val bottom = qrPanelBounds(1080f, 1400f, inverted = false, radiusPx = radius)
        assertThat(bottom.topLeftCornerRadius).isEqualTo(CornerRadius(radius))
        assertThat(bottom.topRightCornerRadius).isEqualTo(CornerRadius(radius))
        assertThat(bottom.bottomLeftCornerRadius).isEqualTo(CornerRadius.Zero)
        assertThat(bottom.bottomRightCornerRadius).isEqualTo(CornerRadius.Zero)
        val top = qrPanelBounds(1080f, 1400f, inverted = true, radiusPx = radius)
        assertThat(top.topLeftCornerRadius).isEqualTo(CornerRadius.Zero)
        assertThat(top.topRightCornerRadius).isEqualTo(CornerRadius.Zero)
        assertThat(top.bottomLeftCornerRadius).isEqualTo(CornerRadius(radius))
        assertThat(top.bottomRightCornerRadius).isEqualTo(CornerRadius(radius))
    }

    private companion object { const val SHADOW_OUTSET = 12 }
}
