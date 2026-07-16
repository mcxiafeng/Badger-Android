package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import javax.inject.Inject

private const val TAG = "AccountSettings"

data class AccountUiState(
    val username: String?,
    val role: String?,
    val serverUrl: String,
    val isLoggedIn: Boolean,
    val isLoggingOut: Boolean = false,
)

/**
 * Backs [AccountAndBackupPage]. Reads the local account snapshot from
 * [AuthPrefs] and subscribes to [UserAuthRepository.state] so the UI
 * automatically reflects login / logout transitions. Note that this VM
 * does NOT expose the underlying repository — UI only sees [state] plus
 * two action methods ([updateServerUrl], [logout]).
 */
@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userAuthRepository: UserAuthRepository,
) : ViewModel() {

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
            role = AuthPrefs.readRole(context),
            serverUrl = AuthPrefs.readServerUrl(context),
            isLoggedIn = authState is AuthState.SignedIn,
        )
    }

    private fun refresh() {
        _state.value = snapshot()
    }

    /**
     * Persist a new Badger-Server base URL. Note that the running OkHttp
     * client keeps using the URL it was built with; a restart is required
     * for the change to take effect on the wire.
     */
    fun updateServerUrl(newUrl: String) {
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized.isBlank()) {
            Log.w(TAG, "updateServerUrl: blank input ignored")
            return
        }
        AuthPrefs.writeServerUrl(context, normalized)
        _state.value = _state.value.copy(serverUrl = normalized)
        Log.d(TAG, "Server URL updated to: $normalized (restart required)")
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