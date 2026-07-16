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
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedOut)

    private var stubUsername: String? = null
    private var stubServerUrl: String = "http://10.0.2.2:8080"

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        userAuthRepository = mockk(relaxed = true) {
            every { state } returns authStateFlow
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
        SettingsHomeViewModel(context, userAuthRepository)

    // 用 backgroundScope 启动 collector,触发 Eagerly stateIn 的实际数据流推进。
    private fun kotlinx.coroutines.test.TestScope.activate(vm: SettingsHomeViewModel) {
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
    }

    // ========== 初始值 ==========

    @Test
    fun `initial state reflects SignedOut and default server url`() = runTest {
        stubUsername = null
        stubServerUrl = "http://10.0.2.2:8080"
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
    fun `server url is re-read from prefs after auth transition`() = runTest {
        stubUsername = "erin"
        stubServerUrl = "https://old.example.com"
        authStateFlow.value = AuthState.SignedIn
        val vm = createViewModel()
        activate(vm)
        assertThat(vm.state.value.serverUrl).isEqualTo("https://old.example.com")

        // 用户在「账号与备份」里切换了 server url;VM 应该反映它
        stubServerUrl = "https://new.example.com"
        authStateFlow.value = AuthState.SignedOut
        advanceUntilIdle()

        assertThat(vm.state.value.serverUrl).isEqualTo("https://new.example.com")
        assertThat(vm.state.value.isLoggedIn).isFalse()
    }
}
