package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.pages.settings.account.AccountSettingsViewModel

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * AccountSettingsViewModel 测试。
 *
 * 覆盖 4 类核心契约：
 * 1. 构造期 snapshot：username / role / serverUrl / isLoggedIn 一次读到
 * 2. authState 切换：触发 refresh，state 跟着变（isLoggedIn 翻转）
 * 3. updateServerUrl：空白输入被忽略；合法输入 trim 后持久化并写回 state
 * 4. logout()：先把 isLoggingOut 翻 true → 调 repo.logout() → 翻回 false；
 *    飞行中再次调用 no-op（防抖）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountSettingsViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var userAuthRepository: UserAuthRepository
    private lateinit var serverApiFactory: ServerApiFactory
    private lateinit var serverUrlHolder: ServerUrlHolder
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedOut)

    // ServerUrlHolder 内嵌一个 AuthPrefs 间接,所以这里必须用真实 holder 实例。
    // 它会调 AuthPrefs.writeServerUrl——mockkObject 已 stub。
    private fun newHolder(): ServerUrlHolder = ServerUrlHolder(context)

    // AuthPrefs 静态方法的 stub 值
    private var stubUsername: String? = null
    private var stubServerUrl: String = "http://10.0.2.2:8080"
    // [Phase 2] 新契约 role 由 isAdmin 派生，不再有独立 role 字符串
    private var stubIsAdmin: Boolean = false

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        userAuthRepository = mockk(relaxed = true) {
            every { state } returns authStateFlow
        }
        serverApiFactory = mockk(relaxed = true)
        mockkObject(AuthPrefs)
        every { AuthPrefs.readUsername(any()) } answers { stubUsername }
        every { AuthPrefs.readIsAdmin(any()) } answers { stubIsAdmin }
        every { AuthPrefs.readServerUrl(any()) } answers { stubServerUrl }
        every { AuthPrefs.writeServerUrl(any(), any()) } answers {
            stubServerUrl = secondArg()
        }
        // [§14.2] Koin 模块:为 AccountSettingsViewModel 注入 mock 依赖。
        // Robolectric 单元测试不走 BadgerApplication.onCreate(),所以必须手工 startKoin。
        // GlobalContext 已经在其它测试中 startKoin 时,这里 stop + 重 start 保证干净上下文。
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { context }
                    single { userAuthRepository }
                    single { serverApiFactory }
                    single { ServerUrlHolder(context) }
                },
            )
        }
        serverUrlHolder = ServerUrlHolder(context)
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        unmockkAll()
    }

    private fun createViewModel(): AccountSettingsViewModel =
        AccountSettingsViewModel()

    // ========== snapshot 初始读取 ==========

    @Test
    fun `init reads snapshot from AuthPrefs and auth state`() = runTest {
        stubUsername = "alice"
        stubIsAdmin = true
        stubServerUrl = "https://badger.example.com"
        authStateFlow.value = AuthState.SignedIn

        val vm = createViewModel()
        // init {} 里 viewModelScope.launch 的 collect 需要 dispatcher 推进
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.username).isEqualTo("alice")
        // [Phase 2] role 由 isAdmin 派生
        assertThat(s.role).isEqualTo("管理员")
        assertThat(s.serverUrl).isEqualTo("https://badger.example.com")
        assertThat(s.isLoggedIn).isTrue()
        assertThat(s.isLoggingOut).isFalse()
    }

    @Test
    fun `init reflects SignedOut when no auth session`() {
        stubUsername = null
        stubIsAdmin = false
        stubServerUrl = "http://10.0.2.2:8080"
        authStateFlow.value = AuthState.SignedOut

        val vm = createViewModel()

        val s = vm.state.value
        assertThat(s.isLoggedIn).isFalse()
        assertThat(s.username).isNull()
        // [Phase 2] role 不再为 null —— 非管理员即「普通用户」
        assertThat(s.role).isEqualTo("普通用户")
    }

    // ========== authState 流转触发刷新 ==========

    @Test
    fun `state flips isLoggedIn when authState transitions SignedOut to SignedIn`() = runTest {
        stubUsername = "bob"
        authStateFlow.value = AuthState.SignedOut
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.isLoggedIn).isFalse()

        // 切换时 refresh 会重新读 prefs,顺带验证 username 跟读
        stubUsername = "bob2"
        authStateFlow.value = AuthState.SignedIn
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.isLoggedIn).isTrue()
        assertThat(s.username).isEqualTo("bob2")
    }

    // ========== updateServerUrl ==========

    @Test
    fun `updateServerUrl ignores blank input and does not touch prefs`() {
        val vm = createViewModel()
        val original = vm.state.value.serverUrl

        vm.updateServerUrl("   ")
        vm.updateServerUrl("")

        io.mockk.verify(exactly = 0) { AuthPrefs.writeServerUrl(any(), any()) }
        assertThat(vm.state.value.serverUrl).isEqualTo(original)
    }

    @Test
    fun `updateServerUrl trims trailing slash, persists, and updates state`() {
        val vm = createViewModel()

        vm.updateServerUrl("  https://badger.example.com/  ")

        // 持久化用 trim 后的值
        io.mockk.verify(exactly = 1) {
            AuthPrefs.writeServerUrl(any(), "https://badger.example.com")
        }
        // state 也同步
        assertThat(vm.state.value.serverUrl).isEqualTo("https://badger.example.com")
    }

    @Test
    fun `updateServerUrl pushes normalized url into ServerApiFactory`() {
        // [修复防御]: URL 写 prefs 只是落盘,真正让 ServerApi 实例切换地址的
        // 是 ServerApiFactory.updateBaseUrl。两边必须都被调到,否则行为退化到
        // 老 bug(保存完仍打旧地址)。
        val vm = createViewModel()

        vm.updateServerUrl("  https://badger.example.com/  ")

        io.mockk.verify(exactly = 1) {
            serverApiFactory.updateBaseUrl("https://badger.example.com")
        }
    }

    // ========== logout() ==========

    @Test
    fun `logout flips isLoggingOut then calls repository then resets flag`() = runTest {
        authStateFlow.value = AuthState.SignedIn
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.isLoggingOut).isFalse()

        vm.logout()
        // 同步段立刻把 isLoggingOut 翻 true
        assertThat(vm.state.value.isLoggingOut).isTrue()

        advanceUntilIdle()
        coVerify(exactly = 1) { userAuthRepository.logout() }
        // 协程跑完后翻回 false
        assertThat(vm.state.value.isLoggingOut).isFalse()
    }

    @Test
    fun `logout second call during flight is no-op`() = runTest {
        authStateFlow.value = AuthState.SignedIn
        val vm = createViewModel()
        advanceUntilIdle()

        vm.logout()
        vm.logout() // 第二次被防抖挡掉
        advanceUntilIdle()

        // 即便调用两次,repo 也只看到一次
        coVerify(exactly = 1) { userAuthRepository.logout() }
    }
}
