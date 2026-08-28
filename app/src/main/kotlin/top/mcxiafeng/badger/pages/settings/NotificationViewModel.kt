package top.mcxiafeng.badger.pages.settings

import android.util.Log
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
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.UserNotification

/**
 * [B2] 通知列表页 VM。未读数来自 [NotificationRepository] 的 60s 轮询；
 * 列表按需 [refresh]，失败写 [NotificationUiState.error]，不静默清空已有列表。
 */
class NotificationViewModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val repository: NotificationRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NotificationUiState> = combine(
        repository.notifications,
        repository.unreadCount,
        userAuthRepository.state,
        _loading,
        _error,
    ) { items, unread, auth, loading, error ->
        NotificationUiState(
            items = items,
            unreadCount = unread.coerceAtLeast(0),
            loading = loading,
            error = error,
            isLoggedIn = auth is AuthState.SignedIn,
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
                Log.w(TAG, "refresh failed: ${e.javaClass.simpleName}: ${e.message}")
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
                Log.w(TAG, "markAsRead failed uuid=$uuid: ${e.javaClass.simpleName}: ${e.message}")
                _error.value = e.message ?: "标记已读失败"
            }
        }
    }

    fun delete(uuid: String) {
        if (uuid.isBlank()) return
        viewModelScope.launch {
            runCatching {
                withContext(dispatcher) { repository.delete(uuid) }
            }.onFailure { e ->
                Log.w(TAG, "delete failed uuid=$uuid: ${e.javaClass.simpleName}: ${e.message}")
                _error.value = e.message ?: "删除失败"
            }
        }
    }

    fun clearError() {
        if (_error.value != null) _error.value = null
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
)
