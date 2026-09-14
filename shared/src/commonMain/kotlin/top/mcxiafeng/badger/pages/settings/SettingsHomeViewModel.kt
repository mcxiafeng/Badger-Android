package top.mcxiafeng.badger.pages.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "SettingsHome"

@Immutable
data class SettingsHomeState(
    val username: String?,
    val isLoggedIn: Boolean,
    val serverUrl: String,
    
    val pendingHint: String,
    
    val unreadCount: Int = 0,
    
    val profileAvatarPath: String? = null,
)

class SettingsHomeViewModel : ViewModel() {

    private val userAuthRepository: UserAuthRepository = KoinComponentBy.get()
    private val userProfileRepository: UserProfileRepository = KoinComponentBy.get()
    private val serverUrlHolder: ServerUrlHolder = KoinComponentBy.get()
    private val syncStatusRepository: SyncStatusRepository = KoinComponentBy.get()
    private val notificationRepository: NotificationRepository = KoinComponentBy.get()

    init {
        BadgerLog.d(TAG, "SettingsHomeViewModel initialized")
    }

    val state: StateFlow<SettingsHomeState> = combine(
        userAuthRepository.state,
        userProfileRepository.getUserProfile(),
        serverUrlHolder.url,
        pendingHintFlow(),
        notificationRepository.unreadCount,
    ) { auth, profile, url, pendingHint, unread ->
        SettingsHomeState(
            username = profile?.name?.takeIf { it.isNotBlank() } ?: AuthPrefs.readUsername(),
            isLoggedIn = auth is AuthState.SignedIn,
            serverUrl = url,
            pendingHint = pendingHint,
            unreadCount = unread.coerceAtLeast(0),
            profileAvatarPath = profile?.avatarPath,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsHomeState(
            username = AuthPrefs.readUsername(),
            isLoggedIn = userAuthRepository.state.value is AuthState.SignedIn,
            serverUrl = serverUrlHolder.url.value,
            pendingHint = DEFAULT_PENDING_HINT,
            unreadCount = notificationRepository.unreadCount.value.coerceAtLeast(0),
            profileAvatarPath = null,
        ),
    )

    

    private fun pendingHintFlow() = flow {
        val hint = runCatching {
            val s = syncStatusRepository.snapshot()
            when {
                s.unsyncedCount > 0 -> "${s.unsyncedCount} 个联系人未同步"
                s.lastSyncVersion > 0 -> "同步正常"
                else -> DEFAULT_PENDING_HINT
            }
        }.getOrElse {
            BadgerLog.w(TAG, "pendingHintFlow: 读 snapshot 失败,fallback", it)
            DEFAULT_PENDING_HINT
        }
        emit(hint)
    }

    companion object {
        private const val DEFAULT_PENDING_HINT = "同步状态"
    }
}
