package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.util.Log
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

private const val TAG = "SettingsHome"

data class SettingsHomeState(
    val username: String?,
    val isLoggedIn: Boolean,
    val serverUrl: String,
    /** [V2-P9] 设置主页"同步状态"项 summary: "N 个待同步 · M 个冲突" / "同步正常"。 */
    val pendingHint: String,
    /** [B2] 未读站内通知数；0 时 UI 不展示角标。 */
    val unreadCount: Int = 0,
)

/**
 * Lightweight VM backing the top-level [SettingsPage]. Exposes the minimum
 * snapshot the home page needs (username + login state + server URL + 同步状态摘要) so
 * Composable code never talks to Repository / SharedPreferences directly.
 *
 * UI 刷新机制:serverUrl 字段不再读 prefs,而是订阅 [ServerUrlHolder] 持有的
 * StateFlow —— 这样 `AccountSettingsViewModel.updateServerUrl` 改了 URL,
 * 所有订阅者(包括本 VM 与 [AccountProfilePage])立刻收到,无须退出页面再进。
 *
 * pendingHint 由 [SyncStatusRepository.snapshot] 在 combine 内异步读,失败兜底"同步状态"。
 *
 * [§14.2] 移除 `@HiltViewModel` 与 `@Inject` —— Koin `inject()` 字段注入。
 */
class SettingsHomeViewModel : ViewModel() {

    private val context: Context = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val serverUrlHolder: ServerUrlHolder = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val syncStatusRepository: SyncStatusRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val notificationRepository: NotificationRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    init {
        Log.d(TAG, "SettingsHomeViewModel initialized")
    }

    val state: StateFlow<SettingsHomeState> = combine(
        userAuthRepository.state,
        serverUrlHolder.url,
        pendingHintFlow(),
        notificationRepository.unreadCount,
    ) { auth, url, pendingHint, unread ->
        SettingsHomeState(
            username = AuthPrefs.readUsername(),
            isLoggedIn = auth is AuthState.SignedIn,
            serverUrl = url,
            pendingHint = pendingHint,
            unreadCount = unread.coerceAtLeast(0),
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
        ),
    )

    /**
     * 同步状态摘要 Flow。失败兜底"同步状态"字符串,避免 SettingsHomeState 阻塞。
     *
     * [Phase 4 Task #21] 退役队列计数，改为 sync_cursor + isLocalOnly 语义。
     */
    private fun pendingHintFlow() = flow {
        val hint = runCatching {
            val s = syncStatusRepository.snapshot()
            when {
                s.unsyncedCount > 0 -> "${s.unsyncedCount} 个联系人未同步"
                s.lastSyncVersion > 0 -> "同步正常"
                else -> DEFAULT_PENDING_HINT
            }
        }.getOrElse {
            Log.w(TAG, "pendingHintFlow: 读 snapshot 失败,fallback", it)
            DEFAULT_PENDING_HINT
        }
        emit(hint)
    }

    companion object {
        private const val DEFAULT_PENDING_HINT = "同步状态"
    }
}
