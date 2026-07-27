package top.mcxiafeng.badger.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MethodsTest {

    @Before
    fun setUp() {
        // [§14.2] Robolectric 同一 JVM 中其它测试可能已 startKoin,但 BadgerApplication.onCreate
        // 又会再次 startKoin 触发 KoinApplicationAlreadyStartedException。这里用
        // runCatching 守住,startKoin 已成功就跳过,其它情况下用 stop+start 重新拉起。
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