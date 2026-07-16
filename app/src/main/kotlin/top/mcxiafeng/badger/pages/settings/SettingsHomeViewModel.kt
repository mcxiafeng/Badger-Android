package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import javax.inject.Inject

private const val TAG = "SettingsHome"

data class SettingsHomeState(
    val username: String?,
    val isLoggedIn: Boolean,
    val serverUrl: String,
)

/**
 * Lightweight VM backing the top-level [SettingsPage]. Exposes the minimum
 * snapshot the home page needs (username + login state + server URL) so
 * Composable code never talks to Repository / SharedPreferences directly.
 */
@HiltViewModel
class SettingsHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userAuthRepository: UserAuthRepository,
) : ViewModel() {

    init {
        Log.d(TAG, "SettingsHomeViewModel initialized")
    }

    val state: StateFlow<SettingsHomeState> = userAuthRepository.state
        .map { auth ->
            SettingsHomeState(
                username = AuthPrefs.readUsername(context),
                isLoggedIn = auth is AuthState.SignedIn,
                serverUrl = AuthPrefs.readServerUrl(context),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsHomeState(
                username = AuthPrefs.readUsername(context),
                isLoggedIn = userAuthRepository.state.value is AuthState.SignedIn,
                serverUrl = AuthPrefs.readServerUrl(context),
            ),
        )
}