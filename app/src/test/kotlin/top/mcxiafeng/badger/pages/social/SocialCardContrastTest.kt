package top.mcxiafeng.badger.pages.social

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic images only; no device screenshot or user photo is read. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SocialCardContrastTest {
    @Test
    fun brightPhotoUsesOpaqueBlackAndDarkPhotoUsesWhite() {
        withBackground(color = AndroidColor.WHITE) { assertThat(sample(it)).isEqualTo(Color.Black) }
        withBackground(color = AndroidColor.BLACK) { assertThat(sample(it)).isEqualTo(Color.White) }
    }

    @Test
    fun anOpaquePhotoKeepsItsTextColorWhenTheUnderlyingThemeFillChanges() {
        listOf(AndroidColor.WHITE, AndroidColor.BLACK).forEach { photo ->
            withBackground(color = photo) { bitmap ->
                assertThat(sample(bitmap, Color.Black)).isEqualTo(sample(bitmap, Color.White))
            }
        }
    }

    @Test
    fun centerCropIgnoresBrightPartsOutsideTheDisplayedPhoto() {
        withBackground(width = 80, height = 240, color = AndroidColor.WHITE) { bitmap ->
            // A 320x160 viewport displays only rows 100..140 of this portrait photo.
            bitmap.setPixels(IntArray(80 * 40) { AndroidColor.BLACK }, 0, 80, 0, 100, 80, 40)
            assertThat(sample(bitmap)).isEqualTo(Color.White)
        }
    }

    @Test
    fun contrastIncludesTheSameDarkScrimThatIsDrawnOverThePhoto() {
        withBackground(color = AndroidColor.rgb(144, 144, 144)) {
            // This grey favours black before the 25% scrim, but white after it.
            assertThat(sample(it)).isEqualTo(Color.White)
        }
    }

    @Test
    fun noPhotoUsesTheActualSolidFillInsteadOfAThemeForegroundRole() {
        assertThat(sample(null, Color.Black)).isEqualTo(Color.White)
        assertThat(sample(null, Color.White)).isEqualTo(Color.Black)
    }

    @Test
    fun unavailablePhotoUsesOneFixedFallbackIndependentOfThemeFill() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        assertThat(sample(bitmap, Color.Black)).isEqualTo(Color.White)
        assertThat(sample(bitmap, Color.White)).isEqualTo(Color.White)
    }

    private fun sample(bitmap: Bitmap?, background: Color = Color.Blue): Color =
        businessCardTextColor(bitmap, background, IntSize(320, 160), textHeightPx = 60, insetPx = 24f)

    private fun withBackground(width: Int = 80, height: Int = 80, color: Int, verify: (Bitmap) -> Unit) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            verify(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
