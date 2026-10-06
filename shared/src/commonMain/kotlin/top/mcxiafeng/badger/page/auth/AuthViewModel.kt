package top.mcxiafeng.badger.page.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.network.auth.AuthApi
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.SafeLog

/**
 * 登录 / 注册 / 忘记密码三个表单共用一个 VM：Auth 与 ForgotPassword 两个路由可能解析到同一
 * 实例（viewModel 落到同一 Owner），所以进入页面时必须调 [onScreenEnter] 复位提交态。
 */
class AuthViewModel : ViewModel() {

    private val _credentials = MutableStateFlow(AuthCredentials())
    val credentials: StateFlow<AuthCredentials> = _credentials.asStateFlow()

    private val _register = MutableStateFlow(RegisterFormState())
    val register: StateFlow<RegisterFormState> = _register.asStateFlow()

    private val _forgot = MutableStateFlow(ForgotFormState())
    val forgot: StateFlow<ForgotFormState> = _forgot.asStateFlow()

    private val _submitState = MutableStateFlow<AuthSubmitState>(AuthSubmitState.Idle)
    val submitState: StateFlow<AuthSubmitState> = _submitState.asStateFlow()

    private var submitJob: Job? = null

    // —— 表单输入（过滤规则见 AuthValidator） ——

    fun onUsername(raw: String) = _credentials.update { it.copy(username = AuthValidator.sanitizeIdentity(raw)) }

    fun onPassword(raw: String) = _credentials.update { it.copy(password = AuthValidator.sanitizePassword(raw)) }

    fun onEmail(raw: String) = _register.update { it.copy(email = AuthValidator.sanitizeIdentity(raw)) }

    fun onPasswordAgain(raw: String) = _register.update { it.copy(passwordAgain = AuthValidator.sanitizePassword(raw)) }

    fun onForgotEmail(raw: String) = _forgot.update {
        val email = AuthValidator.sanitizeIdentity(raw)
        // 邮箱变更后旧验证码作废，需重新发送
        if (email != it.email) it.copy(email = email, code = "", delivery = CodeDeliveryState())
        else it.copy(email = email)
    }

    fun onForgotCode(raw: String) = _forgot.update { it.copy(code = AuthValidator.sanitizeCode(raw)) }

    fun onForgotNewPassword(raw: String) = _forgot.update { it.copy(newPassword = AuthValidator.sanitizePassword(raw)) }

    fun onForgotNewPasswordAgain(raw: String) = _forgot.update { it.copy(newPasswordAgain = AuthValidator.sanitizePassword(raw)) }

    // —— 生命周期 ——

    fun onScreenEnter() {
        BadgerLog.d(TAG, "进入 auth 页面，复位提交态")
        submitJob?.cancel()
        submitJob = null
        _submitState.value = AuthSubmitState.Idle
    }

    // —— 注册策略 ——

    /** 注册页首次可见时拉策略；幂等，失败后可重调重试。 */
    fun loadRegisterPolicy() {
        val current = _register.value
        if (current.policy != null || current.policyLoading) return
        _register.update { it.copy(policyLoading = true, policyFailed = false) }
        viewModelScope.launch {
            val policy = AuthApi.registerPolicy()
            BadgerLog.d(TAG, "注册策略：${policy ?: "加载失败"}")
            _register.update {
                if (policy == null) it.copy(policyLoading = false, policyFailed = true)
                else it.copy(policy = policy, policyLoading = false, policyFailed = false)
            }
        }
    }

    // —— 忘记密码验证码 ——

    fun sendForgotCode() {
        val email = _forgot.value.email
        if (!AuthValidator.isValidEmail(email)) {
            BadgerLog.w(TAG, "发验证码被拦：邮箱无效")
            _submitState.value = AuthSubmitState.Error("请先填写有效的邮箱")
            return
        }
        _forgot.update { it.copy(delivery = CodeDeliveryState(sending = true)) }
        viewModelScope.launch {
            when (val result = AuthApi.sendVerificationCode(email)) {
                is HttpResult.Success -> {
                    BadgerLog.d(TAG, "验证码已发往 ${SafeLog.email(email)}")
                    _forgot.update { it.copy(delivery = CodeDeliveryState(sent = true, hint = "验证码已发送到邮箱，请查收")) }
                }
                is HttpResult.Failure -> {
                    BadgerLog.w(TAG, "发验证码失败：HTTP ${result.code} ${result.errorType}")
                    _forgot.update { it.copy(delivery = CodeDeliveryState()) }
                    _submitState.value = AuthSubmitState.Error(result.userMessage())
                }
            }
        }
    }

    // —— 提交（UI 置灰走 canSubmitXxx，这里不再重复校验） ——

    fun canSubmitLogin(): Boolean =
        submitState.value !is AuthSubmitState.Loading &&
            AuthValidator.loginBlockReason(_credentials.value.username, _credentials.value.password) == null

    fun canSubmitRegister(): Boolean {
        if (submitState.value is AuthSubmitState.Loading) return false
        val c = _credentials.value
        val r = _register.value
        return AuthValidator.registerBlockReason(c.username, r.email, c.password, r.passwordAgain, r.policy) == null
    }

    fun canSubmitForgotPassword(): Boolean {
        if (submitState.value is AuthSubmitState.Loading) return false
        val f = _forgot.value
        return AuthValidator.forgotBlockReason(f.email, f.code, f.newPassword, f.newPasswordAgain) == null
    }

    fun signIn() {
        val c = _credentials.value
        BadgerLog.d(TAG, "登录提交 user=${SafeLog.user(c.username)}")
        submit("登录", { AuthApi.login(c.username, c.password) }) {
            _credentials.update { it.copy(password = "") }
            AuthSubmitState.Success(SuccessKind.LOGIN)
        }
    }

    /** 注册成功不产生登录态（服务端契约），切回登录页让用户再登录。 */
    fun register() {
        val c = _credentials.value
        val r = _register.value
        BadgerLog.d(TAG, "注册提交 user=${SafeLog.user(c.username)} email=${SafeLog.email(r.email)}")
        submit("注册", {
            AuthApi.register(c.username, r.email, c.password, r.passwordAgain)
        }) {
            _credentials.update { it.copy(password = "") }
            _register.update { it.copy(passwordAgain = "") }
            AuthSubmitState.Success(SuccessKind.REGISTER, "注册成功，请登录")
        }
    }

    fun resetPassword() {
        val f = _forgot.value
        BadgerLog.d(TAG, "重置密码提交 email=${SafeLog.email(f.email)}")
        submit("重置密码", { AuthApi.forgotPassword(f.email, f.code, f.newPassword, f.newPasswordAgain) }) {
            _forgot.value = ForgotFormState()
            AuthSubmitState.Success(SuccessKind.PASSWORD_RESET)
        }
    }

    private fun submit(label: String, call: suspend () -> HttpResult, onSuccess: () -> AuthSubmitState) {
        _submitState.value = AuthSubmitState.Loading
        submitJob = viewModelScope.launch {
            when (val result = call()) {
                is HttpResult.Success -> {
                    BadgerLog.d(TAG, "$label 成功")
                    _submitState.value = onSuccess()
                }
                is HttpResult.Failure -> {
                    BadgerLog.w(TAG, "$label 失败：HTTP ${result.code} ${result.errorType}")
                    _submitState.value = AuthSubmitState.Error(result.userMessage())
                }
            }
        }
    }

    /** 服务端错误体契约未定稿，只按错误类型映射用户文案。 */
    private fun HttpResult.Failure.userMessage(): String = when (errorType) {
        HttpResult.ErrorType.NETWORK -> "网络连接失败，请检查网络"
        HttpResult.ErrorType.TIMEOUT -> "连接超时，请重试"
        HttpResult.ErrorType.RATE_LIMIT -> "操作过于频繁，请稍后再试"
        HttpResult.ErrorType.SERVER -> "服务器错误（HTTP $code）$message"
        else -> "请求失败（HTTP $code）"
    }

    companion object {
        private const val TAG = "AuthViewModelTester"
    }
}
