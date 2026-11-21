package top.mcxiafeng.badger.pages.auth

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.auth.AuthApi
import top.mcxiafeng.badger.network.auth.RegisterPolicy
import top.mcxiafeng.badger.network.core.ServerAddressStore
import top.mcxiafeng.badger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var authApi: AuthApi
    private lateinit var addressStore: ServerAddressStore
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        authApi = mockk(relaxed = true)
        addressStore = mockk(relaxed = true)
        every { addressStore.activeUrl } returns MutableStateFlow("http://10.0.0.1:8080")
        every { addressStore.isUrlVerified } returns MutableStateFlow(false)
        viewModel = AuthViewModel(authApi, addressStore)
    }

    // —— 输入净化 ——

    @Test
    fun onUsername_filtersNonAsciiAndWhitespace() {
        viewModel.onUsername("ab 中文\tCD！")
        assertThat(viewModel.credentials.value.username).isEqualTo("abCD")
    }

    @Test
    fun onPassword_filtersControlCharsButKeepsVisibleAscii() {
        viewModel.onPassword("p a\tss\nw0rd!")
        assertThat(viewModel.credentials.value.password).isEqualTo("passw0rd!")
    }

    // —— 登录 ——

    @Test
    fun canSubmitLogin_blockedWhenBlank() {
        assertThat(viewModel.canSubmitLogin()).isFalse()
        viewModel.onUsername("alice")
        assertThat(viewModel.canSubmitLogin()).isFalse()
        viewModel.onPassword("password123")
        assertThat(viewModel.canSubmitLogin()).isTrue()
    }

    @Test
    fun signIn_success_clearsPasswordAndEmitsSuccess() = runTest {
        coEvery { authApi.login("alice", "password123", "http://10.0.0.1:8080") } returns HttpResult.Success("""{"code":0,"data":{"token":"t"}}""")
        viewModel.onUsername("alice")
        viewModel.onPassword("password123")

        viewModel.signIn()
        advanceUntilIdle()

        assertThat(viewModel.submitState.value).isEqualTo(AuthSubmitState.Success(SuccessKind.LOGIN))
        assertThat(viewModel.credentials.value.password).isEmpty()
    }

    @Test
    fun signIn_networkFailure_mapsToUserMessage() = runTest {
        coEvery { authApi.login(any(), any(), any()) } returns
            HttpResult.Failure(0, null, null, HttpResult.ErrorType.NETWORK)
        viewModel.onUsername("alice")
        viewModel.onPassword("password123")

        viewModel.signIn()
        advanceUntilIdle()

        val state = viewModel.submitState.value
        assertThat(state).isInstanceOf(AuthSubmitState.Error::class.java)
        assertThat((state as AuthSubmitState.Error).message).isEqualTo("网络连接失败，请检查网络")
    }

    @Test
    fun signIn_capturesAddressBeforeCoroutineStarts() = runTest {
        val url = addressStore.activeUrl as MutableStateFlow<String>
        coEvery { authApi.login(any(), any(), any()) } returns HttpResult.Success("{}")
        viewModel.onUsername("alice")
        viewModel.onPassword("password123")
        viewModel.signIn()
        url.value = "https://other.example"
        advanceUntilIdle()
        coVerify(exactly = 0) { authApi.login(any(), any(), "https://other.example") }
        assertThat(viewModel.submitState.value).isEqualTo(AuthSubmitState.Idle)
    }

    @Test
    fun signIn_addressChangeIgnoresLateSuccess() = runTest {
        val response = CompletableDeferred<HttpResult>()
        coEvery { authApi.login(any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        viewModel.onUsername("alice")
        viewModel.onPassword("password123")
        viewModel.signIn()
        runCurrent()
        (addressStore.activeUrl as MutableStateFlow<String>).value = "https://other.example"
        runCurrent()
        response.complete(HttpResult.Success("{}"))
        advanceUntilIdle()
        assertThat(viewModel.submitState.value).isEqualTo(AuthSubmitState.Idle)
        verify(exactly = 0) { addressStore.markUrlVerified() }
    }

    @Test
    fun signIn_leavingPageIgnoresLateSuccess() = runTest {
        val response = CompletableDeferred<HttpResult>()
        coEvery { authApi.login(any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        viewModel.onUsername("alice")
        viewModel.onPassword("password123")
        viewModel.signIn()
        runCurrent()
        viewModel.onScreenExit()
        response.complete(HttpResult.Success("{}"))
        advanceUntilIdle()
        assertThat(viewModel.submitState.value).isEqualTo(AuthSubmitState.Idle)
        verify(exactly = 0) { addressStore.markUrlVerified() }
    }

    @Test
    fun signIn_secondSubmitWinsOverFirstLateFailure() = runTest {
        val first = CompletableDeferred<HttpResult>()
        coEvery { authApi.login("alice", any(), any()) } coAnswers {
            withContext(NonCancellable) { first.await() }
        }
        coEvery { authApi.login("bob", any(), any()) } returns HttpResult.Success("{}")
        viewModel.onUsername("alice")
        viewModel.onPassword("password123")
        viewModel.signIn()
        runCurrent()
        viewModel.onUsername("bob")
        viewModel.signIn()
        runCurrent()
        first.complete(HttpResult.Failure(0, null, null, HttpResult.ErrorType.NETWORK))
        advanceUntilIdle()
        assertThat(viewModel.submitState.value).isEqualTo(AuthSubmitState.Success(SuccessKind.LOGIN))
    }

    // —— 注册策略 ——

    @Test
    fun loadRegisterPolicy_failure_marksPolicyFailedAndAllowsRetry() = runTest {
        coEvery { authApi.registerPolicy() } returns null

        viewModel.loadRegisterPolicy()
        advanceUntilIdle()
        assertThat(viewModel.register.value.policyFailed).isTrue()
        assertThat(viewModel.register.value.policyLoading).isFalse()

        coEvery { authApi.registerPolicy() } returns RegisterPolicy()
        viewModel.loadRegisterPolicy()
        advanceUntilIdle()
        assertThat(viewModel.register.value.policy).isNotNull()
        assertThat(viewModel.register.value.policyFailed).isFalse()
    }

    @Test
    fun canSubmitRegister_blockedWhilePolicyMissing() {
        viewModel.onUsername("alice")
        viewModel.onEmail("alice@example.com")
        viewModel.onPassword("password123")
        viewModel.onPasswordAgain("password123")
        assertThat(viewModel.canSubmitRegister()).isFalse()
    }

    @Test
    fun registerGate_blockedWhenPolicyDisallows() {
        val closed = RegisterPolicy(allowRegister = false, requireCaptcha = false)
        // policy 只能由 loadRegisterPolicy 走网络填充，门槛逻辑直接验证 AuthValidator 单一来源
        val reason = AuthValidator.registerBlockReason(
            "alice", "alice@example.com", "password123", "password123", closed,
        )
        assertThat(reason).isEqualTo("注册功能已关闭")

        val captchaRequired = RegisterPolicy(allowRegister = true, requireCaptcha = true)
        val captchaReason = AuthValidator.registerBlockReason(
            "alice", "alice@example.com", "password123", "password123", captchaRequired,
        )
        assertThat(captchaReason).isEqualTo("服务端要求图形验证码，当前版本暂不支持")
    }

    // —— 忘记密码 ——

    @Test
    fun onForgotEmail_changeInvalidatesSentCode() = runTest {
        coEvery { authApi.sendVerificationCode(any()) } returns HttpResult.Success("{}")
        viewModel.onForgotEmail("alice@example.com")

        viewModel.sendForgotCode()
        advanceUntilIdle()
        assertThat(viewModel.forgot.value.delivery.sent).isTrue()

        viewModel.onForgotCode("123456")
        viewModel.onForgotEmail("bob@example.com")
        assertThat(viewModel.forgot.value.code).isEmpty()
        assertThat(viewModel.forgot.value.delivery.sent).isFalse()
    }

    @Test
    fun sendForgotCode_invalidEmail_blocked() = runTest {
        viewModel.onForgotEmail("not-an-email")
        viewModel.sendForgotCode()
        advanceUntilIdle()

        val state = viewModel.submitState.value
        assertThat((state as AuthSubmitState.Error).message).isEqualTo("请先填写有效的邮箱")
    }

    @Test
    fun canSubmitForgotPassword_gates() {
        assertThat(viewModel.canSubmitForgotPassword()).isFalse()
        viewModel.onForgotEmail("alice@example.com")
        viewModel.onForgotCode("123456")
        viewModel.onForgotNewPassword("password123")
        assertThat(viewModel.canSubmitForgotPassword()).isFalse()
        viewModel.onForgotNewPasswordAgain("password123")
        assertThat(viewModel.canSubmitForgotPassword()).isTrue()
    }

    // —— 服务器连接探测 ——

    @Test
    fun probeServerConnection_success_marksVerified() = runTest {
        coEvery { authApi.probeBaseUrl("http://10.0.0.1:8080") } returns HttpResult.Success("{}")

        viewModel.probeServerConnection()
        advanceUntilIdle()

        verify { addressStore.markUrlVerified() }
        assertThat(viewModel.probing.value).isFalse()
    }

    @Test
    fun probeServerConnection_httpErrorStillReachable_marksVerified() = runTest {
        // 服务端以任意状态响应（含 404/500）都说明地址可达
        coEvery { authApi.probeBaseUrl(any()) } returns
            HttpResult.Failure(500, "err", null, HttpResult.ErrorType.SERVER)

        viewModel.probeServerConnection()
        advanceUntilIdle()

        verify { addressStore.markUrlVerified() }
    }

    @Test
    fun probeServerConnection_networkFailure_notVerified() = runTest {
        coEvery { authApi.probeBaseUrl(any()) } returns
            HttpResult.Failure(0, null, null, HttpResult.ErrorType.NETWORK)

        viewModel.probeServerConnection()
        advanceUntilIdle()

        verify(exactly = 0) { addressStore.markUrlVerified() }
        assertThat(viewModel.probing.value).isFalse()
    }

    @Test
    fun probeServerConnection_skipsWhenAlreadyVerified() = runTest {
        every { addressStore.isUrlVerified } returns MutableStateFlow(true)

        viewModel.probeServerConnection()
        advanceUntilIdle()

        coVerify(exactly = 0) { authApi.probeBaseUrl(any()) }
    }

    @Test
    fun signIn_success_marksUrlVerified() = runTest {
        coEvery { authApi.login("alice", "password123", "http://10.0.0.1:8080") } returns HttpResult.Success("""{"data":{"token":"t"}}""")
        viewModel.onUsername("alice")
        viewModel.onPassword("password123")

        viewModel.signIn()
        advanceUntilIdle()

        verify { addressStore.markUrlVerified() }
    }

    // —— 生命周期 ——

    @Test
    fun onScreenEnter_resetsSubmitState() = runTest {
        viewModel.onForgotEmail("bad")
        viewModel.sendForgotCode()
        advanceUntilIdle()
        assertThat(viewModel.submitState.value).isInstanceOf(AuthSubmitState.Error::class.java)

        viewModel.onScreenEnter()
        assertThat(viewModel.submitState.value).isEqualTo(AuthSubmitState.Idle)
    }

    // —— RegisterPolicy 解析（Gson 缺省值语义） ——

    @Test
    fun registerPolicy_from_usesServerDefaultsWhenFieldsMissing() {
        val policy = RegisterPolicy.from(JsonObject())
        assertThat(policy.allowRegister).isTrue()
        assertThat(policy.requireCaptcha).isTrue()
        assertThat(policy.requireEmailCode).isFalse()
    }

    @Test
    fun registerPolicy_from_prefersExplicitValues() {
        val data = JsonObject().apply {
            addProperty("allowRegister", false)
            addProperty("requireCaptcha", false)
            addProperty("requireEmailCode", true)
        }
        val policy = RegisterPolicy.from(data)
        assertThat(policy.allowRegister).isFalse()
        assertThat(policy.requireCaptcha).isFalse()
        assertThat(policy.requireEmailCode).isTrue()
    }
}
