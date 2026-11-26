package top.mcxiafeng.badger.pages.social

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.utils.Methods

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class QrDisplayPaletteTest {
    @Test
    fun lightDefaultHasBlackModulesAndWhiteBackground() {
        val palette = qrDisplayPalette(dark = false, candidate = Color.Black)
        assertThat(palette.foreground).isEqualTo(Color.Black)
        assertThat(palette.background).isEqualTo(Color.White)
    }

    @Test
    fun everyColourIsOpaqueContrastingAndDecodableInBothThemes() {
        val content = "https://example.com/contact?id=123456789&name=test"
        listOf(false, true).forEach { dark ->
            Methods.qrColors.forEach { candidate ->
                val palette = qrDisplayPalette(dark, candidate)
                val foreground = palette.foreground.toArgb()
                val background = palette.background.toArgb()
                assertThat(palette.foreground.alpha).isEqualTo(1f)
                assertThat(ColorUtils.calculateContrast(foreground, background)).isAtLeast(4.5)
                val bitmap = Methods.generateQRCode(content, 512, foreground, background, quietZoneModules = 4)
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertThat(pixels.toSet()).containsExactly(foreground, background)
                    val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                    val luminance = if (dark) source.invert() else source
                    val decoded = try {
                        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(luminance))).text
                    } catch (error: NotFoundException) {
                        throw AssertionError("二维码解码失败：dark=$dark；candidate=$candidate；palette=$palette", error)
                    }
                    assertThat(decoded).isEqualTo(content)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }
}
