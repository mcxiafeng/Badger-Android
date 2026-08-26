package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.data.isServerUrlConfigured
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.setServerUrlConfigured

private const val TAG = "AccountSettings"

data class AccountUiState(
    val username: String?,
    val role: String?,
    val serverUrl: String,
    val isLoggedIn: Boolean,
    val isLoggingOut: Boolean = false,
)

/**
 * Backs [AccountProfilePage]. Reads the local account snapshot from
 * [AuthPrefs] and subscribes to [UserAuthRepository.state] so the UI
 * automatically reflects login / logout transitions. Note that this VM
 * does NOT expose the underlying repository — UI only sees [state] plus
 * two action methods ([updateServerUrl], [logout]).
 */
class AccountSettingsViewModel : ViewModel() {

    private val context: Context = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val serverApiFactory: ServerApiFactory = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val serverUrlHolder: ServerUrlHolder = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        Log.d(TAG, "AccountSettingsViewModel initialized")
        // 订阅 auth state 以在登录/登出后自动刷新
        viewModelScope.launch {
            userAuthRepository.state.collect { refresh() }
        }
    }

    private fun snapshot(): AccountUiState {
        val authState = userAuthRepository.state.value
        return AccountUiState(
            username = AuthPrefs.readUsername(context),
            // [Phase 2] 新契约没有 role 字符串,只有 isAdmin 布尔 —— 由 VM 派生展示文案,
            // 保持 AccountProfilePage 的 `role ?: "普通用户"` 契约不变。
            role = if (AuthPrefs.readIsAdmin(context)) "管理员" else "普通用户",
            serverUrl = AuthPrefs.readServerUrl(context),
            isLoggedIn = authState is AuthState.SignedIn,
        )
    }

    private fun refresh() {
        _state.value = snapshot()
    }

    /**
     * Persist a new Badger-Server base URL. The shared [ServerApi] picks
     * up the change immediately via [ServerApiFactory.updateBaseUrl], so
     * subsequent requests route to the new host without restarting the
     * process.
     *
     * Order matters:
     * 1. write prefs first (kill-safe)
     * 2. broadcast to [ServerUrlHolder] (UI 立刻刷新所有订阅者)
     * 3. push to factory (ServerApi 热更 baseUrl)
     *
     * If the process is killed between any of the first two, the next
     * launch re-reads the new URL from prefs on its own.
     */
    fun updateServerUrl(newUrl: String) {
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized.isBlank()) {
            Log.w(TAG, "updateServerUrl: blank input ignored")
            return
        }
        serverUrlHolder.set(normalized)        // 1+2:写 prefs + 广播
        serverApiFactory.updateBaseUrl(normalized)  // 3:ServerApi 热更
        // [V2-E2E #1] 标记 server URL 已用户主动配置 — 启动期不再弹"未配置"提示。
        setServerUrlConfigured(context, true)
        _state.value = _state.value.copy(serverUrl = normalized)  // 本 VM state 同步
        Log.d(TAG, "Server URL updated to: $normalized (hot-applied + UI broadcasted)")
    }

    fun logout() {
        if (_state.value.isLoggingOut) return
        _state.value = _state.value.copy(isLoggingOut = true)
        Log.d(TAG, "logout: requesting UserAuthRepository.logout()")
        viewModelScope.launch {
            userAuthRepository.logout()
            Log.d(TAG, "logout: completed, authState=${userAuthRepository.state.value}")
            _state.value = _state.value.copy(isLoggingOut = false)
        }
    }
}
