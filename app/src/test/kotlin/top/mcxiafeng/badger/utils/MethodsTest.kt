package top.mcxiafeng.badger.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.platform.ImageCodec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MethodsTest {

    @Before
    fun setUp() {
        
        
        
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { org.robolectric.RuntimeEnvironment.getApplication() }
                },
            )
        }
    }

    @Test
    fun avatarSizeConstant_is256() {
        assertThat(ImageCodec.AVATAR_SIZE).isEqualTo(256)
    }

    @Test
    fun avatarQualityConstant_is60() {
        assertThat(ImageCodec.DEFAULT_WEBP_QUALITY).isEqualTo(90)
    }

    @Test
    fun qrColors_hasSixColors() {
        assertThat(Methods.qrColors).hasSize(6)
    }
}