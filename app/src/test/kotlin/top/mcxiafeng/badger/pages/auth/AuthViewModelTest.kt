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
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.CaptchaResult
import top.mcxiafeng.badger.network.RegisterPolicy
import top.mcxiafeng.badger.network.VerificationCodeResult
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * AuthViewModel 测试。
 *
 * 覆盖登录/注册/忘记密码的输入校验、状态机、防重入、验证码流程以及异常恢复。
 * ViewModel 直接接收 mock 依赖，不依赖全局 Koin 容器。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var userAuthRepository: UserAuthRepository
    private lateinit var serverUrlHolder: ServerUrlHolder

    @Before
    fun setUp() {
        userAuthRepository = mockk(relaxed = true)
        serverUrlHolder = mockk(relaxed = true)
        coEvery { userAuthRepository.fetchRegisterPolicy() } returns RegisterPolicy(
            allowRegister = true,
            requireCaptcha = false,
            requireEmailCode = false,
        )
        coEvery { userAuthRepository.fetchCaptcha() } returns CaptchaResult("cid-default", "AB12")
        coEvery { userAuthRepository.sendVerificationCode(any(), any()) } returns
            VerificationCodeResult("eid-default", null, true)
        coEvery { userAuthRepository.forgotPassword(any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createViewModel(): AuthViewModel =
        AuthViewModel(userAuthRepository, serverUrlHolder)

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
        vm.onUsername("  bob  ")
        vm.typePassword("password123")
        assertThat(vm.canSubmitLogin()).isTrue()
    }

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

    @Test
    fun `signIn blocks when canSubmitLogin is false`() = runTest {
        val vm = createViewModel()
        vm.signIn()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Error::class.java)
        coVerify(exactly = 0) { userAuthRepository.login(any(), any()) }
    }

    @Test
    fun `signIn second call during loading is no-op`() = runTest {
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

    @Test
    fun `signIn exception clears loading state`() = runTest {
        coEvery { userAuthRepository.login("alice", "password123") } throws Exception("网络失败")

        val vm = createViewModel()
        vm.typeUsername("alice")
        vm.typePassword("password123")
        vm.signIn()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state).isInstanceOf(AuthUiState.Error::class.java)
        assertThat((state as AuthUiState.Error).message).contains("网络失败")
    }

    @Test
    fun `register second call during loading is no-op`() = runTest {
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
    fun `register exception clears loading state`() = runTest {
        coEvery { userAuthRepository.register(any(), any(), any(), any(), any(), any(), any(), any()) } throws Exception("注册服务不可用")

        val vm = createViewModel()
        vm.setDefaultPolicy()
        vm.typeUsername("newuser")
        vm.typeEmail("newuser@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        vm.register()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state).isInstanceOf(AuthUiState.Error::class.java)
        assertThat((state as AuthUiState.Error).message).contains("注册服务不可用")
    }

    @Test
    fun `register blocked by canSubmitRegister shows error not Loading`() = runTest {
        val vm = createViewModel()
        vm.typeUsername("newuser")
        vm.typeEmail("newuser@example.com")
        vm.typePassword("password123")
        vm.typePasswordAgain("password123")
        vm.register()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Error::class.java)
        coVerify(exactly = 0) { userAuthRepository.register(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

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
        advanceUntilIdle()

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
    fun `onPassword strips spaces`() {
        val vm = createViewModel()
        vm.onPassword("  pass word  ")
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

    @Test
    fun `default authMode is Login`() {
        val vm = createViewModel()
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
    }

    @Test
    fun `switchToRegister sets authMode to Register`() = runTest {
        val vm = createViewModel()
        vm.switchToRegister()
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Register)
    }

    @Test
    fun `switchToLogin sets authMode to Login`() = runTest {
        val vm = createViewModel()
        vm.switchToRegister()
        vm.switchToLogin()
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
    }

    @Test
    fun `switchToForgotPassword sets authMode to ForgotPassword`() {
        val vm = createViewModel()
        vm.switchToForgotPassword()
        assertThat(vm.authMode.value).isEqualTo(AuthMode.ForgotPassword)
    }

    @Test
    fun `reset sets authMode back to Login`() {
        val vm = createViewModel()
        vm.switchToForgotPassword()
        vm.reset()
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
    }

    @Test
    fun `canSubmitForgotPassword returns false when email is invalid`() {
        val vm = createViewModel()
        vm.forgotEmail.value = "not-an-email"
        vm.forgotCode.value = "123456"
        vm.forgotNewPassword.value = "password123"
        vm.forgotNewPasswordAgain.value = "password123"
        assertThat(vm.canSubmitForgotPassword()).isFalse()
    }

    @Test
    fun `canSubmitForgotPassword returns false when code is blank`() {
        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.forgotCode.value = ""
        vm.forgotNewPassword.value = "password123"
        vm.forgotNewPasswordAgain.value = "password123"
        assertThat(vm.canSubmitForgotPassword()).isFalse()
    }

    @Test
    fun `canSubmitForgotPassword returns false when password shorter than 8`() {
        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.forgotCode.value = "123456"
        vm.forgotNewPassword.value = "1234567"
        vm.forgotNewPasswordAgain.value = "1234567"
        assertThat(vm.canSubmitForgotPassword()).isFalse()
    }

    @Test
    fun `canSubmitForgotPassword returns false when passwords mismatch`() {
        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.forgotCode.value = "123456"
        vm.forgotNewPassword.value = "password123"
        vm.forgotNewPasswordAgain.value = "different456"
        assertThat(vm.canSubmitForgotPassword()).isFalse()
    }

    @Test
    fun `canSubmitForgotPassword returns true with valid input`() {
        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.forgotCode.value = "123456"
        vm.forgotNewPassword.value = "password123"
        vm.forgotNewPasswordAgain.value = "password123"
        assertThat(vm.canSubmitForgotPassword()).isTrue()
    }

    @Test
    fun `canSubmitForgotPassword returns false when busy`() = runTest {
        coEvery { userAuthRepository.forgotPassword(any(), any(), any(), any(), any()) } coAnswers {
            awaitCancellation()
        }
        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.forgotCode.value = "123456"
        vm.forgotNewPassword.value = "password123"
        vm.forgotNewPasswordAgain.value = "password123"

        vm.resetPassword()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Loading::class.java)
        assertThat(vm.canSubmitForgotPassword()).isFalse()
    }

    @Test
    fun `sendForgotCode dev fallback autofills code and sets hint`() = runTest {
        coEvery { userAuthRepository.sendVerificationCode("alice@example.com", "forgotPassword") } returns
            VerificationCodeResult("fid-1", "987654", emailSent = false)

        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.sendForgotCode()
        advanceUntilIdle()

        assertThat(vm.forgotCaptchaId.value).isEqualTo("fid-1")
        assertThat(vm.forgotCode.value).isEqualTo("987654")
        assertThat(vm.forgotCodeSent.value).isTrue()
        assertThat(vm.forgotCodeHint.value).contains("验证码已发送")
    }

    @Test
    fun `sendForgotCode with smtp does not expose code`() = runTest {
        coEvery { userAuthRepository.sendVerificationCode("alice@example.com", "forgotPassword") } returns
            VerificationCodeResult("fid-2", null, emailSent = true)

        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.sendForgotCode()
        advanceUntilIdle()

        assertThat(vm.forgotCaptchaId.value).isEqualTo("fid-2")
        assertThat(vm.forgotCode.value).isEqualTo("")
        assertThat(vm.forgotCodeHint.value).contains("请查收")
    }

    @Test
    fun `sendForgotCode with invalid email rejects without calling repo`() = runTest {
        val vm = createViewModel()
        vm.forgotEmail.value = "not-an-email"
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
        vm.forgotEmail.value = "alice@example.com"
        vm.sendForgotCode()
        vm.sendForgotCode()
        advanceUntilIdle()

        coVerify(exactly = 1) { userAuthRepository.sendVerificationCode("alice@example.com", "forgotPassword") }
    }

    @Test
    fun `resetPassword blocked by canSubmitForgotPassword shows error`() = runTest {
        val vm = createViewModel()
        vm.forgotEmail.value = "invalid"
        vm.forgotCode.value = "123456"
        vm.forgotNewPassword.value = "password123"
        vm.forgotNewPasswordAgain.value = "password123"
        vm.resetPassword()

        assertThat(vm.state.value).isInstanceOf(AuthUiState.Error::class.java)
        coVerify(exactly = 0) { userAuthRepository.forgotPassword(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `resetPassword success switches to login and prefills email`() = runTest {
        coEvery {
            userAuthRepository.forgotPassword("alice@example.com", "fid-1", "123456", "newpass123", "newpass123")
        } returns Unit

        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.forgotCode.value = "123456"
        vm.forgotCaptchaId.value = "fid-1"
        vm.forgotNewPassword.value = "newpass123"
        vm.forgotNewPasswordAgain.value = "newpass123"

        vm.resetPassword()
        advanceUntilIdle()

        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
        assertThat(vm.email.value).isEqualTo("alice@example.com")
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
    }

    @Test
    fun `resetPassword failure shows error state`() = runTest {
        coEvery { userAuthRepository.forgotPassword(any(), any(), any(), any(), any()) } throws Exception("验证码已过期")

        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.forgotCode.value = "123456"
        vm.forgotCaptchaId.value = "fid-1"
        vm.forgotNewPassword.value = "newpass123"
        vm.forgotNewPasswordAgain.value = "newpass123"

        vm.resetPassword()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state).isInstanceOf(AuthUiState.Error::class.java)
        assertThat((state as AuthUiState.Error).message).contains("验证码已过期")
    }

    @Test
    fun `resetPassword reentry guard prevents double call`() = runTest {
        coEvery { userAuthRepository.forgotPassword(any(), any(), any(), any(), any()) } coAnswers { awaitCancellation() }

        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.forgotCode.value = "123456"
        vm.forgotCaptchaId.value = "fid-1"
        vm.forgotNewPassword.value = "newpass123"
        vm.forgotNewPasswordAgain.value = "newpass123"

        vm.resetPassword()
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Loading::class.java)
        vm.resetPassword()
        advanceUntilIdle()

        coVerify(exactly = 1) { userAuthRepository.forgotPassword(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `switchToForgotPassword clears forgot form but preserves login email`() {
        val vm = createViewModel()
        vm.email.value = "alice@example.com"
        vm.switchToForgotPassword()

        assertThat(vm.authMode.value).isEqualTo(AuthMode.ForgotPassword)
        assertThat(vm.state.value).isInstanceOf(AuthUiState.Idle::class.java)
        assertThat(vm.forgotCode.value).isEqualTo("")
        assertThat(vm.forgotNewPassword.value).isEqualTo("")
        assertThat(vm.forgotNewPasswordAgain.value).isEqualTo("")
        assertThat(vm.forgotCaptchaId.value).isNull()
        assertThat(vm.forgotCodeSent.value).isFalse()
    }

    @Test
    fun `reset clears all forgot password fields`() {
        val vm = createViewModel()
        vm.forgotEmail.value = "alice@example.com"
        vm.forgotCode.value = "123456"
        vm.forgotNewPassword.value = "password123"
        vm.forgotNewPasswordAgain.value = "password123"
        vm.forgotCaptchaId.value = "fid-1"
        vm.forgotCodeSent.value = true
        vm.forgotCodeHint.value = "已发送"

        vm.reset()

        assertThat(vm.forgotEmail.value).isEqualTo("")
        assertThat(vm.forgotCode.value).isEqualTo("")
        assertThat(vm.forgotNewPassword.value).isEqualTo("")
        assertThat(vm.forgotNewPasswordAgain.value).isEqualTo("")
        assertThat(vm.forgotCaptchaId.value).isNull()
        assertThat(vm.forgotCodeSent.value).isFalse()
        assertThat(vm.forgotCodeHint.value).isNull()
        assertThat(vm.authMode.value).isEqualTo(AuthMode.Login)
    }

    @Test
    fun `onForgotEmail trims whitespace`() {
        val vm = createViewModel()
        vm.onForgotEmail("  alice@example.com  ")
        assertThat(vm.forgotEmail.value).isEqualTo("alice@example.com")
    }

    @Test
    fun `onForgotCode trims whitespace`() {
        val vm = createViewModel()
        vm.onForgotCode("  123456  ")
        assertThat(vm.forgotCode.value).isEqualTo("123456")
    }

    @Test
    fun `onForgotNewPassword strips spaces`() {
        val vm = createViewModel()
        vm.onForgotNewPassword("  pass word  ")
        assertThat(vm.forgotNewPassword.value).isEqualTo("password")
    }

    @Test
    fun `onForgotNewPasswordAgain strips control chars`() {
        val vm = createViewModel()
        vm.onForgotNewPasswordAgain("pass\nword")
        assertThat(vm.forgotNewPasswordAgain.value).isEqualTo("password")
    }
}
