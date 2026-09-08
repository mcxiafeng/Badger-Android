package top.mcxiafeng.badger.pages.auth

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
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
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.CaptchaResult
import top.mcxiafeng.badger.network.RegisterPolicy
import top.mcxiafeng.badger.network.VerificationCodeResult
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * AuthViewModel 测试。
 *
 * 覆盖契约（对应 [AuthViewModel] / [AuthValidator]）：
 * 1. `canSubmitLogin` / `canSubmitRegister` / `canSubmitForgotPassword` 边界
 * 2. 表单状态归组（credentials / registerState / forgotForm）与 reset 全清
 * 3. 页面生命周期：onAuthScreenEnter 保留凭据、onForgotScreenEnter 全新 forgot 表单
 * 4. signIn / register / resetPassword 的**成功与失败路径**（repo 契约改为抛异常后可测）
 *    + Loading 重入拦截
 * 5. on* 输入清洗（trim / 控制字符 / 非 ASCII 可见字符过滤，走真实输入路径）
 * 6. 注册策略加载 / 图形验证码刷新 / 邮箱验证码发送（dev 明文回填 / smtp 不回显）
 *
 * 说明：UserAuthRepository.login/register/forgotPassword 为抛异常契约（不返回 Result），
 * MockK 用 `throws` stub 失败、relaxed 默认成功 —— 无泛型擦除问题。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var userAuthRepository: UserAuthRepository
    private lateinit var serverUrlHolder: ServerUrlHolder
    private lateinit var serverApiFactory: ServerApiFactory

    @Before
    fun setUp() {
        userAuthRepository = mockk(relaxed = true)
        serverUrlHolder = mockk(relaxed = true)
        serverApiFactory = mockk(relaxed = true)
        // 默认注册策略：允许注册、无验证码 —— 让普通 canSubmitRegister 测试
        // 不被验证码竞态干扰；需要验证码的用例单独 stub 覆盖。
        coEvery { userAuthRepository.fetchRegisterPolicy() } returns RegisterPolicy(
            allowRegister = true, requireCaptcha = false, requireEmailCode = false,
        )
        coEvery { userAuthRepository.fetchCaptcha() } returns CaptchaResult("cid-default", "AB12")
        coEvery { userAuthRepository.sendVerificationCode(any(), any()) } returns
            VerificationCodeResult("eid-default", null, true)
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(module {
                single { userAuthRepository }
                single { serverUrlHolder }
                single { serverApiFactory }
            })
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        unmockkAll()
    }

    private fun createViewModel(): AuthViewModel = AuthViewModel()

    // ---- 真实输入路径 helper（经 on* 清洗，不再直接写状态） ----

    private fun AuthViewModel.typeUsername(v: String) = onUsername(v)
    private fun AuthViewModel.typeEmail(v: String) = onEmail(v)
    private fun AuthViewModel.typePassword(v: String) = onPassword(v)
    private fun AuthViewModel.typePasswordAgain(v: String) = onPasswordAgain(v)

    /** 切到注册并等待策略/验证码加载完成（等价旧版 setDefaultPolicy）。 */
    private fun TestScope.loadDefaultPolicy(vm: AuthViewModel) {
        vm.switchToRegister()
        advanceUntilIdle()
    }

    private fun AuthViewModel.fillValidRegisterForm() {
        typeUsername("alice")
        typeEmail("alice@example.com")
        typePassword("password123")
        typePasswordAgain("password123")
    }

    private fun AuthViewModel.fillValidForgotForm() {
        onForgotEmail("alice@example.com")
        onForgotCode("123456")
        onForgotNewPassword("newpass123")
        onForgotNewPasswordAgain("newpass123")
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
    fun `canSubmitLogin returns false while busy`() = runTest {
        coEvery { userAuthRepository.login(any(), any()) } coAnswers { awaitCancellation() }
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("password123")
        vm.signIn()
        advanceUntilIdle()
        assertThat(vm.isBusy).isTrue()
        assertThat(vm.canSubmitLogin()).isFalse()
    }

    // ========== canSubmitRegister（邮箱必填 + 两次密码一致 + 策略验证码） ==========

    @Test
    fun `canSubmitRegister rejects username shorter than 3 chars`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.typeUsername("ab")
        vm.typeEmail("ab@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister accepts username exactly 3 chars`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.typeUsername("abc")
        vm.typeEmail("abc@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isTrue()
    }

    @Test
    fun `canSubmitRegister rejects username longer than 32 chars`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.typeUsername("a".repeat(33))
        vm.typeEmail("abc@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister rejects password shorter than 8 chars`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("1234567")
        vm.typePasswordAgain("1234567")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister rejects missing email (email now required)`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.typeUsername("alice")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister rejects invalid email`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.typeUsername("alice")
        vm.typeEmail("not-an-email")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister rejects mismatched passwordAgain`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password1234")
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister is false until register policy is loaded`() {
        val vm = createViewModel()
        vm.fillValidRegisterForm()
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister requires captcha when policy requires it`() = runTest {
        coEvery { userAuthRepository.fetchRegisterPolicy() } returns
            RegisterPolicy(true, requireCaptcha = true, requireEmailCode = false)
        coEvery { userAuthRepository.fetchCaptcha() } returns CaptchaResult("cid-1", "K7P2")
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.fillValidRegisterForm()
        assertThat(vm.canSubmitRegister()).isFalse()
        vm.onCaptchaInput("K7P2")
        assertThat(vm.canSubmitRegister()).isTrue()
    }

    @Test
    fun `canSubmitRegister requires email code when policy requires it`() = runTest {
        coEvery { userAuthRepository.fetchRegisterPolicy() } returns
            RegisterPolicy(true, requireCaptcha = false, requireEmailCode = true)
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.fillValidRegisterForm()
        assertThat(vm.canSubmitRegister()).isFalse()
        vm.onEmailCodeInput("123456")
        assertThat(vm.canSubmitRegister()).isTrue()
    }

    @Test
    fun `canSubmitRegister false when register is closed by policy`() = runTest {
        coEvery { userAuthRepository.fetchRegisterPolicy() } returns
            RegisterPolicy(false, requireCaptcha = false, requireEmailCode = false)
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.fillValidRegisterForm()
        assertThat(vm.canSubmitRegister()).isFalse()
    }

    @Test
    fun `canSubmitRegister accepts valid input with default policy`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.fillValidRegisterForm()
        assertThat(vm.canSubmitRegister()).isTrue()
    }

    // ========== signIn（新契约：成功/失败路径可测） ==========

    @Test
    fun `signIn success transitions to SignedIn and clears password`() = runTest {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("password123")

        vm.signIn()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(AuthUiState.SignedIn)
        assertThat(vm.credentials.value.password).isEmpty()
        assertThat(vm.credentials.value.username).isEqualTo("alice")
        coVerify(exactly = 1) { userAuthRepository.login("alice", "password123") }
        coVerify(exactly = 1) { serverUrlHolder.markUrlVerified() }
    }

    @Test
    fun `signIn failure surfaces error message`() = runTest {
        coEvery { userAuthRepository.login(any(), any()) } throws RuntimeException("用户名或密码错误")
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("password123")

        vm.signIn()
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s).isInstanceOf(AuthUiState.Error::class.java)
        assertThat((s as AuthUiState.Error).message).isEqualTo("用户名或密码错误")
        coVerify(exactly = 0) { serverUrlHolder.markUrlVerified() }
    }

    @Test
    fun `signIn blocks when canSubmitLogin is false even after called`() = runTest {
        val vm = createViewModel()
        vm.signIn()
        val s = vm.state.value
        assertThat(s).isInstanceOf(AuthUiState.Error::class.java)
        coVerify(exactly = 0) { userAuthRepository.login(any(), any()) }
    }

    @Test
    fun `signIn second call during loading is no-op (reentry guard)`() = runTest {
        coEvery { userAuthRepository.login("alice", "password123") } coAnswers {
            awaitCancellation()
        }

        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("password123")

        vm.signIn()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Loading::class.java)
        vm.signIn()

        advanceUntilIdle()

        coVerify(exactly = 1) { userAuthRepository.login("alice", "password123") }
    }

    // ========== register ==========

    @Test
    fun `register success transitions to SignedIn`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.fillValidRegisterForm()

        vm.register()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(AuthUiState.SignedIn)
        coVerify(exactly = 1) {
            userAuthRepository.register(
                "alice", "alice@example.com", "password123", "password123",
                null, null, null, null,
            )
        }
        coVerify(exactly = 1) { serverUrlHolder.markUrlVerified() }
    }

    @Test
    fun `register failure surfaces error message`() = runTest {
        coEvery { userAuthRepository.register(any(), any(), any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("用户名已被占用")
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.fillValidRegisterForm()

        vm.register()
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s).isInstanceOf(AuthUiState.Error::class.java)
        assertThat((s as AuthUiState.Error).message).isEqualTo("用户名已被占用")
    }

    @Test
    fun `register passes captcha fields only when policy requires them`() = runTest {
        coEvery { userAuthRepository.fetchRegisterPolicy() } returns
            RegisterPolicy(true, requireCaptcha = true, requireEmailCode = false)
        coEvery { userAuthRepository.fetchCaptcha() } returns CaptchaResult("cid-9", "QQ77")
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.fillValidRegisterForm()
        vm.onCaptchaInput("QQ77")

        vm.register()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            userAuthRepository.register(
                "alice", "alice@example.com", "password123", "password123",
                "cid-9", "QQ77", null, null,
            )
        }
    }

    @Test
    fun `register blocked when email changed after sending code`() = runTest {
        coEvery { userAuthRepository.fetchRegisterPolicy() } returns
            RegisterPolicy(true, requireCaptcha = false, requireEmailCode = true)
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.fillValidRegisterForm()
        coEvery {
            userAuthRepository.sendVerificationCode("alice@example.com", "register")
        } returns VerificationCodeResult("eid-1", null, emailSent = true)
        vm.sendEmailCode()
        advanceUntilIdle()
        // 用户填入收到的验证码后再改邮箱 → register 不得用新邮箱配旧码
        vm.onEmailCodeInput("123456")
        vm.typeEmail("changed@example.com")

        vm.register()

        val s = vm.state.value
        assertThat(s).isInstanceOf(AuthUiState.Error::class.java)
        assertThat((s as AuthUiState.Error).message).contains("邮箱已更改")
        coVerify(exactly = 0) { userAuthRepository.register(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `register second call during loading is no-op (reentry guard)`() = runTest {
        coEvery { userAuthRepository.register(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            awaitCancellation()
        }

        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.fillValidRegisterForm()
        vm.register()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Loading::class.java)
        vm.register()

        advanceUntilIdle()

        coVerify(exactly = 1) {
            userAuthRepository.register(
                "alice", "alice@example.com", "password123", "password123",
                null, null, null, null,
            )
        }
    }

    @Test
    fun `register blocked by canSubmitRegister shows error not Loading`() = runTest {
        val vm = createViewModel()
        vm.fillValidRegisterForm()
        vm.register()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Error::class.java)
        coVerify(exactly = 0) { userAuthRepository.register(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    // ========== reset / 页面生命周期 ==========

    @Test
    fun `reset clears all inputs and resets state to Idle`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        vm.fillValidRegisterForm()
        vm.onCaptchaInput("ABCD")
        vm.onEmailCodeInput("123456")
        vm.fillValidForgotForm()

        vm.reset()

        assertThat(vm.credentials.value).isEqualTo(AuthCredentials())
        assertThat(vm.registerState.value).isEqualTo(RegisterUiState())
        assertThat(vm.forgotForm.value).isEqualTo(ForgotUiState())
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
    }

    @Test
    fun `onAuthScreenEnter keeps credentials and resets state to Idle Login`() = runTest {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("password123")
        vm.typeEmail("alice@example.com")

        vm.onAuthScreenEnter()

        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
        assertThat(vm.credentials.value.username).isEqualTo("alice")
        assertThat(vm.registerState.value.email).isEqualTo("alice@example.com")
    }

    @Test
    fun `onAuthScreenEnter clears stale SignedIn state`() = runTest {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("password123")
        vm.signIn()
        advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(AuthUiState.SignedIn)

        vm.onAuthScreenEnter()

        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
        assertThat(vm.isBusy).isFalse()
    }

    @Test
    fun `onForgotScreenEnter gives a fresh forgot form`() {
        val vm = createViewModel()
        vm.fillValidForgotForm()

        vm.onForgotScreenEnter()

        assertThat(vm.forgotForm.value).isEqualTo(ForgotUiState())
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
    }

    // ========== switchToLogin / switchToRegister ==========

    @Test
    fun `switchToLogin keeps credentials and register form`() = runTest {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typeEmail("alice@example.com")
        vm.typePassword("password123")

        vm.switchToLogin()

        assertThat(vm.credentials.value.username).isEqualTo("alice")
        assertThat(vm.credentials.value.password).isEqualTo("password123")
        assertThat(vm.registerState.value.email).isEqualTo("alice@example.com")
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
    }

    @Test
    fun `switchToRegister keeps credentials and loads policy`() = runTest {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("password123")

        loadDefaultPolicy(vm)

        assertThat(vm.credentials.value.username).isEqualTo("alice")
        assertThat(vm.credentials.value.password).isEqualTo("password123")
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Register)
        assertThat(vm.registerState.value.policy).isEqualTo(RegisterPolicy(true, false, false))
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

    // ========== AuthMode ==========

    @Test
    fun `default authMode is Login`() {
        val vm = createViewModel()
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
    }

    @Test
    fun `switchToRegister sets authMode to Register`() {
        val vm = createViewModel()
        vm.switchToRegister()
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Register)
    }

    @Test
    fun `switchToLogin sets authMode to Login`() {
        val vm = createViewModel()
        vm.switchToRegister()
        vm.switchToLogin()
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
    }

    @Test
    fun `reset sets authMode back to Login`() {
        val vm = createViewModel()
        vm.switchToRegister()
        vm.reset()
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
    }

    // ========== 注册策略 / 验证码 ==========

    @Test
    fun `switchToRegister loads register policy`() = runTest {
        val vm = createViewModel()
        loadDefaultPolicy(vm)
        assertThat(vm.registerState.value.policy).isEqualTo(RegisterPolicy(true, false, false))
    }

    @Test
    fun `policy load failure falls back to permissive policy with error hint`() = runTest {
        coEvery { userAuthRepository.fetchRegisterPolicy() } throws RuntimeException("网络异常")
        val vm = createViewModel()
        loadDefaultPolicy(vm)

        val r = vm.registerState.value
        assertThat(r.policy).isEqualTo(RegisterPolicy(true, false, false))
        assertThat(r.policyError).contains("网络异常")
        assertThat(r.policyLoading).isFalse()
    }

    @Test
    fun `switchToRegister with requireCaptcha fetches a captcha`() = runTest {
        coEvery { userAuthRepository.fetchRegisterPolicy() } returns
            RegisterPolicy(true, requireCaptcha = true, requireEmailCode = false)
        coEvery { userAuthRepository.fetchCaptcha() } returns CaptchaResult("cid-123", "K7P2")

        val vm = createViewModel()
        loadDefaultPolicy(vm)

        assertThat(vm.registerState.value.captchaId).isEqualTo("cid-123")
        assertThat(vm.registerState.value.captchaCode).isEqualTo("K7P2")
        coVerify(exactly = 1) { userAuthRepository.fetchCaptcha() }
    }

    @Test
    fun `refreshCaptcha updates captcha id and code and clears input`() = runTest {
        coEvery { userAuthRepository.fetchCaptcha() } returns CaptchaResult("cid-new", "Z9X4")
        val vm = createViewModel()
        vm.onCaptchaInput("stale")

        vm.refreshCaptcha()
        advanceUntilIdle()

        assertThat(vm.registerState.value.captchaId).isEqualTo("cid-new")
        assertThat(vm.registerState.value.captchaCode).isEqualTo("Z9X4")
        assertThat(vm.registerState.value.captchaInput).isEmpty()
    }

    @Test
    fun `sendEmailCode dev fallback autofills the input and sets hint`() = runTest {
        coEvery { userAuthRepository.sendVerificationCode("alice@example.com", "register") } returns
            VerificationCodeResult("eid-1", "654321", emailSent = false)

        val vm = createViewModel()
        vm.typeEmail("alice@example.com")
        vm.sendEmailCode()
        advanceUntilIdle()

        val r = vm.registerState.value
        assertThat(r.emailCodeCaptchaId).isEqualTo("eid-1")
        assertThat(r.emailCodeInput).isEqualTo("654321")
        assertThat(r.emailCodeSent).isTrue()
        assertThat(r.emailCodeHint).contains("验证码已发送")
    }

    @Test
    fun `sendEmailCode with smtp enabled does not expose code`() = runTest {
        coEvery { userAuthRepository.sendVerificationCode("alice@example.com", "register") } returns
            VerificationCodeResult("eid-2", null, emailSent = true)

        val vm = createViewModel()
        vm.typeEmail("alice@example.com")
        vm.sendEmailCode()
        advanceUntilIdle()

        val r = vm.registerState.value
        assertThat(r.emailCodeCaptchaId).isEqualTo("eid-2")
        assertThat(r.emailCodeInput).isEmpty()
        assertThat(r.emailCodeHint).contains("请查收")
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

    // ========== 输入清洗（走 on* 真实路径） ==========

    @Test
    fun `onUsername trims whitespace and filters non-ascii`() {
        val vm = createViewModel()
        vm.onUsername("  alice  ")
        assertThat(vm.credentials.value.username).isEqualTo("alice")
        vm.onUsername("alice中文")
        assertThat(vm.credentials.value.username).isEqualTo("alice")
    }

    @Test
    fun `onEmail trims whitespace`() {
        val vm = createViewModel()
        vm.onEmail("  alice@example.com  ")
        assertThat(vm.registerState.value.email).isEqualTo("alice@example.com")
    }

    @Test
    fun `onPassword strips spaces (V2-UX restricts to ascii visible range)`() {
        val vm = createViewModel()
        vm.onPassword("  pass word  ")
        assertThat(vm.credentials.value.password).isEqualTo("password")
    }

    @Test
    fun `onPassword filters control characters`() {
        val vm = createViewModel()
        vm.onPassword("pass\nword")
        assertThat(vm.credentials.value.password).isEqualTo("password")
    }

    @Test
    fun `onPasswordAgain applies same sanitization as password handler`() {
        val vm = createViewModel()
        vm.onPasswordAgain("  pwd 123  ")
        assertThat(vm.registerState.value.passwordAgain).isEqualTo("pwd123")
    }

    @Test
    fun `onCaptchaInput strips whitespace and control chars`() {
        val vm = createViewModel()
        vm.onCaptchaInput(" AB\n12 ")
        assertThat(vm.registerState.value.captchaInput).isEqualTo("AB12")
    }

    // ========== canSubmitForgotPassword ==========

    @Test
    fun `canSubmitForgotPassword returns false when email is invalid`() {
        val vm = createViewModel()
        vm.onForgotEmail("not-an-email")
        vm.onForgotCode("123456")
        vm.onForgotNewPassword("password123")
        vm.onForgotNewPasswordAgain("password123")
        assertThat(vm.canSubmitForgotPassword()).isFalse()
    }

    @Test
    fun `canSubmitForgotPassword returns false when code is blank`() {
        val vm = createViewModel()
        vm.onForgotEmail("alice@example.com")
        vm.onForgotNewPassword("password123")
        vm.onForgotNewPasswordAgain("password123")
        assertThat(vm.canSubmitForgotPassword()).isFalse()
    }

    @Test
    fun `canSubmitForgotPassword returns false when password shorter than 8`() {
        val vm = createViewModel()
        vm.onForgotEmail("alice@example.com")
        vm.onForgotCode("123456")
        vm.onForgotNewPassword("1234567")
        vm.onForgotNewPasswordAgain("1234567")
        assertThat(vm.canSubmitForgotPassword()).isFalse()
    }

    @Test
    fun `canSubmitForgotPassword returns false when passwords mismatch`() {
        val vm = createViewModel()
        vm.onForgotEmail("alice@example.com")
        vm.onForgotCode("123456")
        vm.onForgotNewPassword("password123")
        vm.onForgotNewPasswordAgain("different456")
        assertThat(vm.canSubmitForgotPassword()).isFalse()
    }

    @Test
    fun `canSubmitForgotPassword returns true with valid input`() {
        val vm = createViewModel()
        vm.fillValidForgotForm()
        assertThat(vm.canSubmitForgotPassword()).isTrue()
    }

    @Test
    fun `canSubmitForgotPassword returns false when busy`() = runTest {
        coEvery { userAuthRepository.forgotPassword(any(), any(), any(), any(), any()) } coAnswers {
            awaitCancellation()
        }
        val vm = createViewModel()
        vm.fillValidForgotForm()

        vm.resetPassword()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Loading::class.java)
        assertThat(vm.canSubmitForgotPassword()).isFalse()
    }

    // ========== sendForgotCode ==========

    @Test
    fun `sendForgotCode dev fallback autofills code and sets hint`() = runTest {
        coEvery { userAuthRepository.sendVerificationCode("alice@example.com", "forgotPassword") } returns
            VerificationCodeResult("fid-1", "987654", emailSent = false)

        val vm = createViewModel()
        vm.onForgotEmail("alice@example.com")
        vm.sendForgotCode()
        advanceUntilIdle()

        val f = vm.forgotForm.value
        assertThat(f.codeCaptchaId).isEqualTo("fid-1")
        assertThat(f.code).isEqualTo("987654")
        assertThat(f.codeSent).isTrue()
        assertThat(f.codeHint).contains("验证码已发送")
    }

    @Test
    fun `sendForgotCode with smtp does not expose code`() = runTest {
        coEvery { userAuthRepository.sendVerificationCode("alice@example.com", "forgotPassword") } returns
            VerificationCodeResult("fid-2", null, emailSent = true)

        val vm = createViewModel()
        vm.onForgotEmail("alice@example.com")
        vm.sendForgotCode()
        advanceUntilIdle()

        val f = vm.forgotForm.value
        assertThat(f.codeCaptchaId).isEqualTo("fid-2")
        assertThat(f.code).isEmpty()
        assertThat(f.codeHint).contains("请查收")
    }

    @Test
    fun `sendForgotCode with invalid email rejects without calling repo`() = runTest {
        val vm = createViewModel()
        vm.onForgotEmail("not-an-email")
        vm.sendForgotCode()
        advanceUntilIdle()

        assertThat(vm.state.value).isInstanceOf(AuthUiState.Error::class.java)
        coVerify(exactly = 0) { userAuthRepository.sendVerificationCode(any(), any()) }
    }

    @Test
    fun `sendForgotCode reentry guard prevents double call`() = runTest {
        coEvery { userAuthRepository.sendVerificationCode("alice@example.com", "forgotPassword") } coAnswers {
            awaitCancellation()
        }

        val vm = createViewModel()
        vm.onForgotEmail("alice@example.com")
        vm.sendForgotCode()
        vm.sendForgotCode()

        advanceUntilIdle()

        coVerify(exactly = 1) { userAuthRepository.sendVerificationCode("alice@example.com", "forgotPassword") }
    }

    // ========== resetPassword ==========

    @Test
    fun `resetPassword blocked by validator shows error`() = runTest {
        val vm = createViewModel()
        vm.onForgotEmail("invalid")
        vm.onForgotCode("123456")
        vm.onForgotNewPassword("password123")
        vm.onForgotNewPasswordAgain("password123")
        vm.resetPassword()

        assertThat(vm.state.value).isInstanceOf(AuthUiState.Error::class.java)
        coVerify(exactly = 0) { userAuthRepository.forgotPassword(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `resetPassword success emits ResetDone`() = runTest {
        val vm = createViewModel()
        vm.fillValidForgotForm()

        vm.resetPassword()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(AuthUiState.ResetDone)
        coVerify(exactly = 1) {
            userAuthRepository.forgotPassword("alice@example.com", any(), "123456", "newpass123", "newpass123")
        }
    }

    @Test
    fun `resetPassword failure shows error state`() = runTest {
        coEvery {
            userAuthRepository.forgotPassword(any(), any(), any(), any(), any())
        } throws Exception("验证码已过期")

        val vm = createViewModel()
        vm.fillValidForgotForm()

        vm.resetPassword()
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s).isInstanceOf(AuthUiState.Error::class.java)
        assertThat((s as AuthUiState.Error).message).contains("验证码已过期")
    }

    @Test
    fun `resetPassword reentry guard prevents double call`() = runTest {
        coEvery {
            userAuthRepository.forgotPassword(any(), any(), any(), any(), any())
        } coAnswers { awaitCancellation() }

        val vm = createViewModel()
        vm.fillValidForgotForm()

        vm.resetPassword()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Loading::class.java)
        vm.resetPassword()

        advanceUntilIdle()

        coVerify(exactly = 1) { userAuthRepository.forgotPassword(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `returning to auth screen after reset clears ResetDone and keeps login usable`() = runTest {
        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.fillValidForgotForm()
        vm.resetPassword()
        advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(AuthUiState.ResetDone)

        // 忘记密码二级页 pop 回认证主页 → onAuthScreenEnter
        vm.onAuthScreenEnter()

        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
        assertThat(vm.credentials.value.username).isEqualTo("alice")
        assertThat(vm.canSubmitLogin()).isFalse() // 密码为空，需重输
    }

    // ========== applyServerUrl ==========

    @Test
    fun `applyServerUrl normalizes and broadcasts to holder and factory`() {
        val vm = createViewModel()
        vm.applyServerUrl("http://192.168.1.5:8080/")
        coVerify(exactly = 1) { serverUrlHolder.set("http://192.168.1.5:8080") }
        coVerify(exactly = 1) { serverApiFactory.updateBaseUrl("http://192.168.1.5:8080") }
    }

    @Test
    fun `applyServerUrl ignores blank input`() {
        val vm = createViewModel()
        vm.applyServerUrl("   ")
        coVerify(exactly = 0) { serverUrlHolder.set(any()) }
        coVerify(exactly = 0) { serverApiFactory.updateBaseUrl(any()) }
    }
}
