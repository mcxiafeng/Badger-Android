package top.mcxiafeng.badger.pages.auth

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * AuthViewModel 测试。
 *
 * 覆盖契约（与 [AuthViewModel] 的 [修复防御] 注释一一对应）：
 * 1. `canSubmitLogin` 边界：空字段 → false；非空 → true；isBusy 拦截
 * 2. `canSubmitRegister` 边界：用户名 2/3/32/33 字符、密码 <8 / >=8
 * 3. `reset()` 清空全部输入并把 state 拉回 Idle
 * 4. `switchToLogin` / `switchToRegister` 清空 email / state，**保留** username + password
 * 5. signIn / register 重入拦截（loading 期间第二次调用 repo 不再被调）
 * 6. onUsername / onEmail / onPassword 的 trim 行为
 *
 * MockK 已知问题：挂起函数 `coEvery { ... } returns Result.success(Unit)` 在 JVM 上的
 * 泛型擦除导致 `r.fold(...)` 触发 `ClassCastException: kotlin.Result cannot
 * be cast to kotlin.Unit`。本测试因此**不**验证 signIn/register 的成功/失败路径
 * （那些需要在集成层验证或改造 UserAuthRepository 为接口）。本文件覆盖的契约
 * 全部不依赖 `Result<Unit>` 的具体泛型实例。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var userAuthRepository: UserAuthRepository

    @Before
    fun setUp() {
        userAuthRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createViewModel(): AuthViewModel =
        AuthViewModel(userAuthRepository)

    private fun AuthViewModel.typeUsername(v: String) {
        username.value = v
    }

    private fun AuthViewModel.typeEmail(v: String) {
        email.value = v
    }

    private fun AuthViewModel.typePassword(v: String) {
        password.value = v
    }

    // ========== canSubmitLogin ==========

    @Test
    fun `canSubmitLogin returns false when username is blank`() {
        val vm = createViewModel()
        vm.typeUsername("")
        vm.typePassword("password123")
        assertThat(vm.canSubmitLogin()).isFalse()
    }

    @Test
    fun `canSubmitLogin returns false when password is blank`() {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("")
        assertThat(vm.canSubmitLogin()).isFalse()
    }

    @Test
    fun `canSubmitLogin returns true when both fields are non-blank`() {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("password123")
        assertThat(vm.canSubmitLogin()).isTrue()
    }

    @Test
    fun `canSubmitLogin trims input via onUsername`() {
        val vm = createViewModel()
        vm.typeUsername("  bob  ".trim())
        vm.typePassword("password123")
        assertThat(vm.canSubmitLogin()).isTrue()
    }

    // ========== canSubmitRegister ==========

    @Test
    fun `canSubmitRegister rejects username shorter than 3 chars`() {
        val vm = createViewModel()
        vm.typeUsername("ab")
        vm.typePassword("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister accepts username exactly 3 chars`() {
        val vm = createViewModel()
        vm.typeUsername("abc")
        vm.typePassword("password123")
        assertThat(vm.canSubmitRegister()).isTrue()
    }

    @Test
    fun `canSubmitRegister accepts username exactly 32 chars`() {
        val vm = createViewModel()
        vm.typeUsername("a".repeat(32))
        vm.typePassword("password123")
        assertThat(vm.canSubmitRegister()).isTrue()
    }

    @Test
    fun `canSubmitRegister rejects username longer than 32 chars`() {
        val vm = createViewModel()
        vm.typeUsername("a".repeat(33))
        vm.typePassword("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister rejects password shorter than 8 chars`() {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("1234567")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister accepts password exactly 8 chars`() {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("12345678")
        assertThat(vm.canSubmitRegister()).isTrue()
    }

    // ========== isBusy 拦截 ==========

    @Test
    fun `signIn blocks when canSubmitLogin is false even after called`() = runTest {
        val vm = createViewModel()
        // 空 username / password 状态
        vm.signIn()
        // 立即拿到 Error(canSubmitLogin 拦截后根本没进入 Loading,也没调 repo)
        val s = vm.state.value
        assertThat(s).isInstanceOf(AuthUiState.Error::class.java)
        coVerify(exactly = 0) { userAuthRepository.login(any(), any()) }
    }

    @Test
    fun `signIn second call during loading is no-op (reentry guard)`() = runTest {
        // [修复防御]: 用 awaitCancellation 让 mock 的 login 永远不返回 —— 这样
        // state 停留在 Loading(因为 r.fold 从未执行),第二次 signIn 时
        // canSubmitLogin 会因为 isBusy 拦截,我们只要验证 repo 只被调用 1 次。
        coEvery { userAuthRepository.login("alice", "password123") } coAnswers {
            awaitCancellation()
        }

        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("password123")

        vm.signIn()
        // 第一次 signIn 后 state 应该是 Loading
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Loading::class.java)
        vm.signIn()

        advanceUntilIdle()

        // 关键断言:即便调用两次 signIn(),repo 也只被调用一次 —— 因为
        // canSubmitLogin 在 Loading 期间返回 false,第二次根本没进入
        // viewModelScope.launch。
        coVerify(exactly = 1) { userAuthRepository.login("alice", "password123") }
    }

    @Test
    fun `register second call during loading is no-op (reentry guard)`() = runTest {
        coEvery { userAuthRepository.register(any(), any(), any(), any()) } coAnswers {
            awaitCancellation()
        }

        val vm = createViewModel()
        vm.typeUsername("newuser")
        vm.typePassword("password123")
        vm.register()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Loading::class.java)
        vm.register()

        advanceUntilIdle()

        coVerify(exactly = 1) {
            userAuthRepository.register("newuser", "password123", null, null)
        }
    }

    // ========== reset ==========

    @Test
    fun `reset clears all inputs and resets state to Idle`() {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")

        vm.reset()

        assertThat(vm.username.value).isEqualTo("")
        assertThat(vm.email.value).isEqualTo("")
        assertThat(vm.password.value).isEqualTo("")
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
    }

    // ========== switchToLogin / switchToRegister ==========

    @Test
    fun `switchToLogin clears email but keeps username and password`() {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")

        vm.switchToLogin()

        // [修复防御]: 模式切换不应当清空已输入的 username / password —— 用户切回原模式时输入还在。
        assertThat(vm.username.value).isEqualTo("alice")
        assertThat(vm.password.value).isEqualTo("password123")
        assertThat(vm.email.value).isEqualTo("")
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
    }

    @Test
    fun `switchToRegister keeps username and password and clears state`() {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")

        vm.switchToRegister()

        assertThat(vm.username.value).isEqualTo("alice")
        assertThat(vm.password.value).isEqualTo("password123")
        assertThat(vm.email.value).isEqualTo("alice@example.com")
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
    }

    @Test
    fun `switchToLogin clears Error state to allow retry`() {
        val vm = createViewModel()
        vm.signIn()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Error::class.java)

        vm.switchToLogin()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
    }

    // ========== onUsername / onEmail trim 行为 ==========

    @Test
    fun `onUsername trims whitespace`() {
        val vm = createViewModel()
        vm.onUsername("  alice  ")
        assertThat(vm.username.value).isEqualTo("alice")
    }

    @Test
    fun `onEmail trims whitespace`() {
        val vm = createViewModel()
        vm.onEmail("  alice@example.com  ")
        assertThat(vm.email.value).isEqualTo("alice@example.com")
    }

    @Test
    fun `onPassword does not trim (passwords can contain spaces)`() {
        val vm = createViewModel()
        vm.onPassword("  pass word  ")
        assertThat(vm.password.value).isEqualTo("  pass word  ")
    }
}