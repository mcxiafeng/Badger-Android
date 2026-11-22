package top.mcxiafeng.badger.utils

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BusinessImageSizingTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun panoramicAvatarBoundsTheLongestEdge() {
        val context = RuntimeEnvironment.getApplication()
        val source = Bitmap.createBitmap(4096, 256, Bitmap.Config.ARGB_8888)
        try {
            val file = Methods.saveBitmapAsAvatar(context, source, "panorama.webp")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            assertThat(maxOf(bounds.outWidth, bounds.outHeight)).isAtMost(Methods.AVATAR_SIZE)
            assertThat(bounds.outWidth.toFloat() / bounds.outHeight).isEqualTo(16f)
            assertThat(source.isRecycled).isFalse()
        } finally { source.recycle() }
    }
}
