package top.mcxiafeng.badger.pages.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.network.UserSettings
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.pages.settings.components.SettingsUiMessage
import top.mcxiafeng.badger.pages.settings.components.postError
import top.mcxiafeng.badger.pages.settings.components.postInfo
import top.mcxiafeng.badger.ui.navigation.ThemeConfig
import top.mcxiafeng.badger.ui.navigation.ThemeMode
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "UserSettingsVM"

@Immutable
sealed interface UserSettingsUiState {
    data object Loading : UserSettingsUiState
    data class Success(
        val settings: UserSettings,
        val isLoggedIn: Boolean,
        val saving: Boolean = false,
        val shortLinkEnabled: Boolean = false,
    ) : UserSettingsUiState
    data class Error(val message: String) : UserSettingsUiState
}

class UserSettingsViewModel(
    private val dispatcher: CoroutineDispatcher = BadgerDispatchers.io,
) : ViewModel() {

    private val serverApiFactory: ServerApiFactory = KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = KoinComponentBy.get()

    private val _state = MutableStateFlow<UserSettingsUiState>(UserSettingsUiState.Loading)
    val state = _state.asStateFlow()

    private val _messages = Channel<SettingsUiMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsUiMessage> = _messages.receiveAsFlow()

    init {
        BadgerLog.d(TAG, "UserSettingsViewModel initialized")
        viewModelScope.launch {
            userAuthRepository.state.collect { auth ->
                if (auth is AuthState.SignedIn) load() else {
                    _state.value = UserSettingsUiState.Success(
                        settings = UserSettings(),
                        isLoggedIn = false,
                    )
                }
            }
        }
    }

    
    fun load() {
        val showLoading = _state.value !is UserSettingsUiState.Success
        refreshInternal(showLoading)
    }

    private fun refreshInternal(showLoading: Boolean) {
        if (showLoading) _state.value = UserSettingsUiState.Loading
        viewModelScope.launch {
            
            runCatching { withContext(dispatcher) { serverApiFactory.get().getUserSettings() } }
                .onSuccess { settings ->
                    _state.value = UserSettingsUiState.Success(
                        settings,
                        isLoggedIn = true,
                        shortLinkEnabled = ShortLinkService.isEnabled(),
                    )
                    BadgerLog.d(TAG, "load ok: lang=${settings.language} theme=${settings.theme}")
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    BadgerLog.e(TAG, "load failed", e)
                    val current = _state.value
                    if (current is UserSettingsUiState.Loading) {
                        _state.value = UserSettingsUiState.Error(e.message ?: "加载失败")
                    } else {
                        _messages.postError(TAG, "刷新失败", e)
                    }
                }
        }
    }

    fun updateLanguage(lang: String) {
        withSaving("语言已更新") { serverApiFactory.get().updateUserSettings(language = lang) }
    }

    
    fun updateTheme(serverTheme: String) {
        mapServerThemeToLocal(serverTheme)?.let { ThemeConfig.saveThemeMode(it) }
        withSaving("主题已更新") { serverApiFactory.get().updateUserSettings(theme = serverTheme) }
    }

    fun updateNotifyEmail(enabled: Boolean) = withSaving {
        serverApiFactory.get().updateUserSettings(notifyEmail = enabled)
    }

    fun updateShortLinkProvider(provider: String) = withSaving {
        serverApiFactory.get().updateUserSettings(shortLinkProvider = provider)
    }

    
    fun setShortLinkEnabled(v: Boolean) {
        ShortLinkService.setEnabled(v)
        val current = _state.value
        if (current is UserSettingsUiState.Success) {
            _state.value = current.copy(shortLinkEnabled = v)
        }
        BadgerLog.d(TAG, "短链服务开关: $v")
    }

    
    fun updateShortioApiKey(key: String) {
        if (key.isBlank()) return
        withSaving("API Key 已更新") { serverApiFactory.get().updateUserSettings(shortioApiKey = key) }
    }

    fun clearShortioApiKey() = withSaving {
        serverApiFactory.get().updateUserSettings(clearShortioApiKey = true)
    }

    private fun withSaving(successMsg: String? = null, block: suspend () -> Unit) {
        val current = _state.value
        if (current is UserSettingsUiState.Success) {
            _state.value = current.copy(saving = true)
        }
        viewModelScope.launch {
            
            runCatching { withContext(dispatcher) { block() } }
                .onSuccess {
                    BadgerLog.d(TAG, "update ok")
                    successMsg?.let { _messages.postInfo(it) }
                    refreshInternal(showLoading = false) 
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    BadgerLog.e(TAG, "update failed", e)
                    _messages.postError(TAG, "更新失败", e)
                    if (current is UserSettingsUiState.Success) {
                        _state.value = current.copy(saving = false)
                    }
                }
        }
    }

    
    private fun mapServerThemeToLocal(serverTheme: String): ThemeMode? = when (serverTheme.lowercase()) {
        "system" -> ThemeMode.SYSTEM
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> null
    }
}
