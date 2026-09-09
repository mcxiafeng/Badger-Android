package top.mcxiafeng.badger.pages.settings

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

/** 用户设置 UI 状态。 */
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

/**
 * 用户设置 VM（新实现）。
 *
 * 加载服务端 [UserSettings]（语言/主题/通知邮件/短链配置），编辑后写回
 * `POST /api/user/settings`。主题变更同时写穿本地 [ThemeConfig]（立即生效 + 云端留存）。
 * short.io API Key 为 write-only：服务端只回 `shortioApiKeySet` 布尔，输入空白=保留，显式清除走 clear。
 */
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

    /** 首次/重登加载：无快照时显示 Loading；已有快照则静默刷新不闪转圈。 */
    fun load() {
        val showLoading = _state.value !is UserSettingsUiState.Success
        refreshInternal(showLoading)
    }

    private fun refreshInternal(showLoading: Boolean) {
        if (showLoading) _state.value = UserSettingsUiState.Loading
        viewModelScope.launch {
            // 传输层为同步阻塞实现（OkHttp 无内部调度），必须在 IO 线程执行
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

    /** 主题写穿：本地 ThemeConfig 立即生效 + 云端 POST 留存。 */
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

    /** 短链服务总开关（本地偏好：NFC 写入是否使用短链接；不影响云端 provider 选择）。 */
    fun setShortLinkEnabled(v: Boolean) {
        ShortLinkService.setEnabled(v)
        val current = _state.value
        if (current is UserSettingsUiState.Success) {
            _state.value = current.copy(shortLinkEnabled = v)
        }
        BadgerLog.d(TAG, "短链服务开关: $v")
    }

    /** 写入 short.io API Key（空白=保留已存，非空=更新）。 */
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
            // 同 refreshInternal：网络必须离开主线程
            runCatching { withContext(dispatcher) { block() } }
                .onSuccess {
                    BadgerLog.d(TAG, "update ok")
                    successMsg?.let { _messages.postInfo(it) }
                    refreshInternal(showLoading = false) // 静默重拉确认，不闪 Loading
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

    /** 服务端 theme 字符串 → 本地 ThemeMode（仅 system/light/dark 映射；未知值不应用本地）。 */
    private fun mapServerThemeToLocal(serverTheme: String): ThemeMode? = when (serverTheme.lowercase()) {
        "system" -> ThemeMode.SYSTEM
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> null
    }
}
