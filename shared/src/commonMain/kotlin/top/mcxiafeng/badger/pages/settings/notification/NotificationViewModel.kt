package top.mcxiafeng.badger.pages.settings.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.UserNotification
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

/** [C4] 通知筛选模式。 */
enum class NotificationFilter { ALL, UNREAD }

/**
 * [B2] 通知列表页 VM。未读数来自 [NotificationRepository] 的 60s 轮询；
 * 列表按需 [refresh]，失败写 [NotificationUiState.error]，不静默清空已有列表。
 *
 * [C4] 新增：筛选（全部/未读）+ 点击跳转通知详情。
 */
class NotificationViewModel(
    private val dispatcher: CoroutineDispatcher = BadgerDispatchers.io,
) : ViewModel() {

    private val repository: NotificationRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val contactCacheDao: ContactCacheDao = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _filter = MutableStateFlow(NotificationFilter.ALL)

    val uiState: StateFlow<NotificationUiState> = combine(
        repository.notifications,
        repository.unreadCount,
        userAuthRepository.state,
        _loading,
        _error,
        _filter,
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val items = array[0] as List<UserNotification>
        val unread = array[1] as Int
        val auth = array[2] as AuthState
        val loading = array[3] as Boolean
        val error = array[4] as String?
        val filter = array[5] as NotificationFilter
        val filtered = when (filter) {
            NotificationFilter.ALL -> items
            NotificationFilter.UNREAD -> items.filter { !it.read }
        }
        NotificationUiState(
            items = filtered,
            unreadCount = unread.coerceAtLeast(0),
            loading = loading,
            error = error,
            isLoggedIn = auth is AuthState.SignedIn,
            filter = filter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationUiState(
            isLoggedIn = userAuthRepository.state.value is AuthState.SignedIn,
        ),
    )

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = runCatching {
                withContext(dispatcher) {
                    repository.refreshNotifications()
                    repository.refreshUnreadCount()
                }
            }
            result.onFailure { e ->
                BadgerLog.w(TAG, "refresh failed: ${e::class.simpleName}: ${e.message}")
                _error.value = e.message ?: "加载失败"
            }
            _loading.value = false
        }
    }

    fun markAsRead(uuid: String) {
        if (uuid.isBlank()) return
        val row = repository.notifications.value.firstOrNull { it.uuid == uuid } ?: return
        if (row.read) return
        viewModelScope.launch {
            runCatching {
                withContext(dispatcher) { repository.markAsRead(uuid) }
            }.onFailure { e ->
                BadgerLog.w(TAG, "markAsRead failed uuid=$uuid: ${e::class.simpleName}: ${e.message}")
                _error.value = e.message ?: "标记已读失败"
            }
        }
    }

    /**
     * [C4] 通过服务端 person UUID 解析本地 Room contactId 后导航。
     *
     * 通知的 entityId 是服务端 UUID，但详情页导航需要本地 Room 自增 ID。
     * 解析失败（本地无缓存）时仅记日志，不崩溃。
     */
    fun navigateToPerson(serverUuid: String, onResolved: (Long) -> Unit) {
        if (serverUuid.isBlank()) return
        viewModelScope.launch {
            runCatching {
                withContext(dispatcher) {
                    contactCacheDao.getContactByServerId(serverUuid)?.id
                }
            }.onSuccess { localId ->
                if (localId != null && localId > 0) {
                    onResolved(localId)
                } else {
                    BadgerLog.w(TAG, "navigateToPerson: no local contact for serverUuid=${serverUuid.take(8)}")
                }
            }.onFailure { e ->
                BadgerLog.w(TAG, "navigateToPerson failed: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    fun delete(uuid: String) {
        if (uuid.isBlank()) return
        viewModelScope.launch {
            runCatching {
                withContext(dispatcher) { repository.delete(uuid) }
            }.onFailure { e ->
                BadgerLog.w(TAG, "delete failed uuid=$uuid: ${e::class.simpleName}: ${e.message}")
                _error.value = e.message ?: "删除失败"
            }
        }
    }

    fun clearError() {
        if (_error.value != null) _error.value = null
    }

    /** [C4] 切换筛选模式。 */
    fun setFilter(filter: NotificationFilter) {
        if (_filter.value != filter) _filter.value = filter
    }

    companion object {
        private const val TAG = "NotificationVM"
    }
}

data class NotificationUiState(
    val items: List<UserNotification> = emptyList(),
    val unreadCount: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val filter: NotificationFilter = NotificationFilter.ALL,
)
