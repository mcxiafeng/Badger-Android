package top.mcxiafeng.badger.pages.auth

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.RegisterPolicy
import top.mcxiafeng.badger.utils.SafeLog

/** [A2] 认证模式三态：登录、注册、忘记密码。 */
sealed interface AuthMode {
    data object Login : AuthMode
    data object Register : AuthMode
    data object ForgotPassword : AuthMode
}

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object SignedIn : AuthUiState
    data class Error(val message: String) : AuthUiState
}

private const val TAG = "AuthViewModel"

/**
 * Shared VM for the LoginScreen / RegisterScreen.
 *
 * The two screens use distinct Koin ViewModel keys so their username/password/email
 * inputs do not bleed across navigation. Dependencies are constructor-injected instead
 * of resolved through the global Service Locator, making the authentication state machine
 * deterministic and straightforward to unit test.
 */
class AuthViewModel(
    private val userAuthRepository: UserAuthRepository,
    private val serverUrlHolder: ServerUrlHolder,
) : ViewModel() {

    val username: MutableState<String> = mutableStateOf("")
    val email: MutableState<String> = mutableStateOf("")
    val password: MutableState<String> = mutableStateOf("")
    val passwordAgain: MutableState<String> = mutableStateOf("")

    // ---- [Phase 2] 验证码状态 ----
    val captchaInput: MutableState<String> = mutableStateOf("")
    val captchaId: MutableState<String?> = mutableStateOf(null)
    val captchaCode: MutableState<String?> = mutableStateOf(null)
    val captchaLoading: MutableState<Boolean> = mutableStateOf(false)
    val emailCodeInput: MutableState<String> = mutableStateOf("")
    val emailCaptchaId: MutableState<String?> = mutableStateOf(null)
    val sendingEmailCode: MutableState<Boolean> = mutableStateOf(false)
    val emailCodeSent: MutableState<Boolean> = mutableStateOf(false)
    val emailCodeHint: MutableState<String?> = mutableStateOf(null)

    // ---- [Phase 2] 注册策略 ----
    /** null = 策略未加载/加载中；[policyLoading] 标识在途请求。 */
    val registerPolicy: MutableState<RegisterPolicy?> = mutableStateOf(null)
    val policyLoading: MutableState<Boolean> = mutableStateOf(false)
    val policyError: MutableState<String?> = mutableStateOf(null)

    // ---- [A2] 认证模式 ----
    private val _authMode = MutableStateFlow<AuthMode>(AuthMode.Login)
    val authMode: StateFlow<AuthMode> = _authMode.asStateFlow()

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    // ---- [A2] 忘记密码状态 ----
    val forgotEmail: MutableState<String> = mutableStateOf("")
    val forgotCode: MutableState<String> = mutableStateOf("")
    val forgotNewPassword: MutableState<String> = mutableStateOf("")
    val forgotNewPasswordAgain: MutableState<String> = mutableStateOf("")
    val forgotCaptchaId: MutableState<String?> = mutableStateOf(null)
    val sendingForgotCode: MutableState<Boolean> = mutableStateOf(false)
    val forgotCodeSent: MutableState<Boolean> = mutableStateOf(false)
    val forgotCodeHint: MutableState<String?> = mutableStateOf(null)

    val onUsername: (String) -> Unit = { raw ->
        username.value = raw
            .filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }
            .trim()
    }
    val onEmail: (String) -> Unit = { raw ->
        email.value = raw
            .filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }
            .trim()
    }
    val onPassword: (String) -> Unit = { raw ->
        password.value = raw.filterNot { it.isISOControl() || it.code !in 0x21..0x7E }
    }
    val onPasswordAgain: (String) -> Unit = { raw ->
        passwordAgain.value = raw.filterNot { it.isISOControl() || it.code !in 0x21..0x7E }
    }

    val onForgotEmail: (String) -> Unit = { raw ->
        forgotEmail.value = raw
            .filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }
            .trim()
    }
    val onForgotCode: (String) -> Unit = { raw ->
        forgotCode.value = raw.filterNot { it.isISOControl() || it.isWhitespace() }.trim()
    }
    val onForgotNewPassword: (String) -> Unit = { raw ->
        forgotNewPassword.value = raw.filterNot { it.isISOControl() || it.code !in 0x21..0x7E }
    }
    val onForgotNewPasswordAgain: (String) -> Unit = { raw ->
        forgotNewPasswordAgain.value = raw.filterNot { it.isISOControl() || it.code !in 0x21..0x7E }
    }

    /** 加载中 / 已登录都视为忙，调用方据此禁用按钮与输入。 */
    val isBusy: Boolean
        get() = _state.value is AuthUiState.Loading || _state.value is AuthUiState.SignedIn

    fun reset() {
        Log.d(TAG, "reset() — clearing inputs and state")
        username.value = ""
        email.value = ""
        password.value = ""
        passwordAgain.value = ""
        captchaInput.value = ""
        emailCodeInput.value = ""
        captchaId.value = null
        captchaCode.value = null
        emailCaptchaId.value = null
        emailCodeSent.value = false
        emailCodeHint.value = null
        registerPolicy.value = null
        policyLoading.value = false
        policyError.value = null
        forgotEmail.value = ""
        forgotCode.value = ""
        forgotNewPassword.value = ""
        forgotNewPasswordAgain.value = ""
        forgotCaptchaId.value = null
        sendingForgotCode.value = false
        forgotCodeSent.value = false
        forgotCodeHint.value = null
        _authMode.value = AuthMode.Login
        _state.value = AuthUiState.Idle
    }

    fun switchToLogin() {
        Log.d(TAG, "switchToLogin()")
        email.value = ""
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.Login
    }

    fun switchToRegister() {
        Log.d(TAG, "switchToRegister()")
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.Register
        ensureRegisterPolicy(forceCaptchaRefresh = true)
    }

    fun switchToForgotPassword() {
        Log.d(TAG, "switchToForgotPassword()")
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.ForgotPassword
        forgotCode.value = ""
        forgotNewPassword.value = ""
        forgotNewPasswordAgain.value = ""
        forgotCaptchaId.value = null
        sendingForgotCode.value = false
        forgotCodeSent.value = false
        forgotCodeHint.value = null
    }

    fun ensureRegisterPolicy(forceCaptchaRefresh: Boolean = false) {
        if (registerPolicy.value != null) {
            if (forceCaptchaRefresh && registerPolicy.value?.requireCaptcha == true) refreshCaptcha()
            return
        }
        if (policyLoading.value) return
        policyLoading.value = true
        policyError.value = null
        viewModelScope.launch {
            runCatching { userAuthRepository.fetchRegisterPolicy() }
                .onSuccess { p ->
                    registerPolicy.value = p
                    policyLoading.value = false
                    if (p.requireCaptcha) refreshCaptcha()
                }
                .onFailure { e ->
                    Log.w(TAG, "ensureRegisterPolicy: failed ${e.javaClass.simpleName}: ${e.message}")
                    policyLoading.value = false
                    policyError.value = e.message ?: "注册策略加载失败"
                    // 保持与原契约一致：策略端点不可用时允许继续，由服务端最终校验。
                    registerPolicy.value = RegisterPolicy(
                        allowRegister = true,
                        requireCaptcha = false,
                        requireEmailCode = false,
                    )
                }
        }
    }

    fun refreshCaptcha() {
        if (captchaLoading.value) return
        captchaLoading.value = true
        captchaCode.value = null
        viewModelScope.launch {
            runCatching { userAuthRepository.fetchCaptcha() }
                .onSuccess { c ->
                    captchaId.value = c.captchaId
                    captchaCode.value = c.code
                    captchaInput.value = ""
                    captchaLoading.value = false
                    Log.d(TAG, "refreshCaptcha: id=${c.captchaId.take(8)} code=${c.code ?: "<hidden>"}")
                }
                .onFailure { e ->
                    Log.w(TAG, "refreshCaptcha: failed ${e.javaClass.simpleName}: ${e.message}")
                    captchaLoading.value = false
                    captchaCode.value = null
                }
        }
    }

    fun sendEmailCode() {
        if (sendingEmailCode.value) return
        val e = email.value
        if (!isValidEmail(e)) {
            _state.value = AuthUiState.Error("请先填写正确的邮箱")
            return
        }
        sendingEmailCode.value = true
        emailCodeHint.value = null
        viewModelScope.launch {
            runCatching { userAuthRepository.sendVerificationCode(e, "register") }
                .onSuccess { r ->
                    emailCaptchaId.value = r.captchaId
                    sendingEmailCode.value = false
                    emailCodeSent.value = true
                    if (!r.emailSent && r.code != null) {
                        emailCodeInput.value = r.code
                        emailCodeHint.value = "验证码已发送（开发模式明文回显）"
                    } else {
                        emailCodeInput.value = ""
                        emailCodeHint.value = "验证码已发送到邮箱，请查收"
                    }
                }
                .onFailure { e ->
                    Log.w(TAG, "sendEmailCode: failed ${e.javaClass.simpleName}: ${e.message}")
                    sendingEmailCode.value = false
                    _state.value = AuthUiState.Error(e.message ?: "验证码发送失败")
                }
        }
    }

    fun sendForgotCode() {
        if (sendingForgotCode.value) return
        val e = forgotEmail.value
        if (!isValidEmail(e)) {
            _state.value = AuthUiState.Error("请先填写正确的邮箱")
            return
        }
        sendingForgotCode.value = true
        forgotCodeHint.value = null
        viewModelScope.launch {
            runCatching { userAuthRepository.sendVerificationCode(e, "forgotPassword") }
                .onSuccess { r ->
                    forgotCaptchaId.value = r.captchaId
                    sendingForgotCode.value = false
                    forgotCodeSent.value = true
                    if (!r.emailSent && r.code != null) {
                        forgotCode.value = r.code
                        forgotCodeHint.value = "验证码已发送（开发模式明文回显）"
                    } else {
                        forgotCode.value = ""
                        forgotCodeHint.value = "验证码已发送到邮箱，请查收"
                    }
                }
                .onFailure { e ->
                    Log.w(TAG, "sendForgotCode: failed ${e.javaClass.simpleName}: ${e.message}")
                    sendingForgotCode.value = false
                    _state.value = AuthUiState.Error(e.message ?: "验证码发送失败")
                }
        }
    }

    fun resetPassword() {
        if (!canSubmitForgotPassword()) {
            val msg = when {
                !isValidEmail(forgotEmail.value) -> "请填写有效邮箱"
                forgotCode.value.isBlank() -> "请输入验证码"
                forgotNewPassword.value.length < 8 -> "新密码至少 8 位"
                forgotNewPasswordAgain.value != forgotNewPassword.value -> "两次密码不一致"
                else -> "请检查输入"
            }
            Log.w(TAG, "resetPassword: blocked by canSubmitForgotPassword: $msg")
            _state.value = AuthUiState.Error(msg)
            return
        }
        Log.d(TAG, "resetPassword: submit email=${SafeLog.email(forgotEmail.value)}")
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            val r = runCatching {
                userAuthRepository.forgotPassword(
                    email = forgotEmail.value,
                    captchaId = forgotCaptchaId.value ?: "",
                    captchaCode = forgotCode.value,
                    newPassword = forgotNewPassword.value,
                    newPasswordAgain = forgotNewPasswordAgain.value,
                )
            }
            _state.value = r.fold(
                onSuccess = {
                    val savedEmail = forgotEmail.value
                    switchToLogin()
                    email.value = savedEmail
                    AuthUiState.Idle
                },
                onFailure = {
                    val msg = it.message ?: "密码重置失败"
                    Log.w(TAG, "resetPassword: failed: $msg")
                    AuthUiState.Error(msg)
                },
            )
        }
    }

    fun canSubmitLogin(): Boolean = !isBusy && username.value.isNotBlank() && password.value.isNotBlank()

    fun canSubmitForgotPassword(): Boolean {
        if (isBusy) return false
        if (!isValidEmail(forgotEmail.value)) return false
        if (forgotCode.value.isBlank()) return false
        if (forgotNewPassword.value.length < 8) return false
        if (forgotNewPasswordAgain.value != forgotNewPassword.value) return false
        return true
    }

    fun canSubmitRegister(): Boolean {
        if (isBusy) return false
        val u = username.value
        if (u.length < 3 || u.length > 32) return false
        if (password.value.length < 8) return false
        if (!isValidEmail(email.value)) return false
        if (passwordAgain.value != password.value) return false
        val p = registerPolicy.value ?: return false
        if (!p.allowRegister) return false
        if (p.requireCaptcha && captchaInput.value.isBlank()) return false
        if (p.requireEmailCode && emailCodeInput.value.isBlank()) return false
        return true
    }

    private fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email)

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }

    fun isValidEmailForHint(email: String): Boolean = EMAIL_REGEX.matches(email)

    fun signIn() {
        if (!canSubmitLogin()) {
            Log.w(TAG, "signIn: blocked by canSubmitLogin (username/pwd blank)")
            _state.value = AuthUiState.Error("用户名和密码不能为空")
            return
        }
        Log.d(TAG, "signIn: submit user=${SafeLog.user(username.value)}")
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            val r = runCatching {
                userAuthRepository.login(username.value, password.value)
            }.fold(
                onSuccess = { it },
                onFailure = { Result.failure(it) },
            )
            _state.value = r.fold(
                onSuccess = {
                    Log.d(TAG, "signIn: repo success, transitioning to SignedIn")
                    serverUrlHolder.markUrlVerified()
                    AuthUiState.SignedIn
                },
                onFailure = {
                    val msg = it.message ?: "登录失败"
                    Log.w(TAG, "signIn: repo failed: $msg")
                    AuthUiState.Error(msg)
                },
            )
        }
    }

    fun register() {
        if (!canSubmitRegister()) {
            val u = username.value
            val p = registerPolicy.value
            val msg = when {
                u.length < 3 || u.length > 32 -> "用户名长度需 3-32 字符"
                password.value.length < 8 -> "密码至少 8 位"
                !isValidEmail(email.value) -> "请填写有效邮箱"
                passwordAgain.value != password.value -> "两次密码不一致"
                p == null -> "注册策略加载中，请稍候"
                !p.allowRegister -> "注册功能已关闭"
                p.requireCaptcha && captchaInput.value.isBlank() -> "请输入图形验证码"
                p.requireEmailCode && emailCodeInput.value.isBlank() -> "请输入邮箱验证码"
                else -> "请检查输入"
            }
            Log.w(TAG, "register: blocked by canSubmitRegister: $msg")
            _state.value = AuthUiState.Error(msg)
            return
        }
        val u = username.value
        Log.d(TAG, "register: submit user=${SafeLog.user(u)} email=${SafeLog.email(email.value)}")
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            val r = runCatching {
                userAuthRepository.register(
                    username = u,
                    email = email.value,
                    password = password.value,
                    passwordAgain = passwordAgain.value,
                    captchaId = captchaId.value.takeIf { registerPolicy.value?.requireCaptcha == true },
                    captchaCode = captchaInput.value.takeIf { registerPolicy.value?.requireCaptcha == true },
                    emailCaptchaId = emailCaptchaId.value.takeIf { registerPolicy.value?.requireEmailCode == true },
                    emailCode = emailCodeInput.value.takeIf { registerPolicy.value?.requireEmailCode == true },
                )
            }.fold(
                onSuccess = { it },
                onFailure = { Result.failure(it) },
            )
            _state.value = r.fold(
                onSuccess = {
                    Log.d(TAG, "register: repo success, transitioning to SignedIn")
                    serverUrlHolder.markUrlVerified()
                    AuthUiState.SignedIn
                },
                onFailure = {
                    val msg = it.message ?: "注册失败"
                    Log.w(TAG, "register: repo failed: $msg")
                    AuthUiState.Error(msg)
                },
            )
        }
    }
}
