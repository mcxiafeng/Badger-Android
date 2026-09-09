package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.UserSettings
import top.mcxiafeng.badger.testutil.MainDispatcherRule
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UserSettingsViewModel 回归测试。
 *
 * 锁定核心契约：服务端调用必须经注入的 [CoroutineDispatcher] 重定向（离开 Main 线程）。
 *
 * 背景：OkHttp 传输为同步阻塞实现，曾在 viewModelScope（Main）里直调
 * `serverApiFactory.get().getUserSettings()`，Android 上必抛
 * NetworkOnMainThreadException（其 message 为 null → UI 显示"加载失败"）。
 * iOS 侧 KtorApiTransport 内部 `runBlocking { withContext(IO) }` 自带切线程，
 * 故该缺陷仅 Android 暴露，B0–B7 的 iOS 编译 + 单测基线未能发现。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UserSettingsViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var userAuthRepository: UserAuthRepository
    private lateinit var serverApiFactory: ServerApiFactory
    private lateinit var serverApi: ServerApi

    /** 记录型 dispatcher：仅当被 withContext 真正调度时置真（直调 Main 不会触达）。 */
    private val dispatcherEngaged = AtomicBoolean(false)
    private val recordingDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatcherEngaged.set(true)
            block.run() // 测试内联执行即可，只需证明调度发生过
        }
    }

    @Before
    fun setUp() {
        serverApi = mockk(relaxed = true) {
            every { getUserSettings() } answers {
                UserSettings(language = "zh", theme = "dark", notifyEmail = true)
            }
        }
        serverApiFactory = mockk(relaxed = true) {
            every { get() } returns serverApi
        }
        userAuthRepository = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(AuthState.SignedOut)
        }
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { serverApiFactory }
                    single { userAuthRepository }
                },
            )
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        unmockkAll()
    }

    private fun createViewModel(): UserSettingsViewModel =
        UserSettingsViewModel(dispatcher = recordingDispatcher)

    @Test
    fun `load routes getUserSettings through injected dispatcher off Main`() = runTest {
        val vm = createViewModel()

        vm.load()
        advanceUntilIdle()

        // 核心断言：网络调用确实经 dispatcher 重定向（未裸跑在 Main 上）。
        assertThat(dispatcherEngaged.get()).isTrue()
        verify(exactly = 1) { serverApi.getUserSettings() }
        val state = vm.state.value
        assertThat(state).isInstanceOf(UserSettingsUiState.Success::class.java)
        val success = state as UserSettingsUiState.Success
        assertThat(success.isLoggedIn).isTrue()
        assertThat(success.settings.language).isEqualTo("zh")
    }
}
