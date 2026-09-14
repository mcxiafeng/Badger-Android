package top.mcxiafeng.badger.pages.settings.account

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

class AccountSettingsViewModel : ViewModel() {

    private val userAuthRepository: UserAuthRepository = KoinComponentBy.get()
    private val serverApiFactory: ServerApiFactory = KoinComponentBy.get()
    private val serverUrlHolder: ServerUrlHolder = KoinComponentBy.get()
    private val userProfileRepository: UserProfileRepository = KoinComponentBy.get()

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    

    val profile: StateFlow<UserProfileCacheEntity?> =
        userProfileRepository.getUserProfile()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        BadgerLog.d(TAG, "AccountSettingsViewModel initialized")
        viewModelScope.launch { userAuthRepository.state.collect { refresh() } }
    }

    private fun snapshot(): AccountUiState {
        val authState = userAuthRepository.state.value
        return AccountUiState(
            username = AuthPrefs.readUsername(),
            
            role = if (AuthPrefs.readIsAdmin()) "管理员" else "普通用户",
            serverUrl = AuthPrefs.readServerUrl(),
            isLoggedIn = authState is AuthState.SignedIn,
        )
    }

    private fun refresh() {
        _state.value = snapshot()
    }

    
    suspend fun refreshAccountInfo(): Boolean {
        val ok = userProfileRepository.refreshFromServer()
        BadgerLog.d(TAG, "refreshAccountInfo: ok=$ok")
        return ok
    }

    
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
                BadgerLog.d(TAG, "updateName ok")
            }.onFailure {
                if (it is CancellationException) throw it
                BadgerLog.w(TAG, "updateName failed: ${it::class.simpleName}: ${it.message}")
            }
        }
    }

    
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
                BadgerLog.d(TAG, "updateBio ok")
            }.onFailure {
                if (it is CancellationException) throw it
                BadgerLog.w(TAG, "updateBio failed: ${it::class.simpleName}: ${it.message}")
            }
        }
    }

    

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
