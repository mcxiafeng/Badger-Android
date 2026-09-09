package top.mcxiafeng.badger.pages.settings.account

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.data.prefs.isServerUrlConfigured
import top.mcxiafeng.badger.data.prefs.setServerUrlConfigured
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "AccountSettings"

@Immutable
data class AccountUiState(
    val username: String?,
    val role: String?,
    val serverUrl: String,
    val isLoggedIn: Boolean,
    val isLoggingOut: Boolean = false,
)

/**
 * 账号设置 VM（重写）。
 *
 * 取代原先借用 `person.contact.UserProfileDetailViewModel` 取 repository 的异味：
 * 昵称 / 简介的 read-modify-write 全部上移到本 VM，UI 只看 [profile] + 调
 * [updateName] / [updateBio]，Composable 不再做 DB IO。
 *
 * 仍订阅 [UserAuthRepository.state] 以在登录/登出后刷新 [state]；
 * [updateServerUrl] / [logout] 逻辑不变（写 prefs → 广播 → 热更 ServerApi）。
 */
class AccountSettingsViewModel : ViewModel() {

    private val userAuthRepository: UserAuthRepository = KoinComponentBy.get()
    private val serverApiFactory: ServerApiFactory = KoinComponentBy.get()
    private val serverUrlHolder: ServerUrlHolder = KoinComponentBy.get()
    private val userProfileRepository: UserProfileRepository = KoinComponentBy.get()

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    private val _profile = MutableStateFlow<UserProfileCacheEntity?>(null)
    val profile: StateFlow<UserProfileCacheEntity?> = _profile.asStateFlow()

    init {
        BadgerLog.d(TAG, "AccountSettingsViewModel initialized")
        viewModelScope.launch { userAuthRepository.state.collect { refresh() } }
        viewModelScope.launch { loadProfile() }
    }

    private suspend fun loadProfile() {
        runCatching { userProfileRepository.getUserProfileOnce() }
            .onSuccess { _profile.value = it }
            .onFailure {
                if (it is CancellationException) throw it
                BadgerLog.w(TAG, "loadProfile failed: ${it::class.simpleName}: ${it.message}")
            }
    }

    private fun snapshot(): AccountUiState {
        val authState = userAuthRepository.state.value
        return AccountUiState(
            username = AuthPrefs.readUsername(),
            // [Phase 2] 新契约只有 isAdmin 布尔，由 VM 派生展示文案。
            role = if (AuthPrefs.readIsAdmin()) "管理员" else "普通用户",
            serverUrl = AuthPrefs.readServerUrl(),
            isLoggedIn = authState is AuthState.SignedIn,
        )
    }

    private fun refresh() {
        _state.value = snapshot()
    }

    /** 修改昵称：read-modify-write，保存后回填 [profile]。空白回退"用户"。 */
    fun updateName(newName: String) {
        val normalized = newName.trim().ifBlank { "用户" }
        viewModelScope.launch {
            runCatching {
                val current = userProfileRepository.getUserProfileOnce()
                    ?: UserProfileCacheEntity(name = "用户", updateTime = nowMs())
                userProfileRepository.saveUserProfile(
                    current.copy(name = normalized, updateTime = nowMs())
                )
            }.onSuccess {
                _profile.value = userProfileRepository.getUserProfileOnce()
                BadgerLog.d(TAG, "updateName ok")
            }.onFailure {
                if (it is CancellationException) throw it
                BadgerLog.w(TAG, "updateName failed: ${it::class.simpleName}: ${it.message}")
            }
        }
    }

    /** 修改简介：空白存 null（与原逻辑一致）。 */
    fun updateBio(newBio: String?) {
        val normalized = newBio?.trim()?.ifBlank { null }
        viewModelScope.launch {
            runCatching {
                val current = userProfileRepository.getUserProfileOnce()
                    ?: UserProfileCacheEntity(name = "用户", updateTime = nowMs())
                userProfileRepository.saveUserProfile(
                    current.copy(bio = normalized, updateTime = nowMs())
                )
            }.onSuccess {
                _profile.value = userProfileRepository.getUserProfileOnce()
                BadgerLog.d(TAG, "updateBio ok")
            }.onFailure {
                if (it is CancellationException) throw it
                BadgerLog.w(TAG, "updateBio failed: ${it::class.simpleName}: ${it.message}")
            }
        }
    }

    /**
     * 持久化新的 Badger-Server base URL：写 prefs → 广播 ServerUrlHolder → 热更 ServerApi。
     * kill-safe：进程在任一步骤后被杀，下次启动从 prefs 自读新 URL。
     */
    fun updateServerUrl(newUrl: String) {
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized.isBlank()) {
            BadgerLog.w(TAG, "updateServerUrl: blank input ignored")
            return
        }
        serverUrlHolder.set(normalized)
        serverApiFactory.updateBaseUrl(normalized)
        setServerUrlConfigured(true)
        _state.value = _state.value.copy(serverUrl = normalized)
        BadgerLog.d(TAG, "Server URL updated (hot-applied + UI broadcasted)")
    }

    fun logout() {
        if (_state.value.isLoggingOut) return
        _state.value = _state.value.copy(isLoggingOut = true)
        BadgerLog.d(TAG, "logout: requesting UserAuthRepository.logout()")
        viewModelScope.launch {
            userAuthRepository.logout()
            BadgerLog.d(TAG, "logout: completed, authState=${userAuthRepository.state.value}")
            _state.value = _state.value.copy(isLoggingOut = false)
        }
    }
}
