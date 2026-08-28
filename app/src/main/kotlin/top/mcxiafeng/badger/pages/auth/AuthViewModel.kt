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
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.RegisterPolicy
import top.mcxiafeng.badger.utils.SafeLog

/**
 * [A2] 认证模式三态 — 替代 UI 层的 `isLoginMode: Boolean`，支持忘记密码第三入口。
 * UI 层（AuthScreens.kt, A3）根据此状态切换渲染内容。
 */
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
 * Shared VM for the LoginScreen / RegisterScreen. The two screens use
 * distinct koinViewModel keys so their username/password/email inputs do
 * not bleed across navigation.
 *
 * 加载态时所有输入与提交动作都会因为 [_state] = Loading 被禁用，天然防重入；
 * 这里不再额外加 isSubmitting 标志位。
 *
 * [Phase 2] 注册流程扩展（新 Java /api 契约）：
 * - `passwordAgain`（两次密码一致）+ 必填邮箱（旧实现邮箱可选，新服务端注册必需 email）；
 * - 注册策略 `registerPolicy` → `requireCaptcha`/`requireEmailCode` 决定渲染哪些验证码输入；
 * - 图形验证码：`getCaptcha` 返回明文 code（dev），直接展示供输入；邮箱验证码：`sendVerificationCode` + 输入框；
 * - `canSubmitRegister` 在策略就绪前不放开（防"验证码未加载就提交"竞态）；
 * - 注册成功后服务端不返回 token（`data:null`），自动登录由 `UserAuthRepository.register` 内部完成。
 *
 * [§14.2] 移除 `@HiltViewModel` 与 `@Inject` —— Koin 通过 `inject()` 字段注入
 * [UserAuthRepository]。`viewModel { ... }` 模式不适用,因为这个 VM 通过
 * `koinViewModel(key = ...)` 直接从 Koin 拿实例(参见 AuthScreens.kt)。
 */
class AuthViewModel : ViewModel() {

    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    val username: MutableState<String> = mutableStateOf("")
    val email: MutableState<String> = mutableStateOf("")
    val password: MutableState<String> = mutableStateOf("")
    val passwordAgain: MutableState<String> = mutableStateOf("")

    // ---- [Phase 2] 验证码状态 ----
    val captchaInput: MutableState<String> = mutableStateOf("")       // 用户输入的图形验证码
    val captchaId: MutableState<String?> = mutableStateOf(null)       // 当前 captchaId（getCaptcha 下发）
    val captchaCode: MutableState<String?> = mutableStateOf(null)     // 展示用明文 code（dev 下发）
    val captchaLoading: MutableState<Boolean> = mutableStateOf(false)
    val emailCodeInput: MutableState<String> = mutableStateOf("")     // 用户输入的邮箱验证码
    val emailCaptchaId: MutableState<String?> = mutableStateOf(null)
    val sendingEmailCode: MutableState<Boolean> = mutableStateOf(false)
    val emailCodeSent: MutableState<Boolean> = mutableStateOf(false)
    val emailCodeHint: MutableState<String?> = mutableStateOf(null)

    // ---- [Phase 2] 注册策略 ----
    /** null = 策略未加载/加载中；[policyLoading] 标识在途请求。 */
    val registerPolicy: MutableState<RegisterPolicy?> = mutableStateOf(null)
    val policyLoading: MutableState<Boolean> = mutableStateOf(false)
    val policyError: MutableState<String?> = mutableStateOf(null)

    // ---- [A2] 认证模式（三态：Login / Register / ForgotPassword） ----
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
        // [修复输入清洗] 过滤控制字符(\n / \r / \t 等)防止 IME 边界 / 粘贴异常
        // 把多行内容塞进单行字段,同时 trim 掉首尾空白。
        // [V2-UX]: 用户名限定 ASCII 可见字符 (0x21..0x7E)。允许 ASCII 字母数字 + _-. 标点,
        // 不允许中文 / 空格 / 其他 unicode 字符。与服务端 [a-zA-Z0-9_-]{3,32} 契约对齐,
        // 避免「用户输入了中文注册按钮一直 disabled」这种迷惑态。
        username.value = raw.filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }.trim()
    }
    val onEmail: (String) -> Unit = { raw ->
        // [修复输入清洗] + [V2-UX]: 邮箱只允许 ASCII 可见字符(字母数字 + @._%-),
        // 不允许中文 / 空格 / 控制字符。
        email.value = raw.filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }.trim()
    }
    val onPassword: (String) -> Unit = { raw ->
        // [修复输入清洗] 控制字符一律过滤 (\n / \r / \t 永远不是合法密码字符)。
        // [V2-UX]: 与 username / email 一致,密码也限制 ASCII 可见字符 (0x21..0x7E),
        // 避免中文 / emoji / 特殊 unicode 进入密码字段导致服务端 hash 校验不一致。
        // 空格一同禁掉 —— 移动端 IME 下首尾空格几乎不会被主动使用,却会跟复制粘贴、跨设备同步产生歧义。
        password.value = raw.filterNot { it.isISOControl() || it.code !in 0x21..0x7E }
    }
    /** 二次输入密码：与 [onPassword] 同清洗规则，保证两次比较语义一致。 */
    val onPasswordAgain: (String) -> Unit = { raw ->
        passwordAgain.value = raw.filterNot { it.isISOControl() || it.code !in 0x21..0x7E }
    }

    // ---- [A2] 忘记密码输入处理器（同 onEmail / onPassword 清洗规则） ----
    val onForgotEmail: (String) -> Unit = { raw ->
        forgotEmail.value = raw.filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }.trim()
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

    /** 加载中 / 已登录都视为"忙"，调用方据此禁用按钮与输入。 */
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
        // [A2] 清空忘记密码状态
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

    /** 切换到登录模式：保留 username 与 password，但清掉 error。 */
    fun switchToLogin() {
        Log.d(TAG, "switchToLogin()")
        email.value = ""
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.Login
    }

    /** 切换到注册模式：保留 username 与 password，加载注册策略（决定验证码形态）。 */
    fun switchToRegister() {
        Log.d(TAG, "switchToRegister()")
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.Register
        ensureRegisterPolicy(forceCaptchaRefresh = true)
    }

    /** [A2] 切换到忘记密码模式：清空错误态，重置忘记密码表单。 */
    fun switchToForgotPassword() {
        Log.d(TAG, "switchToForgotPassword()")
        _state.value = AuthUiState.Idle
        _authMode.value = AuthMode.ForgotPassword
        // 清空忘记密码表单（保留 email 如果用户从登录页带过来）
        forgotCode.value = ""
        forgotNewPassword.value = ""
        forgotNewPasswordAgain.value = ""
        forgotCaptchaId.value = null
        sendingForgotCode.value = false
        forgotCodeSent.value = false
        forgotCodeHint.value = null
    }

    /**
     * [Phase 2] 拉注册策略（幂等）：策略未加载才请求；[forceCaptchaRefresh]=true 时
     * 若策略要求图形验证码则顺手换一张（防 5 分钟 TTL 过期 / 切页残留旧码）。
     */
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
                    Log.d(TAG, "ensureRegisterPolicy: allowRegister=${p.allowRegister} requireCaptcha=${p.requireCaptcha} requireEmailCode=${p.requireEmailCode}")
                    registerPolicy.value = p
                    policyLoading.value = false
                    if (p.requireCaptcha) refreshCaptcha()
                }
                .onFailure { e ->
                    Log.w(TAG, "ensureRegisterPolicy: failed ${e.javaClass.simpleName}: ${e.message}")
                    policyLoading.value = false
                    policyError.value = e.message ?: "注册策略加载失败"
                    // [修复防御]: 策略拉取失败不能让注册按钮无限 disabled —— 用宽松默认值放行,
                    // 服务端若实际要求验证码会以 4xx 明确拒绝,客户端错误文案可见。
                    registerPolicy.value = RegisterPolicy(allowRegister = true, requireCaptcha = false, requireEmailCode = false)
                }
        }
    }

    /** [Phase 2] 刷新图形验证码（getCaptcha → captchaId + 明文 code 展示）。 */
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
                    // [修复防御]: 验证码加载失败给出可见错误而非静默 —— 用户能区分"网络问题"与"输入错误"
                    _state.value = AuthUiState.Error(e.message ?: "验证码加载失败，请重试")
                }
        }
    }

    /** [Phase 2] 发送邮箱验证码（purpose=register）。dev 回退明文 code 时自动回填输入框，方便联调。 */
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
                        // [修复防御]: dev 明文回退 —— 回填让联调不依赖邮箱收件;生产 SMTP 不返回 code 不受影响
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

    /**
     * [A2] 发送忘记密码验证码（purpose="forgotPassword"）。
     * 复用 [UserAuthRepository.sendVerificationCode]，dev 回退明文 code 自动回填。
     */
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
                        // [修复防御]: dev 明文回退 —— 回填让联调不依赖邮箱收件
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

    /**
     * [A2] 重置密码：调用 `POST /api/auth/forgotPassword`。
     * 成功后自动切回登录模式并预填邮箱，方便用户直接登录。
     */
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
            // [修复防御]: forgotPassword() 失败时直接抛异常，ViewModel 用 runCatching 兜底。
            // 不返回 Result<Unit> 回避 MockK 泛型擦除 —— coAnswers 返回 Result 时
            // r.fold() 触发 ClassCastException: Result cannot be cast to Unit。
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
                    Log.d(TAG, "resetPassword: success, switching to login")
                    // [修复防御]: 先保存邮箱，再切模式（switchToLogin 会清空 forgotEmail）
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

    /** 登录按钮是否可点（用于启用态校验，避免空表单误触）。 */
    fun canSubmitLogin(): Boolean = !isBusy && username.value.isNotBlank() && password.value.isNotBlank()

    /** [A2] 忘记密码按钮是否可点：邮箱合法 + 验证码非空 + 新密码 ≥ 8 + 两次一致 + 不忙。 */
    fun canSubmitForgotPassword(): Boolean {
        if (isBusy) return false
        if (!isValidEmail(forgotEmail.value)) return false
        if (forgotCode.value.isBlank()) return false
        if (forgotNewPassword.value.length < 8) return false
        if (forgotNewPasswordAgain.value != forgotNewPassword.value) return false
        return true
    }

    /**
     * 注册按钮是否可点：[Phase 2] 新增「邮箱必填 + 两次密码一致 + 按策略校验验证码」。
     * 策略未就绪（null）时不允许提交，防止验证码竞态。
     */
    fun canSubmitRegister(): Boolean {
        if (isBusy) return false
        val u = username.value
        if (u.length < 3 || u.length > 32) return false
        if (password.value.length < 8) return false
        // [Phase 2] 邮箱必填（新服务端 register 必需 email）
        if (!isValidEmail(email.value)) return false
        if (passwordAgain.value != password.value) return false
        val p = registerPolicy.value ?: return false
        if (!p.allowRegister) return false
        if (p.requireCaptcha && captchaInput.value.isBlank()) return false
        if (p.requireEmailCode && emailCodeInput.value.isBlank()) return false
        return true
    }

    /**
     * 邮箱格式校验。RFC 5322 完整正则太复杂,这里取简化版:
     * local@domain.tld —— local 段允许 A-Za-z0-9._%+-,
     * domain 段允许 A-Za-z0-9.-,tld 至少 2 个字母。
     */
    private fun isValidEmail(email: String): Boolean =
        Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(email)

    /**
     * Compose UI 调用的邮箱校验假入口 —— 与 [isValidEmail] 同语义,
     * 暴露为 public 让 UI 在非阻塞状态下显示 hint("邮箱格式不正确")。
     */
    fun isValidEmailForHint(email: String): Boolean = isValidEmail(email)

    fun signIn() {
        if (!canSubmitLogin()) {
            // [修复防御]: 双重防御 —— 按钮通常已经按 canSubmitLogin 禁用，
            // 这里再加一层拦截，避免键盘 enter 等绕过 UI 控件的事件触发。
            Log.w(TAG, "signIn: blocked by canSubmitLogin (username/pwd blank)")
            _state.value = AuthUiState.Error("用户名和密码不能为空")
            return
        }
        Log.d(TAG, "signIn: submit user=${SafeLog.user(username.value)}")
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            val r = userAuthRepository.login(username.value, password.value)
            _state.value = r.fold(
                onSuccess = {
                    Log.d(TAG, "signIn: repo success, transitioning to SignedIn")
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
            // [修复防御]: 与 signIn 相同 —— 按钮已被 canSubmitRegister 限制，
            // 但约束要在 VM 层兜底，避免 UI 状态被外部干扰。
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
            val r = userAuthRepository.register(
                username = u,
                email = email.value,
                password = password.value,
                passwordAgain = passwordAgain.value,
                captchaId = captchaId.value.takeIf { registerPolicy.value?.requireCaptcha == true },
                captchaCode = captchaInput.value.takeIf { registerPolicy.value?.requireCaptcha == true },
                emailCaptchaId = emailCaptchaId.value.takeIf { registerPolicy.value?.requireEmailCode == true },
                emailCode = emailCodeInput.value.takeIf { registerPolicy.value?.requireEmailCode == true },
            )
            _state.value = r.fold(
                onSuccess = {
                    Log.d(TAG, "register: repo success, transitioning to SignedIn")
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
