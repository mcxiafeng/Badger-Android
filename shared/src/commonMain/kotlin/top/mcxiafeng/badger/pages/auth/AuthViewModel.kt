package top.mcxiafeng.badger.pages.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.prefs.setServerUrlConfigured
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.network.RegisterPolicy
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.KtorHttpCore
import top.mcxiafeng.badger.utils.SafeLog

private const val TAG = "AuthViewModel"

private const val PURPOSE_REGISTER = "register"

private const val PROBE_TIMEOUT_MS = 15_000L

class AuthViewModel : ViewModel() {

    private val userAuthRepository: UserAuthRepository = KoinComponentBy.get()
    private val serverUrlHolder: ServerUrlHolder = KoinComponentBy.get()
    private val serverApiFactory: ServerApiFactory = KoinComponentBy.get()
    private val http: KtorHttpCore = KtorHttpCore()

    
    private var inFlightJob: Job? = null

    
    private var probeJob: Job? = null

    
    private var emailCodeBoundTo: String? = null

    private val _authMode = MutableStateFlow<AuthMode>(AuthMode.Login)
    val authMode: StateFlow<AuthMode> = _authMode.asStateFlow()

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _credentials = MutableStateFlow(AuthCredentials())
    val credentials: StateFlow<AuthCredentials> = _credentials.asStateFlow()

    private val _registerState = MutableStateFlow(RegisterUiState())
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    private val _forgotForm = MutableStateFlow(ForgotUiState())
    val forgotForm: StateFlow<ForgotUiState> = _forgotForm.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    
    val isBusy: Boolean
        get() = _state.value is AuthUiState.Loading || _state.value is AuthUiState.SignedIn

    

    fun onUsername(raw: String) {
        _credentials.update { it.copy(username = AuthValidator.sanitizeIdentity(raw)) }
    }

    fun onPassword(raw: String) {
        _credentials.update { it.copy(password = AuthValidator.sanitizePassword(raw)) }
    }

    fun onEmail(raw: String) {
        _registerState.update { it.copy(email = AuthValidator.sanitizeIdentity(raw)) }
    }

    fun onPasswordAgain(raw: String) {
        _registerState.update { it.copy(passwordAgain = AuthValidator.sanitizePassword(raw)) }
    }

    fun onCaptchaInput(raw: String) {
        _registerState.update { it.copy(captchaInput = AuthValidator.sanitizeCode(raw)) }
    }

    fun onEmailCodeInput(raw: String) {
        _registerState.update { it.copy(emailCodeInput = AuthValidator.sanitizeCode(raw)) }
    }

    fun onForgotEmail(raw: String) {
        _forgotForm.update { it.copy(email = AuthValidator.sanitizeIdentity(raw)) }
    }

    fun onForgotCode(raw: String) {
        _forgotForm.update { it.copy(code = AuthValidator.sanitizeCode(raw)) }
    }

    fun onForgotNewPassword(raw: String) {
        _forgotForm.update { it.copy(newPassword = AuthValidator.sanitizePassword(raw)) }
    }

    fun onForgotNewPasswordAgain(raw: String) {
        _forgotForm.update { it.copy(newPasswordAgain = AuthValidator.sanitizePassword(raw)) }
    }

    

    

    fun onAuthScreenEnter() {
        BadgerLog.d(TAG, "onAuthScreenEnter")
        cancelInFlight()
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.Login
    }

    
    fun onForgotScreenEnter() {
        BadgerLog.d(TAG, "onForgotScreenEnter")
        _state.value = AuthUiState.Idle
        _forgotForm.value = ForgotUiState()
    }

    

    fun probeServerConnection() {
        if (_probing.value) return
        if (serverUrlHolder.isUrlVerified.value) return
        val url = serverUrlHolder.url.value
        BadgerLog.d(TAG, "probeServerConnection: probing ${SafeLog.url(url)}")
        _probing.value = true
        probeJob = viewModelScope.launch {
            try {
                val reachable = when (val result = http.get(url, timeoutMs = PROBE_TIMEOUT_MS)) {
                    is HttpResult.Success -> true
                    is HttpResult.Failure -> result.code > 0
                }
                BadgerLog.d(TAG, "probeServerConnection: ${SafeLog.url(url)} → reachable=$reachable")
                if (reachable) serverUrlHolder.markUrlVerified()
            } finally {
                _probing.value = false
            }
        }
    }

    
    fun switchToLogin() {
        BadgerLog.d(TAG, "switchToLogin()")
        cancelInFlight()
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.Login
    }

    
    fun switchToRegister() {
        BadgerLog.d(TAG, "switchToRegister()")
        cancelInFlight()
        emailCodeBoundTo = null
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.Register
        ensureRegisterPolicy(forceCaptchaRefresh = true)
    }

    
    fun reset() {
        BadgerLog.d(TAG, "reset() — clearing all auth form state")
        cancelInFlight()
        _credentials.value = AuthCredentials()
        _registerState.value = RegisterUiState()
        _forgotForm.value = ForgotUiState()
        emailCodeBoundTo = null
        _authMode.value = AuthMode.Login
        _state.value = AuthUiState.Idle
    }

    

    fun canSubmitLogin(): Boolean =
        !isBusy && AuthValidator.loginBlockReason(
            _credentials.value.username,
            _credentials.value.password,
        ) == null

    fun canSubmitRegister(): Boolean {
        if (isBusy) return false
        val c = _credentials.value
        val r = _registerState.value
        return AuthValidator.registerBlockReason(
            c.username, r.email, c.password, r.passwordAgain, r.policy, r.captchaInput, r.emailCodeInput,
        ) == null
    }

    fun canSubmitForgotPassword(): Boolean {
        if (isBusy) return false
        val f = _forgotForm.value
        return AuthValidator.forgotBlockReason(f.email, f.code, f.newPassword, f.newPasswordAgain) == null
    }

    

    
    fun ensureRegisterPolicy(forceCaptchaRefresh: Boolean = false) {
        val current = _registerState.value
        if (current.policy != null) {
            if (forceCaptchaRefresh && current.policy.requireCaptcha) refreshCaptcha()
            return
        }
        if (current.policyLoading) return
        _registerState.update { it.copy(policyLoading = true, policyError = null) }
        viewModelScope.launch {
            runCatching { userAuthRepository.fetchRegisterPolicy() }
                .onSuccess { p ->
                    BadgerLog.d(
                        TAG,
                        "ensureRegisterPolicy: allowRegister=${p.allowRegister} " +
                            "requireCaptcha=${p.requireCaptcha} requireEmailCode=${p.requireEmailCode}",
                    )
                    _registerState.update { it.copy(policy = p, policyLoading = false, policyError = null) }
                    if (p.requireCaptcha) refreshCaptcha()
                }
                .onFailure { e ->
                    BadgerLog.w(TAG, "ensureRegisterPolicy: failed ${e::class.simpleName}: ${e.message}")
                    
                    
                    _registerState.update {
                        it.copy(
                            policyLoading = false,
                            policyError = e.message ?: "注册策略加载失败",
                            policy = RegisterPolicy(allowRegister = true, requireCaptcha = false, requireEmailCode = false),
                        )
                    }
                }
        }
    }

    
    fun refreshCaptcha() {
        if (_registerState.value.captchaLoading) return
        _registerState.update { it.copy(captchaLoading = true, captchaCode = null, captchaImageBase64 = null) }
        viewModelScope.launch {
            runCatching { userAuthRepository.fetchCaptcha() }
                .onSuccess { c ->
                    BadgerLog.d(TAG, "refreshCaptcha: id=${c.captchaId.take(8)} hasImage=${c.imageBase64 != null}")
                    _registerState.update {
                        it.copy(
                            captchaId = c.captchaId,
                            captchaCode = c.code,
                            captchaImageBase64 = c.imageBase64,
                            captchaInput = "",
                            captchaLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    BadgerLog.w(TAG, "refreshCaptcha: failed ${e::class.simpleName}: ${e.message}")
                    _registerState.update { it.copy(captchaLoading = false, captchaCode = null, captchaImageBase64 = null) }
                }
        }
    }

    
    fun sendEmailCode() {
        val r = _registerState.value
        if (r.sendingEmailCode) return
        if (!AuthValidator.isValidEmail(r.email)) {
            _state.value = AuthUiState.Error("请先填写正确的邮箱")
            return
        }
        emailCodeBoundTo = r.email
        _registerState.update { it.copy(sendingEmailCode = true, emailCodeHint = null) }
        inFlightJob = viewModelScope.launch {
            runCatching { userAuthRepository.sendVerificationCode(r.email, PURPOSE_REGISTER) }
                .onSuccess { result ->
                    BadgerLog.d(TAG, "sendEmailCode: sent to ${SafeLog.email(r.email)} emailSent=${result.emailSent}")
                    _registerState.update {
                        if (!result.emailSent && result.code != null) {
                            it.copy(
                                emailCodeCaptchaId = result.captchaId,
                                sendingEmailCode = false,
                                emailCodeSent = true,
                                emailCodeInput = result.code,
                                emailCodeHint = "验证码已发送（开发模式明文回显）",
                            )
                        } else {
                            it.copy(
                                emailCodeCaptchaId = result.captchaId,
                                sendingEmailCode = false,
                                emailCodeSent = true,
                                emailCodeInput = "",
                                emailCodeHint = "验证码已发送到邮箱，请查收",
                            )
                        }
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) {
                        
                        _registerState.update { it.copy(sendingEmailCode = false) }
                        throw e
                    }
                    BadgerLog.w(TAG, "sendEmailCode: failed ${e::class.simpleName}: ${e.message}")
                    _registerState.update { it.copy(sendingEmailCode = false) }
                    _state.value = AuthUiState.Error(e.message ?: "验证码发送失败")
                }
        }
    }

    
    fun sendForgotCode() {
        val f = _forgotForm.value
        if (f.sendingCode) return
        if (!AuthValidator.isValidEmail(f.email)) {
            _state.value = AuthUiState.Error("请先填写正确的邮箱")
            return
        }
        _forgotForm.update { it.copy(sendingCode = true, codeHint = null) }
        viewModelScope.launch {
            runCatching {
                
                userAuthRepository.sendVerificationCode(f.email, "forgotPassword")
            }
                .onSuccess { result ->
                    BadgerLog.d(TAG, "sendForgotCode: sent to ${SafeLog.email(f.email)} emailSent=${result.emailSent}")
                    _forgotForm.update {
                        if (!result.emailSent && result.code != null) {
                            it.copy(
                                codeCaptchaId = result.captchaId,
                                sendingCode = false,
                                codeSent = true,
                                code = result.code,
                                codeHint = "验证码已发送（开发模式明文回显）",
                            )
                        } else {
                            it.copy(
                                codeCaptchaId = result.captchaId,
                                sendingCode = false,
                                codeSent = true,
                                code = "",
                                codeHint = "验证码已发送到邮箱，请查收",
                            )
                        }
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) {
                        _forgotForm.update { it.copy(sendingCode = false) }
                        throw e
                    }
                    BadgerLog.w(TAG, "sendForgotCode: failed ${e::class.simpleName}: ${e.message}")
                    _forgotForm.update { it.copy(sendingCode = false) }
                    _state.value = AuthUiState.Error(e.message ?: "验证码发送失败")
                }
        }
    }

    

    fun signIn() {
        if (isBusy) {
            BadgerLog.w(TAG, "signIn: reentry blocked (busy)")
            return
        }
        val c = _credentials.value
        val reason = AuthValidator.loginBlockReason(c.username, c.password)
        if (reason != null) {
            
            BadgerLog.w(TAG, "signIn: blocked by validator: $reason")
            _state.value = AuthUiState.Error(reason)
            return
        }
        BadgerLog.d(TAG, "signIn: submit user=${SafeLog.user(c.username)}")
        _state.value = AuthUiState.Loading
        inFlightJob = viewModelScope.launch {
            runCatching { userAuthRepository.login(c.username, c.password) }
                .onSuccess { onAuthSuccess("signIn") }
                .onFailure { e ->
                    if (e is CancellationException) {
                        onSubmissionCancelled()
                        throw e
                    }
                    onAuthFailure(e, "登录失败")
                }
        }
    }

    fun register() {
        if (isBusy) {
            BadgerLog.w(TAG, "register: reentry blocked (busy)")
            return
        }
        val c = _credentials.value
        val r = _registerState.value
        val reason = AuthValidator.registerBlockReason(
            c.username, r.email, c.password, r.passwordAgain, r.policy, r.captchaInput, r.emailCodeInput,
        )
        if (reason != null) {
            BadgerLog.w(TAG, "register: blocked by validator: $reason")
            _state.value = AuthUiState.Error(reason)
            return
        }
        
        if (r.policy?.requireEmailCode == true && emailCodeBoundTo != null && r.email != emailCodeBoundTo) {
            BadgerLog.w(TAG, "register: email changed since sendEmailCode, clearing email code")
            emailCodeBoundTo = null
            _registerState.update {
                it.copy(emailCodeCaptchaId = null, emailCodeInput = "", emailCodeSent = false, emailCodeHint = null)
            }
            _state.value = AuthUiState.Error("邮箱已更改，请重新发送验证码")
            return
        }
        BadgerLog.d(TAG, "register: submit user=${SafeLog.user(c.username)} email=${SafeLog.email(r.email)}")
        _state.value = AuthUiState.Loading
        inFlightJob = viewModelScope.launch {
            runCatching {
                userAuthRepository.register(
                    username = c.username,
                    email = r.email,
                    password = c.password,
                    passwordAgain = r.passwordAgain,
                    captchaId = r.captchaId.takeIf { r.policy?.requireCaptcha == true },
                    captchaCode = r.captchaInput.takeIf { r.policy?.requireCaptcha == true },
                    emailCaptchaId = r.emailCodeCaptchaId.takeIf { r.policy?.requireEmailCode == true },
                    emailCode = r.emailCodeInput.takeIf { r.policy?.requireEmailCode == true },
                )
            }
                .onSuccess { onAuthSuccess("register") }
                .onFailure { e ->
                    if (e is CancellationException) {
                        onSubmissionCancelled()
                        throw e
                    }
                    onAuthFailure(e, "注册失败")
                }
        }
    }

    
    fun resetPassword() {
        if (isBusy) return
        val f = _forgotForm.value
        val reason = AuthValidator.forgotBlockReason(f.email, f.code, f.newPassword, f.newPasswordAgain)
        if (reason != null) {
            BadgerLog.w(TAG, "resetPassword: blocked by validator: $reason")
            _state.value = AuthUiState.Error(reason)
            return
        }
        BadgerLog.d(TAG, "resetPassword: submit email=${SafeLog.email(f.email)}")
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            runCatching {
                userAuthRepository.forgotPassword(
                    email = f.email,
                    captchaId = f.codeCaptchaId ?: "",
                    captchaCode = f.code,
                    newPassword = f.newPassword,
                    newPasswordAgain = f.newPasswordAgain,
                )
            }
                .onSuccess {
                    BadgerLog.d(TAG, "resetPassword: success, emitting ResetDone")
                    _state.value = AuthUiState.ResetDone
                }
                .onFailure { e ->
                    if (e is CancellationException) {
                        onSubmissionCancelled()
                        throw e
                    }
                    onAuthFailure(e, "密码重置失败")
                }
        }
    }

    

    fun applyServerUrl(newUrl: String) {
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized.isBlank()) {
            BadgerLog.w(TAG, "applyServerUrl: blank input ignored")
            return
        }
        serverUrlHolder.set(normalized)
        serverApiFactory.updateBaseUrl(normalized)
        setServerUrlConfigured(true)
        
        probeJob?.cancel()
        probeJob = null
        BadgerLog.d(TAG, "applyServerUrl: hot-applied ${SafeLog.url(normalized)}")
    }

    

    private fun cancelInFlight() {
        inFlightJob?.cancel()
        inFlightJob = null
    }

    
    private fun onSubmissionCancelled() {
        BadgerLog.d(TAG, "submission cancelled, state back to Idle")
        _state.value = AuthUiState.Idle
    }

    private fun onAuthSuccess(source: String) {
        BadgerLog.d(TAG, "$source: repo success, transitioning to SignedIn")
        
        serverUrlHolder.markUrlVerified()
        
        _credentials.update { it.copy(password = "") }
        _state.value = AuthUiState.SignedIn
    }

    private fun onAuthFailure(e: Throwable, fallback: String) {
        val msg = e.message ?: fallback
        BadgerLog.w(TAG, "auth failed: $msg")
        _state.value = AuthUiState.Error(msg)
    }
}
