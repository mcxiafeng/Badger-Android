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
import top.mcxiafeng.badger.utils.SafeLog

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
 * [§14.2] 移除 `@HiltViewModel` 与 `@Inject` —— Koin 通过 `inject()` 字段注入
 * [UserAuthRepository]。`viewModel { ... }` 模式不适用,因为这个 VM 通过
 * `koinViewModel(key = ...)` 直接从 Koin 拿实例(参见 AuthScreens.kt)。
 */
class AuthViewModel : ViewModel() {

    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    val username: MutableState<String> = mutableStateOf("")
    val email: MutableState<String> = mutableStateOf("")
    val password: MutableState<String> = mutableStateOf("")

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val onUsername: (String) -> Unit = { raw ->
        // [修复输入清洗] 过滤控制字符(\n / \r / \t 等)防止 IME 边界 / 粘贴异常
        // 把多行内容塞进单行字段,同时 trim 掉首尾空白。
        // [V2-UX]: 用户名限定 ASCII 可见字符 (0x21..0x7E)。允许 ASCII 字母数字 + _-. 标点,
        // 不允许中文 / 空格 / 其他 unicode 字符。与服务端 [a-zA-Z0-9_-]{3,32} 契约对齐,
        // 避免「用户输入了中文注册按钮一直 disabled」这种迷惑态。
        // Kotlin 1.9 之前 Char.isAscii() 还没稳定,这里用 code 范围代替。
        username.value = raw.filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }.trim()
    }
    val onEmail: (String) -> Unit = { raw ->
        // [修复输入清洗] + [V2-UX]: 邮箱只允许 ASCII 可见字符(字母数字 + @._%-),
        // 不允许中文 / 空格 / 控制字符。trim 是 no-op(空格已过滤),
        // 保留以防 IME 注入首尾空白。
        email.value = raw.filterNot { it.isISOControl() || it.isWhitespace() || it.code !in 0x21..0x7E }.trim()
    }
    val onPassword: (String) -> Unit = { raw ->
        // [修复输入清洗] 控制字符一律过滤 (\n / \r / \t 永远不是合法密码字符)。
        // [V2-UX]: 与 username / email 一致,密码也限制 ASCII 可见字符 (0x21..0x7E),
        // 避免中文 / emoji / 特殊 unicode 进入密码字段导致服务端 hash 校验不一致
        // 或 IME 边界对齐错位。空格一同禁掉 —— 之前留给密码策略的"首尾空格"在
        // 移动端 IME 下几乎不会被主动使用,但会跟复制粘贴、跨设备同步产生歧义。
        password.value = raw.filterNot { it.isISOControl() || it.code !in 0x21..0x7E }
    }

    /** 加载中 / 已登录都视为"忙"，调用方据此禁用按钮与输入。 */
    val isBusy: Boolean
        get() = _state.value is AuthUiState.Loading || _state.value is AuthUiState.SignedIn

    fun reset() {
        Log.d(TAG, "reset() — clearing inputs and state")
        username.value = ""
        email.value = ""
        password.value = ""
        _state.value = AuthUiState.Idle
    }

    /** 切换到登录模式：保留 username 与 password，但清掉 error。 */
    fun switchToLogin() {
        Log.d(TAG, "switchToLogin()")
        email.value = ""
        _state.value = AuthUiState.Idle
    }

    /** 切换到注册模式：保留 username 与 password，但清掉 error。 */
    fun switchToRegister() {
        Log.d(TAG, "switchToRegister()")
        _state.value = AuthUiState.Idle
    }

    /** 登录按钮是否可点（用于启用态校验，避免空表单误触）。 */
    fun canSubmitLogin(): Boolean = !isBusy && username.value.isNotBlank() && password.value.isNotBlank()

    /** 注册按钮是否可点：用户名 3-32、密码 >=8、邮箱(若填了)合法。 */
    fun canSubmitRegister(): Boolean {
        if (isBusy) return false
        val u = username.value
        if (u.length < 3 || u.length > 32) return false
        if (password.value.length < 8) return false
        // [V2-UX]: 邮箱是可选字段,空字符串视为未填直接放行;
        // 非空就必须符合 RFC 5322 简化正则,否则用户看到禁用按钮但无反馈。
        val e = email.value
        if (e.isNotEmpty() && !isValidEmail(e)) return false
        return true
    }

    /**
     * 邮箱格式校验。RFC 5322 完整正则太复杂,这里取简化版:
     * local@domain.tld —— local 段允许 A-Za-z0-9._%+-,
     * domain 段允许 A-Za-z0-9.-,tld 至少 2 个字母。
     *
     * 服务端会再校验一次,客户端只做"看着就在"的过滤。
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
            // 但密码长度等约束要在 VM 层兜底，避免 UI 状态被外部干扰。
            val u = username.value
            val e = email.value
            val msg = when {
                u.length < 3 || u.length > 32 -> "用户名长度需 3-32 字符"
                password.value.length < 8 -> "密码至少 8 位"
                e.isNotEmpty() && !isValidEmail(e) -> "邮箱格式不正确"
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
                password = password.value,
                email = email.value.takeIf { it.isNotBlank() },
                displayName = null,
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
