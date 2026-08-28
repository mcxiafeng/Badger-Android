package top.mcxiafeng.badger.data.repository

import android.util.Log
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

/**
 * [B1] 站内通知仓库：未读数 60s 轮询 + 列表按需拉取。
 *
 * 服务端 `GET /api/user/notifications` **无分页**（一次全量，未读在前），
 * 因此 [notifications] 是全量快照，不是 page/size 游标。
 *
 * 轮询约束（对齐服务端前端 `auth-shared.js` + Token 安全）：
 * - 仅 [AuthState.SignedIn] 且 TokenHolder 非空才打 `/unread-count`
 * - 登出立即停轮询并把未读/列表清零（避免 badge 残留）
 * - 网络/401 失败保留上次未读数（有日志，不吞根因，不把 badge 抖成 0）
 */
class NotificationRepository(
    private val serverApi: ServerApi,
    private val userAuthRepository: UserAuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    externalScope: CoroutineScope? = null,
) {
    private val scope: CoroutineScope =
        externalScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _notifications = MutableStateFlow<List<UserNotification>>(emptyList())
    val notifications: StateFlow<List<UserNotification>> = _notifications.asStateFlow()

    @Volatile private var pollJob: Job? = null

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

    /** 立即拉一次未读数（B2 下拉刷新 / 标记已读后校正）。 */
    suspend fun refreshUnreadCount() {
        if (userAuthRepository.currentToken().isNullOrBlank()) {
            Log.d(TAG, "refreshUnreadCount skipped: no token")
            return
        }
        try {
            val n = withContext(ioDispatcher) { serverApi.getUnreadNotificationCount() }
            // [修复防御]: 登出与 in-flight 轮询竞态 —— token 已清则丢弃结果，避免 badge 在 SignedOut 后被写回。
            if (userAuthRepository.currentToken().isNullOrBlank()) return
            _unreadCount.value = n.coerceAtLeast(0)
        } catch (e: ApiException) {
            // [修复防御]: 401/5xx 不把 badge 清零 —— 避免网络抖动让角标闪没；有日志不吞根因。
            Log.w(TAG, "unread-count failed: status=${e.status} what=${e.what} body=${e.bodyText?.take(80)}")
        } catch (e: Throwable) {
            Log.w(TAG, "unread-count failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 按需拉全量列表（B2 通知页）。失败抛给调用方，不静默清空已有列表。 */
    suspend fun refreshNotifications() {
        if (userAuthRepository.currentToken().isNullOrBlank()) {
            Log.d(TAG, "refreshNotifications skipped: no token")
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
            Log.d(TAG, "unread poll started interval=${POLL_INTERVAL_MS}ms")
            while (isActive) {
                refreshUnreadCount()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        if (pollJob != null) Log.d(TAG, "unread poll stopped")
        pollJob?.cancel()
        pollJob = null
    }

    companion object {
        private const val TAG = "NotificationRepo"
        const val POLL_INTERVAL_MS = 60_000L
    }
}
