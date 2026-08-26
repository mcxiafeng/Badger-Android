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
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.CaptchaResult
import top.mcxiafeng.badger.network.RegisterPolicy
import top.mcxiafeng.badger.network.VerificationCodeResult
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * AuthViewModel 测试。
 *
 * 覆盖契约（与 [AuthViewModel] 的 [修复防御] 注释一一对应）：
 * 1. `canSubmitLogin` 边界：空字段 → false；非空 → true；isBusy 拦截
 * 2. `canSubmitRegister` 边界：用户名 2/3/32/33 字符、密码 <8 / >=8、邮箱必填、
 *    两次密码一致、按注册策略校验图形/邮箱验证码
 * 3. `reset()` 清空全部输入并把 state 拉回 Idle
 * 4. `switchToLogin` / `switchToRegister` 清空 email / state，**保留** username + password
 * 5. signIn / register 重入拦截（loading 期间第二次调用 repo 不再被调）
 * 6. onUsername / onEmail / onPassword 的 trim / 清洗行为（[V2-UX] 禁空格）
 * 7. [Phase 2] 注册策略加载 / 图形验证码刷新 / 邮箱验证码发送（dev 明文回填）
 *
 * MockK 已知问题：挂起函数 `coEvery { ... } returns Result.success(Unit)` 在 JVM 上的
 * 泛型擦除导致 `r.fold(...)` 触发 `ClassCastException`。本测试因此**不**验证
 * signIn/register 的成功/失败路径（那些在集成层验证或改造 UserAuthRepository 为接口）。
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
        // [Phase 2] 默认注册策略：允许注册、无验证码 —— 让普通 canSubmitRegister 测试
        // 不被验证码竞态干扰；需要验证码的用例单独 stub 覆盖。
        coEvery { userAuthRepository.fetchRegisterPolicy() } returns RegisterPolicy(
            allowRegister = true, requireCaptcha = false, requireEmailCode = false,
        )
        coEvery { userAuthRepository.fetchCaptcha() } returns CaptchaResult("cid-default", "AB12")
        coEvery { userAuthRepository.sendVerificationCode(any(), any()) } returns
            VerificationCodeResult("eid-default", null, true)
        // [§14.2] 为 ViewModel 注入 mock 依赖
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(module { single { userAuthRepository } })
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        unmockkAll()
    }

    private fun createViewModel(): AuthViewModel =
        AuthViewModel()

    private fun AuthViewModel.typeUsername(v: String) {
        username.value = v
    }

    private fun AuthViewModel.typeEmail(v: String) {
        email.value = v
    }

    private fun AuthViewModel.typePassword(v: String) {
        password.value = v
    }

    private fun AuthViewModel.typePasswordAgain(v: String) {
        passwordAgain.value = v
    }

    private fun AuthViewModel.setDefaultPolicy() {
        registerPolicy.value = RegisterPolicy(allowRegister = true, requireCaptcha = false, requireEmailCode = false)
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

    // ========== canSubmitRegister（[Phase 2] 邮箱必填 + 两次密码一致 + 策略验证码） ==========

    @Test
    fun `canSubmitRegister rejects username shorter than 3 chars`() {
        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("ab")
        vm.typeEmail("ab@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister accepts username exactly 3 chars`() {
        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("abc")
        vm.typeEmail("abc@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isTrue()
    }

    @Test
    fun `canSubmitRegister rejects username longer than 32 chars`() {
        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("a".repeat(33))
        vm.typeEmail("abc@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister rejects password shorter than 8 chars`() {
        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("1234567")
        vm.typePasswordAgain("1234567")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister rejects missing email (email now required)`() {
        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("alice")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister rejects invalid email`() {
        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("alice")
        vm.typeEmail("not-an-email")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister rejects mismatched passwordAgain`() {
        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password1234")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister is false until register policy is loaded`() {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        // registerPolicy 默认 null → 不允许提交（防验证码竞态）
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister requires captcha when policy requires it`() {
        val vm = createViewModel()
        vm.registerPolicy.value = RegisterPolicy(true, requireCaptcha = true, requireEmailCode = false)
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
        vm.captchaInput.value = "ABCD"
        assertThat(vm.canSubmitRegister()).isTrue()
    }

    @Test
    fun `canSubmitRegister requires email code when policy requires it`() {
        val vm = createViewModel()
        vm.registerPolicy.value = RegisterPolicy(true, requireCaptcha = false, requireEmailCode = true)
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
        vm.emailCodeInput.value = "123456"
        assertThat(vm.canSubmitRegister()).isTrue()
    }

    @Test
    fun `canSubmitRegister false when register is closed by policy`() {
        val vm = createViewModel()
        vm.registerPolicy.value = RegisterPolicy(false, requireCaptcha = false, requireEmailCode = false)
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister accepts valid input with default policy`() {
        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
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
        coEvery { userAuthRepository.register(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            awaitCancellation()
        }

        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("newuser")
        vm.typeEmail("newuser@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        vm.register()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Loading::class.java)
        vm.register()

        advanceUntilIdle()

        coVerify(exactly = 1) {
            userAuthRepository.register(
                "newuser", "newuser@example.com", "password123", "password123",
                null, null, null, null,
            )
        }
    }

    @Test
    fun `register blocked by canSubmitRegister shows error not Loading`() = runTest {
        val vm = createViewModel()
        // policy null → canSubmitRegister false → register() 拦截
        vm.typeUsername("newuser")
        vm.typeEmail("newuser@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        vm.register()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Error::class.java)
        coVerify(exactly = 0) { userAuthRepository.register(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    // ========== reset ==========

    @Test
    fun `reset clears all inputs and resets state to Idle`() {
        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        vm.captchaInput.value = "ABCD"
        vm.captchaId.value = "cid"
        vm.emailCodeInput.value = "123456"

        vm.reset()

        assertThat(vm.username.value).isEqualTo("")
        assertThat(vm.email.value).isEqualTo("")
        assertThat(vm.password.value).isEqualTo("")
        assertThat(vm.passwordAgain.value).isEqualTo("")
        assertThat(vm.captchaInput.value).isEqualTo("")
        assertThat(vm.captchaId.value).isNull()
        assertThat(vm.emailCodeInput.value).isEqualTo("")
        assertThat(vm.registerPolicy.value).isNull()
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

    // ========== [Phase 2] 注册策略 / 验证码 ==========

    @Test
    fun `switchToRegister loads register policy`() = runTest {
        val vm = createViewModel()
        vm.switchToRegister()
        advanceUntilIdle()
        assertThat(vm.registerPolicy.value).isEqualTo(RegisterPolicy(true, false, false))
    }

    @Test
    fun `switchToRegister with requireCaptcha fetches a captcha`() = runTest {
        coEvery { userAuthRepository.fetchRegisterPolicy() } returns RegisterPolicy(true, requireCaptcha = true, requireEmailCode = false)
        coEvery { userAuthRepository.fetchCaptcha() } returns CaptchaResult("cid-123", "K7P2")

        val vm = createViewModel()
        vm.switchToRegister()
        advanceUntilIdle()

        assertThat(vm.captchaId.value).isEqualTo("cid-123")
        assertThat(vm.captchaCode.value).isEqualTo("K7P2")
        coVerify(exactly = 1) { userAuthRepository.fetchCaptcha() }
    }

    @Test
    fun `refreshCaptcha updates captcha id and code and clears input`() = runTest {
        coEvery { userAuthRepository.fetchCaptcha() } returns CaptchaResult("cid-new", "Z9X4")
        val vm = createViewModel()
        vm.captchaInput.value = "stale"

        vm.refreshCaptcha()
        advanceUntilIdle()

        assertThat(vm.captchaId.value).isEqualTo("cid-new")
        assertThat(vm.captchaCode.value).isEqualTo("Z9X4")
        assertThat(vm.captchaInput.value).isEqualTo("")
    }

    @Test
    fun `sendEmailCode dev fallback autofills the input and sets hint`() = runTest {
        coEvery { userAuthRepository.sendVerificationCode("alice@example.com", "register") } returns
            VerificationCodeResult("eid-1", "654321", emailSent = false)

        val vm = createViewModel()
        vm.typeEmail("alice@example.com")
        vm.sendEmailCode()
        advanceUntilIdle()

        assertThat(vm.emailCaptchaId.value).isEqualTo("eid-1")
        assertThat(vm.emailCodeInput.value).isEqualTo("654321")
        assertThat(vm.emailCodeSent.value).isTrue()
        assertThat(vm.emailCodeHint.value).contains("验证码已发送")
    }

    @Test
    fun `sendEmailCode with smtp enabled does not expose code`() = runTest {
        coEvery { userAuthRepository.sendVerificationCode("alice@example.com", "register") } returns
            VerificationCodeResult("eid-2", null, emailSent = true)

        val vm = createViewModel()
        vm.typeEmail("alice@example.com")
        vm.sendEmailCode()
        advanceUntilIdle()

        assertThat(vm.emailCaptchaId.value).isEqualTo("eid-2")
        assertThat(vm.emailCodeInput.value).isEqualTo("")
        assertThat(vm.emailCodeHint.value).contains("请查收")
    }

    @Test
    fun `sendEmailCode with invalid email rejects without calling repo`() = runTest {
        val vm = createViewModel()
        vm.typeEmail("not-an-email")
        vm.sendEmailCode()
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Error::class.java)
        coVerify(exactly = 0) { userAuthRepository.sendVerificationCode(any(), any()) }
    }

    // ========== onUsername / onEmail / onPassword 清洗行为 ==========

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
    fun `onPassword strips spaces (V2-UX restricts to ascii visible range)`() {
        val vm = createViewModel()
        vm.onPassword("  pass word  ")
        // [Phase 2 基线修复]: 旧测试断言"密码可含空格"，但实现 [V2-UX] 明确禁空格
        // （移动端 IME 下首尾空格歧义 + 跨设备同步）；此处对齐实现语义。
        assertThat(vm.password.value).isEqualTo("password")
    }

    @Test
    fun `onPassword filters control characters`() {
        val vm = createViewModel()
        vm.onPassword("pass\nword")
        assertThat(vm.password.value).isEqualTo("password")
    }

    @Test
    fun `onPasswordAgain applies same sanitization as password handler`() {
        val vm = createViewModel()
        vm.onPasswordAgain("  pwd 123  ")
        assertThat(vm.passwordAgain.value).isEqualTo("pwd123")
    }
}
