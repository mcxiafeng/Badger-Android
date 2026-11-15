package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import top.mcxiafeng.badger.network.auth.AuthSession
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import javax.inject.Inject

/**
 * 设置页账号区展示状态（QQ 式「头像 + 昵称」头部）：
 * 未登录 → 「未登录」+ 默认人形头像；已登录 → 用户名 + 首字母头像。
 */
data class SettingsAccountState(
    val loggedIn: Boolean,
    val displayName: String,
    val subtitle: String,
    /** 头像首字母；null = 展示默认人形头像（未登录，或旧会话缺用户名）。 */
    val avatarInitial: String?,
) {
    companion object {
        private const val LABEL_NOT_LOGGED_IN = "未登录"
        private const val LABEL_LOGGED_IN = "已登录"
        private const val SUBTITLE_NOT_LOGGED_IN = "点击登录，同步云端数据"
        private const val SUBTITLE_LOGGED_IN = "已登录"
        private const val SUBTITLE_NO_USERNAME = "Badger 账号"

        fun from(session: AuthSession?): SettingsAccountState = when {
            session == null -> SettingsAccountState(
                loggedIn = false,
                displayName = LABEL_NOT_LOGGED_IN,
                subtitle = SUBTITLE_NOT_LOGGED_IN,
                avatarInitial = null,
            )
            // 旧版本写入的会话没有用户名：不拿 uuid 当昵称，回退为「已登录」+ 默认头像
            session.username.isBlank() -> SettingsAccountState(
                loggedIn = true,
                displayName = LABEL_LOGGED_IN,
                subtitle = SUBTITLE_NO_USERNAME,
                avatarInitial = null,
            )
            else -> SettingsAccountState(
                loggedIn = true,
                displayName = session.username,
                subtitle = SUBTITLE_LOGGED_IN,
                avatarInitial = session.username.take(1),
            )
        }
    }
}

/** 账号区状态由 [AuthSessionStore] 会话流驱动：登录/登出后设置页即时刷新。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionStore: AuthSessionStore,
) : ViewModel() {

    val accountState: StateFlow<SettingsAccountState> = sessionStore.sessionFlow
        .map { session ->
            Log.d(TAG, "账号区状态刷新：loggedIn=${session != null}")
            SettingsAccountState.from(session)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsAccountState.from(null))

    companion object {
        private const val TAG = "SettingsViewModelTester"
    }
}
