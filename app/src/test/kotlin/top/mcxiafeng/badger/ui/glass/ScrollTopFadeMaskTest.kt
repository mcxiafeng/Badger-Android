package top.mcxiafeng.badger.ui.glass

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synthetic pixels only: never captures a device, window, or application screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScrollTopFadeMaskTest {
    @Test
    fun actualShaderOpacityIsLinearAndLeavesPixelsBelowTheRegionOpaque() {
        val bitmap = fadedPixels(screenY = 0f)
        try {
            assertThat(Color.alpha(bitmap.getPixel(1, 0))).isAtMost(1)
            assertThat(Color.alpha(bitmap.getPixel(1, 200)) / 255f).isWithin(0.01f).of(0.5f)
            assertThat(Color.alpha(bitmap.getPixel(1, 399))).isAtLeast(254)
            assertThat(Color.alpha(bitmap.getPixel(1, 500))).isEqualTo(255)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun anInsetViewportUsesScreenOpacityWithoutRestartingAtItsOwnTop() {
        val bitmap = fadedPixels(screenY = 80f)
        try {
            assertThat(Color.alpha(bitmap.getPixel(1, 0)) / 255f).isWithin(0.01f).of(0.2f)
            assertThat(Color.alpha(bitmap.getPixel(1, 120)) / 255f).isWithin(0.01f).of(0.5f)
            assertThat(Color.alpha(bitmap.getPixel(1, 320))).isEqualTo(255)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun maskingAnIsolatedScrollLayerDoesNotEraseFixedSiblings() {
        val bitmap = Bitmap.createBitmap(4, 600, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLUE)
            val saved = canvas.saveLayer(0f, 0f, 4f, 600f, null)
            canvas.drawColor(Color.RED)
            canvas.drawRect(0f, 0f, 4f, 600f, ScrollTopFadeMask().paint(TopEffectRegion(400f), 0f))
            canvas.restoreToCount(saved)
            assertThat(Color.alpha(bitmap.getPixel(1, 0))).isEqualTo(255)
            assertThat(Color.blue(bitmap.getPixel(1, 0))).isAtLeast(254)
            assertThat(bitmap.getPixel(1, 500)).isEqualTo(Color.RED)
        } finally {
            bitmap.recycle()
        }
    }

    private fun fadedPixels(screenY: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(4, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.RED)
        canvas.drawRect(0f, 0f, 4f, 600f, ScrollTopFadeMask().paint(TopEffectRegion(400f), screenY))
        return bitmap
    }
}
