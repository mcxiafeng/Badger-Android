package top.mcxiafeng.badger.pages.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.auth.AuthApi
import top.mcxiafeng.badger.network.core.ServerAddressStore
import top.mcxiafeng.badger.utils.SafeLog
import javax.inject.Inject

/**
 * 登录 / 注册 / 忘记密码三个表单共用一个 VM（activity 作用域，Auth 与 ForgotPassword
 * 两个路由解析到同一实例），所以进入页面时必须调 [onScreenEnter] 复位提交态。
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val addressStore: ServerAddressStore,
) : ViewModel() {

    private val _credentials = MutableStateFlow(AuthCredentials())
    val credentials: StateFlow<AuthCredentials> = _credentials.asStateFlow()

    private val _register = MutableStateFlow(RegisterFormState())
    val register: StateFlow<RegisterFormState> = _register.asStateFlow()

    private val _forgot = MutableStateFlow(ForgotFormState())
    val forgot: StateFlow<ForgotFormState> = _forgot.asStateFlow()

    private val _submitState = MutableStateFlow<AuthSubmitState>(AuthSubmitState.Idle)
    val submitState: StateFlow<AuthSubmitState> = _submitState.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    // —— 服务器连接（状态条展示） ——

    val serverUrl: StateFlow<String> = addressStore.activeUrl
    val isUrlVerified: StateFlow<Boolean> = addressStore.isUrlVerified

    private var submitJob: Job? = null

    /** 连通性探测 Job：换服务器地址时取消（旧地址的探测结果作废）。 */
    private var probeJob: Job? = null

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
        Log.d(TAG, "进入 auth 页面，复位提交态")
        submitJob?.cancel()
        submitJob = null
        _submitState.value = AuthSubmitState.Idle
    }

    // —— 服务器连通探测 ——

    /**
     * 探测当前激活地址是否可达。仅网络失败/DNS/超时算不可达；已验证或探测中时幂等跳过。
     * 连接地址管理页改地址会取消在途探测（旧地址结果作废）。
     */
    fun probeServerConnection() {
        if (_probing.value) return
        if (addressStore.isUrlVerified.value) return
        val url = addressStore.activeUrl.value
        Log.d(TAG, "probeServerConnection: probing ${SafeLog.url(url)}")
        _probing.value = true
        probeJob = viewModelScope.launch {
            try {
                val reachable = when (val result = authApi.probeBaseUrl(url)) {
                    is HttpResult.Success -> true
                    is HttpResult.Failure -> result.code > 0
                }
                Log.d(TAG, "probeServerConnection: ${SafeLog.url(url)} → reachable=$reachable")
                if (reachable) addressStore.markUrlVerified()
            } finally {
                _probing.value = false
            }
        }
    }

    /** 连接地址管理页改地址后调用：旧地址的探测结果作废，状态条回到警示/探测态。 */
    fun onServerAddressChanged() {
        probeJob?.cancel()
        probeJob = null
    }

    // —— 注册策略 ——

    /** 注册页首次可见时拉策略；幂等，失败后可重调重试。 */
    fun loadRegisterPolicy() {
        val current = _register.value
        if (current.policy != null || current.policyLoading) return
        _register.update { it.copy(policyLoading = true, policyFailed = false) }
        viewModelScope.launch {
            val policy = authApi.registerPolicy()
            Log.d(TAG, "注册策略：${policy ?: "加载失败"}")
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
            Log.w(TAG, "发验证码被拦：邮箱无效")
            _submitState.value = AuthSubmitState.Error("请先填写有效的邮箱")
            return
        }
        _forgot.update { it.copy(delivery = CodeDeliveryState(sending = true)) }
        viewModelScope.launch {
            when (val result = authApi.sendVerificationCode(email)) {
                is HttpResult.Success -> {
                    Log.d(TAG, "验证码已发往 ${SafeLog.email(email)}")
                    _forgot.update { it.copy(delivery = CodeDeliveryState(sent = true, hint = "验证码已发送到邮箱，请查收")) }
                }
                is HttpResult.Failure -> {
                    Log.w(TAG, "发验证码失败：HTTP ${result.code} ${result.errorType}")
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
        Log.d(TAG, "登录提交 user=${SafeLog.user(c.username)}")
        submit("登录", { authApi.login(c.username, c.password) }) {
            // 登录成功 = 当前 URL 验证通过 → 服务器状态条转「已连接」
            addressStore.markUrlVerified()
            _credentials.update { it.copy(password = "") }
            AuthSubmitState.Success(SuccessKind.LOGIN)
        }
    }

    /** 注册成功不产生登录态（服务端契约），切回登录页让用户再登录。 */
    fun register() {
        val c = _credentials.value
        val r = _register.value
        Log.d(TAG, "注册提交 user=${SafeLog.user(c.username)} email=${SafeLog.email(r.email)}")
        submit("注册", { authApi.register(c.username, r.email, c.password, r.passwordAgain) }) {
            // 注册请求本身可达服务端，同样算地址验证通过
            addressStore.markUrlVerified()
            _credentials.update { it.copy(password = "") }
            _register.update { it.copy(passwordAgain = "") }
            AuthSubmitState.Success(SuccessKind.REGISTER, "注册成功，请登录")
        }
    }

    fun resetPassword() {
        val f = _forgot.value
        Log.d(TAG, "重置密码提交 email=${SafeLog.email(f.email)}")
        submit("重置密码", { authApi.forgotPassword(f.email, f.code, f.newPassword, f.newPasswordAgain) }) {
            _forgot.value = ForgotFormState()
            AuthSubmitState.Success(SuccessKind.PASSWORD_RESET)
        }
    }

    private fun submit(label: String, call: suspend () -> HttpResult, onSuccess: () -> AuthSubmitState) {
        _submitState.value = AuthSubmitState.Loading
        submitJob = viewModelScope.launch {
            when (val result = call()) {
                is HttpResult.Success -> {
                    Log.d(TAG, "$label 成功")
                    _submitState.value = onSuccess()
                }
                is HttpResult.Failure -> {
                    Log.w(TAG, "$label 失败：HTTP ${result.code} ${result.errorType}")
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
