package top.mcxiafeng.badger.ui.glass

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Paint synthetic pixels; never capture a device or a real Compose UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ItemPressHighlightTest {
    @Test
    fun theWholeRoundedItemBrightensUniformlyInsteadOfOnlyItsTextArea() {
        val bitmap = paintedItem(1f)
        try {
            val middle = bitmap.getPixel(50, 30)
            assertThat(bitmap.getPixel(8, 30)).isEqualTo(middle)
            assertThat(bitmap.getPixel(92, 30)).isEqualTo(middle)
            assertThat(bitmap.getPixel(50, 8)).isEqualTo(middle)
            assertThat(bitmap.getPixel(50, 52)).isEqualTo(middle)
            assertThat(AndroidColor.red(middle)).isGreaterThan(BASE_CHANNEL)
            assertThat(bitmap.getPixel(0, 0)).isEqualTo(BASE_COLOR)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun releasedItemHasNoResidualHighlight() {
        val bitmap = paintedItem(0f)
        try {
            assertThat(bitmap.getPixel(8, 30)).isEqualTo(BASE_COLOR)
            assertThat(bitmap.getPixel(50, 30)).isEqualTo(BASE_COLOR)
            assertThat(bitmap.getPixel(92, 30)).isEqualTo(BASE_COLOR)
        } finally {
            bitmap.recycle()
        }
    }

    private fun paintedItem(strength: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(100f, 60f)) {
            drawRect(Color(BASE_COLOR))
            drawItemPressHighlight(radiusPx = 12f, strength = strength)
        }
        return bitmap
    }

    private companion object {
        const val BASE_CHANNEL = 32
        val BASE_COLOR = AndroidColor.rgb(BASE_CHANNEL, BASE_CHANNEL, BASE_CHANNEL)
    }
}
