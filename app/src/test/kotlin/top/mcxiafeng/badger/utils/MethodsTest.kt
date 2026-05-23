package top.mcxiafeng.badger.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MethodsTest {

    @Test
    fun avatarSizeConstant_is256() {
        assertThat(Methods.AVATAR_SIZE).isEqualTo(256)
    }

    @Test
    fun avatarQualityConstant_is60() {
        assertThat(Methods.AVATAR_QUALITY).isEqualTo(60)
    }

    @Test
    fun qrColors_hasSixColors() {
        assertThat(Methods.qrColors).hasSize(6)
    }
}
