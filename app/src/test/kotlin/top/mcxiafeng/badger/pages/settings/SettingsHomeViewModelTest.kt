package top.mcxiafeng.badger.pages.settings

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * SettingsHomeViewModel 测试。
 *
 * 覆盖 3 类契约：
 * 1. 初始值：在 repo.state 还未推送任何值前，state 应该用 initialValue
 *    显示一份"快照"（避免主页开屏闪 null）。
 * 2. authState 流转：登录/登出后 username / isLoggedIn / serverUrl 重读。
 * 3. serverUrl 流转：stateIn 的 map 每次都会重新读 prefs（覆盖 server URL
 *    在 logout 之后被还原的场景）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsHomeViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var userAuthRepository: UserAuthRepository
    private lateinit var serverUrlHolder: ServerUrlHolder
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedOut)
    // 真实 holder —— 它会读 stubServerUrl 当初始值,然后通过 StateFlow 推给 VM
    private val serverUrlFlow = MutableStateFlow("http://10.0.2.2:8080")

    private var stubUsername: String? = null
    private var stubServerUrl: String = "http://10.0.2.2:8080"

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        userAuthRepository = mockk(relaxed = true) {
            every { state } returns authStateFlow
        }
        serverUrlHolder = mockk(relaxed = true) {
            every { url } returns serverUrlFlow
        }
        mockkObject(AuthPrefs)
        every { AuthPrefs.readUsername(any()) } answers { stubUsername }
        every { AuthPrefs.readServerUrl(any()) } answers { stubServerUrl }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createViewModel(): SettingsHomeViewModel =
        SettingsHomeViewModel(context, userAuthRepository, serverUrlHolder)

    // ========== helper: 用 backgroundScope 启动 collector 让 Eagerly stateIn 推进 ==========
    private fun kotlinx.coroutines.test.TestScope.activate(vm: SettingsHomeViewModel) {
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
    }

    // ========== 初始值 ==========

    @Test
    fun `initial state reflects SignedOut and default server url`() = runTest {
        stubUsername = null
        stubServerUrl = "http://10.0.2.2:8080"
        serverUrlFlow.value = "http://10.0.2.2:8080"
        authStateFlow.value = AuthState.SignedOut

        val vm = createViewModel()

        // 还没 collector,initialValue 已经在 state.value 里
        val s = vm.state.value
        assertThat(s.username).isNull()
        assertThat(s.isLoggedIn).isFalse()
        assertThat(s.serverUrl).isEqualTo("http://10.0.2.2:8080")
    }

    @Test
    fun `initial state reflects already-SignedIn session`() = runTest {
        stubUsername = "carol"
        stubServerUrl = "https://badger.example.com"
        serverUrlFlow.value = "https://badger.example.com"
        authStateFlow.value = AuthState.SignedIn

        val vm = createViewModel()

        val s = vm.state.value
        assertThat(s.username).isEqualTo("carol")
        assertThat(s.isLoggedIn).isTrue()
        assertThat(s.serverUrl).isEqualTo("https://badger.example.com")
    }

    // ========== authState 流转 ==========

    @Test
    fun `state flips isLoggedIn when authState transitions`() = runTest {
        stubUsername = "dave"
        authStateFlow.value = AuthState.SignedOut
        val vm = createViewModel()
        activate(vm)

        assertThat(vm.state.value.isLoggedIn).isFalse()

        stubUsername = "dave-prime"
        authStateFlow.value = AuthState.SignedIn
        // 再推一轮,让 Eagerly stateIn 把新的 map 结果落到 state.value
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.isLoggedIn).isTrue()
        assertThat(s.username).isEqualTo("dave-prime")
    }

    @Test
    fun `server url flips when ServerUrlHolder broadcasts`() = runTest {
        // [修复防御]: 这是本次新加的核心契约 —— 改了 server url 之后,
        // 订阅了 [ServerUrlHolder] 的 VM 应该立即刷新,不用退出页面再进。
        // 之前的实现用 map { AuthPrefs.readServerUrl(...) },只有 authState 流转
        // 才会重读 prefs,所以「改了地址 UI 不变」。
        stubServerUrl = "https://old.example.com"
        serverUrlFlow.value = "https://old.example.com"
        authStateFlow.value = AuthState.SignedIn
        val vm = createViewModel()
        activate(vm)
        assertThat(vm.state.value.serverUrl).isEqualTo("https://old.example.com")

        // 模拟 AccountSettingsViewModel.updateServerUrl() 写完 prefs 后
        // 通知了 holder。VM 应该立即把 state 翻过去。
        serverUrlFlow.value = "https://new.example.com"
        advanceUntilIdle()

        assertThat(vm.state.value.serverUrl).isEqualTo("https://new.example.com")
        // authState 没变,其他字段不动
        assertThat(vm.state.value.isLoggedIn).isTrue()
    }
}
