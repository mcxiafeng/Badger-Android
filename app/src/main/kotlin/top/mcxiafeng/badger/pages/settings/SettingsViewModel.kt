package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.auth.AuthSession
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import javax.inject.Inject

/**
 * 设置页账号区展示状态（QQ 式「头像 + 昵称」头部）：
 * 本地用户与云端身份分别展示；会话到期不改变资料所属空间。
 */
@Immutable
data class SettingsAccountState(
    val loggedIn: Boolean,
    val displayName: String,
    val subtitle: String,
    /** 头像首字母；null = 展示默认人形头像（未登录，或旧会话缺用户名）。 */
    val avatarInitial: String?,
) {
    companion object {
        private const val LABEL_NOT_LOGGED_IN = "本地用户"
        private const val LABEL_LOGGED_IN = "已登录"
        private const val SUBTITLE_NOT_LOGGED_IN = "资料保存在本机"
        private const val SUBTITLE_LOGGED_IN = "已登录"
        private const val SUBTITLE_NO_USERNAME = "Badger 账号"

        fun from(session: AuthSession?, scope: AccountScope): SettingsAccountState {
            if (scope.isLocal) return from(null)
            val authenticated = session != null && session.serverId == scope.serverId && session.userUuid == scope.userId && session.dataEpoch == scope.dataEpoch
            val name = scope.username.ifBlank { SUBTITLE_NO_USERNAME }
            return SettingsAccountState(authenticated, name,
                if (authenticated) SUBTITLE_LOGGED_IN else "需要重新登录，资料仍保存在本机", name.take(1))
        }

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
    private val accounts: AccountStore,
) : ViewModel() {

    val accountState: StateFlow<SettingsAccountState> = combine(sessionStore.sessionFlow, accounts.scopeFlow) { session, scope ->
            Log.d(TAG, "账号区状态刷新：local=${scope.isLocal}")
            SettingsAccountState.from(session, scope)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsAccountState.from(sessionStore.read(), accounts.current()))

    companion object {
        private const val TAG = "SettingsViewModelTester"
    }
}
