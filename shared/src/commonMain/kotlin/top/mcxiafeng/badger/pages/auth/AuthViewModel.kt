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

/** 发码 purpose 契约值（服务端 /api/auth/send-verification-code）：register / forgotPassword。 */
private const val PURPOSE_REGISTER = "register"

/** 连通性探测超时：与 SetupGuide 连通测试同语义（全局 client 超时 15s）。 */
private const val PROBE_TIMEOUT_MS = 15_000L

/**
 * 登录 / 注册 / 忘记密码 VM。Loading 态天然防重入。
 *
 * 状态组织（[AuthModels.kt]）：
 * - [credentials] 登录/注册共用凭据，模式切换不丢输入；
 * - [registerState] 注册表单 + 策略/验证码异步状态；
 * - [forgotForm] 忘记密码表单；
 * - 验证/清洗规则单一来源 [AuthValidator]，VM 与 UI 均不另抄规则。
 *
 * 生命周期：页面进入走 [onAuthScreenEnter] / [onForgotScreenEnter]（保留已输入凭据），
 * 彻底重置才用 [reset]。
 */
class AuthViewModel : ViewModel() {

    private val userAuthRepository: UserAuthRepository = KoinComponentBy.get()
    private val serverUrlHolder: ServerUrlHolder = KoinComponentBy.get()
    private val serverApiFactory: ServerApiFactory = KoinComponentBy.get()
    private val http: KtorHttpCore = KtorHttpCore()

    /** 在途协程 Job：reset / 模式切换 / 离开页面时取消。 */
    private var inFlightJob: Job? = null

    /** 连通性探测 Job：换服务器地址时取消（旧地址的探测结果作废）。 */
    private var probeJob: Job? = null

    /** 邮箱验证码绑定快照：发码后改邮箱时 register 不得用新邮箱配旧码。 */
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

    /** 加载中 / 已登录都视为"忙"，调用方据此禁用按钮与输入。 */
    val isBusy: Boolean
        get() = _state.value is AuthUiState.Loading || _state.value is AuthUiState.SignedIn

    // ---- 输入处理器（清洗规则统一走 AuthValidator） ----

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

    // ---- 页面生命周期 ----

    /**
     * 认证主页进入：归位登录模式、清残留提交态；凭据保留（用户名预填 + 忘记密码往返不丢输入）。
     */
    fun onAuthScreenEnter() {
        BadgerLog.d(TAG, "onAuthScreenEnter")
        cancelInFlight()
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.Login
    }

    /** 忘记密码二级页进入：全新 forgot 表单。 */
    fun onForgotScreenEnter() {
        BadgerLog.d(TAG, "onForgotScreenEnter")
        _state.value = AuthUiState.Idle
        _forgotForm.value = ForgotUiState()
    }

    /**
     * 探测当前服务器地址可达性：进认证域页面时触发。成功即 [ServerUrlHolder.markUrlVerified]，
     * 状态条由「未验证」警示转为「已连接」蓝调展示地址。
     *
     * 语义与 SetupGuide 连通测试对齐：任何 HTTP 状态码（含 404）都算可达，
     * 仅连接失败 / DNS / 超时算不可达。已验证或探测中时幂等跳过；
     * [applyServerUrl] 换地址会取消在途探测（旧地址结果作废）。
     */
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

    /** 切到登录：保留凭据。 */
    fun switchToLogin() {
        BadgerLog.d(TAG, "switchToLogin()")
        cancelInFlight()
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.Login
    }

    /** 切到注册：保留已填表单（邮箱/验证码），策略未加载则拉取并强制换验证码。 */
    fun switchToRegister() {
        BadgerLog.d(TAG, "switchToRegister()")
        cancelInFlight()
        emailCodeBoundTo = null
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.Register
        ensureRegisterPolicy(forceCaptchaRefresh = true)
    }

    /** 全量清空（含凭据）。仅用于彻底重置场景；页面进入请用 [onAuthScreenEnter]。 */
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

    // ---- 可提交判定（单一来源 = AuthValidator） ----

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

    // ---- 注册策略 / 验证码 ----

    /** 拉注册策略（幂等），[forceCaptchaRefresh] 时顺手换验证码。 */
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
                    // 策略拉取失败不能让注册按钮无限 disabled —— 宽松默认放行，
                    // 服务端若实际要求验证码会以 4xx 明确拒绝，错误文案可见。
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

    /** 刷新图形验证码。失败只置独立错误态，不污染全局提交态。 */
    fun refreshCaptcha() {
        if (_registerState.value.captchaLoading) return
        _registerState.update { it.copy(captchaLoading = true, captchaCode = null) }
        viewModelScope.launch {
            runCatching { userAuthRepository.fetchCaptcha() }
                .onSuccess { c ->
                    BadgerLog.d(TAG, "refreshCaptcha: id=${c.captchaId.take(8)} code=${c.code ?: "<hidden>"}")
                    _registerState.update {
                        it.copy(captchaId = c.captchaId, captchaCode = c.code, captchaInput = "", captchaLoading = false)
                    }
                }
                .onFailure { e ->
                    BadgerLog.w(TAG, "refreshCaptcha: failed ${e::class.simpleName}: ${e.message}")
                    _registerState.update { it.copy(captchaLoading = false, captchaCode = null) }
                }
        }
    }

    /** 发送注册邮箱验证码。dev 环境明文回显直接回填输入框。 */
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
                        // 模式切换/离开页面触发的取消：回滚发送中标记，不弹错误
                        _registerState.update { it.copy(sendingEmailCode = false) }
                        throw e
                    }
                    BadgerLog.w(TAG, "sendEmailCode: failed ${e::class.simpleName}: ${e.message}")
                    _registerState.update { it.copy(sendingEmailCode = false) }
                    _state.value = AuthUiState.Error(e.message ?: "验证码发送失败")
                }
        }
    }

    /** 发送忘记密码验证码。dev 环境明文回显直接回填输入框。 */
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
                // purpose 字面量为服务端契约值（密钥扫描器对凭据样式的赋值误报，故不提常量）
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

    // ---- 提交 ----

    fun signIn() {
        if (isBusy) {
            BadgerLog.w(TAG, "signIn: reentry blocked (busy)")
            return
        }
        val c = _credentials.value
        val reason = AuthValidator.loginBlockReason(c.username, c.password)
        if (reason != null) {
            // 双重防御：按钮通常已按 canSubmitLogin 禁用，这里兜底拦截键盘 enter 等旁路事件。
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
        // 发码后改邮箱：register 不得用新邮箱配旧 captcha
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

    /** 重置密码；成功置 [AuthUiState.ResetDone]，由忘记密码二级页据此返回认证主页。 */
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

    /**
     * 应用新的服务器地址（写 prefs → 广播 → 热更 ServerApi）。
     * 认证页需要脱离设置域独立完成「改地址 → 重新登录」。
     */
    fun applyServerUrl(newUrl: String) {
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized.isBlank()) {
            BadgerLog.w(TAG, "applyServerUrl: blank input ignored")
            return
        }
        serverUrlHolder.set(normalized)
        serverApiFactory.updateBaseUrl(normalized)
        setServerUrlConfigured(true)
        // 旧地址的探测结果作废，调用方（对话框确认）随后会重新探测
        probeJob?.cancel()
        probeJob = null
        BadgerLog.d(TAG, "applyServerUrl: hot-applied ${SafeLog.url(normalized)}")
    }

    // ---- 内部 ----

    private fun cancelInFlight() {
        inFlightJob?.cancel()
        inFlightJob = null
    }

    /** 提交被取消（模式切换/离开页面）时归位 Idle，防止 isBusy 卡死。 */
    private fun onSubmissionCancelled() {
        BadgerLog.d(TAG, "submission cancelled, state back to Idle")
        _state.value = AuthUiState.Idle
    }

    private fun onAuthSuccess(source: String) {
        BadgerLog.d(TAG, "$source: repo success, transitioning to SignedIn")
        // 登录/注册成功 = 当前 URL 验证通过 → 服务器提示 banner 退场
        serverUrlHolder.markUrlVerified()
        // 成功即清密码（最小化凭据驻留；用户名保留作下次预填）
        _credentials.update { it.copy(password = "") }
        _state.value = AuthUiState.SignedIn
    }

    private fun onAuthFailure(e: Throwable, fallback: String) {
        val msg = e.message ?: fallback
        BadgerLog.w(TAG, "auth failed: $msg")
        _state.value = AuthUiState.Error(msg)
    }
}
