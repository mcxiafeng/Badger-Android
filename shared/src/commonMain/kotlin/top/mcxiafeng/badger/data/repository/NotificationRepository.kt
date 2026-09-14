package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.UserNotification

class NotificationRepository(
    private val serverApi: ServerApi,
    private val userAuthRepository: UserAuthRepository,
    private val ioDispatcher: CoroutineDispatcher = BadgerDispatchers.io,
    externalScope: CoroutineScope? = null,
) {
    private val scope: CoroutineScope =
        externalScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _notifications = MutableStateFlow<List<UserNotification>>(emptyList())
    val notifications: StateFlow<List<UserNotification>> = _notifications.asStateFlow()

    @kotlin.concurrent.Volatile private var pollJob: Job? = null

    init {
        scope.launch {
            userAuthRepository.state.collect { state ->
                when (state) {
                    AuthState.SignedIn -> startPolling()
                    AuthState.SignedOut, is AuthState.Error -> {
                        stopPolling()
                        _unreadCount.value = 0
                        _notifications.value = emptyList()
                    }
                    AuthState.Unknown -> Unit
                }
            }
        }
    }

    
    suspend fun refreshUnreadCount() {
        if (userAuthRepository.currentToken().isNullOrBlank()) {
            BadgerLog.d(TAG, "refreshUnreadCount skipped: no token")
            return
        }
        try {
            val n = withContext(ioDispatcher) { serverApi.getUnreadNotificationCount() }
            
            if (userAuthRepository.currentToken().isNullOrBlank()) return
            _unreadCount.value = n.coerceAtLeast(0)
        } catch (e: ApiException) {
            
            BadgerLog.w(TAG, "unread-count failed: status=${e.status} what=${e.what} body=${e.bodyText?.take(80)}")
        } catch (e: Exception) {
            BadgerLog.w(TAG, "unread-count failed: ${e::class.simpleName}: ${e.message}")
        }
    }

    
    suspend fun refreshNotifications() {
        if (userAuthRepository.currentToken().isNullOrBlank()) {
            BadgerLog.d(TAG, "refreshNotifications skipped: no token")
            return
        }
        val list = withContext(ioDispatcher) { serverApi.listNotifications() }
        _notifications.value = list
    }

    suspend fun markAsRead(uuid: String) {
        withContext(ioDispatcher) { serverApi.markNotificationRead(uuid) }
        _notifications.update { rows ->
            rows.map { if (it.uuid == uuid) it.copy(read = true) else it }
        }
        refreshUnreadCount()
    }

    suspend fun delete(uuid: String) {
        withContext(ioDispatcher) { serverApi.deleteNotification(uuid) }
        _notifications.update { rows -> rows.filterNot { it.uuid == uuid } }
        refreshUnreadCount()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            BadgerLog.d(TAG, "unread poll started interval=${POLL_INTERVAL_MS}ms")
            while (isActive) {
                refreshUnreadCount()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        if (pollJob != null) BadgerLog.d(TAG, "unread poll stopped")
        pollJob?.cancel()
        pollJob = null
    }

    companion object {
        private const val TAG = "NotificationRepo"
        const val POLL_INTERVAL_MS = 60_000L
    }
}
