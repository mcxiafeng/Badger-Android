package top.mcxiafeng.badger.pages.scanner

import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScannerWorkBitmapCopyTest {

    @Test
    fun `copy returns independent bitmap and does not recycle source`() {
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)

        val copy = createWorkBitmapCopy(source)

        assertThat(copy).isNotNull()
        assertThat(copy).isNotSameInstanceAs(source)
        assertThat(source.isRecycled).isFalse()
        assertThat(copy!!.width).isEqualTo(8)
        assertThat(copy.height).isEqualTo(8)
        assertThat(copy.getPixel(0, 0)).isEqualTo(Color.RED)

        copy.recycle()
        assertThat(source.isRecycled).isFalse()
        source.recycle()
    }

    @Test
    fun `copy of recycled source returns null`() {
        val source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        source.recycle()

        assertThat(createWorkBitmapCopy(source)).isNull()
    }
}
