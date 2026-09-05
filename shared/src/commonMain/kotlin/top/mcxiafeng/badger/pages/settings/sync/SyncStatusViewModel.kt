package top.mcxiafeng.badger.pages.settings.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.platform.BatteryOptimization
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.nowMs

/**
 * [Phase 4 Task #21] SyncStatusPage 的 ViewModel。
 *
 * 退役队列语义后：
 * - snapshot 读 sync_cursor + isLocalOnly 计数
 * - 仅保留 Refresh / RetryAll 事件
 *
 * [§14.2] 移除 `@HiltViewModel` 与 `@Inject` —— Koin `inject()` 字段注入。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModel : ViewModel() {

    private val repository: SyncStatusRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val tag = TAG

    private val refreshTrigger = MutableStateFlow(0L)

    val uiState: StateFlow<SyncStatusUiState> = refreshTrigger
        .flatMapLatest {
            flow {
                emit(SyncStatusUiState.Loading)
                try {
                    val snapshot = repository.snapshot()
                    val batteryOptimized = readBatteryOptimized()
                    emit(SyncStatusUiState.Success(snapshot, batteryOptimized))
                } catch (e: Exception) {
                    BadgerLog.e(tag, "uiState: 读 snapshot/battery 失败", e)
                    emit(SyncStatusUiState.Error(e.message ?: "加载失败"))
                }
            }
        }
        .catch { e ->
            BadgerLog.e(tag, "uiState: catch 外层异常", e)
            emit(SyncStatusUiState.Error(e.message ?: "加载失败"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SyncStatusUiState.Loading,
        )

    private val _messages = Channel<SyncStatusMessage>(Channel.BUFFERED)
    val messages: Flow<SyncStatusMessage> = _messages.receiveAsFlow()

    fun onEvent(event: SyncStatusEvent) {
        BadgerLog.d(tag, "onEvent: $event")
        when (event) {
            SyncStatusEvent.Refresh -> triggerRefresh()
            SyncStatusEvent.RetryAll -> retryAll()
            SyncStatusEvent.DismissMessage -> {
                BadgerLog.d(tag, "DismissMessage: no-op(由 Snackbar duration 控制)")
            }
        }
    }

    private fun triggerRefresh() {
        refreshTrigger.value = nowMs()
    }

    private fun retryAll() {
        viewModelScope.launch {
            val count = repository.retryAll()
            val msg = if (count == 0) {
                "已触发增量同步(无新增变更)"
            } else {
                "已触发增量同步,应用 $count 条变更"
            }
            _messages.send(SyncStatusMessage.Info(msg))
            BadgerLog.d(tag, "retryAll: applied=$count")
            triggerRefresh()
        }
    }

    private fun readBatteryOptimized(): Boolean =
        // [KMP K13c] PowerManager 检查下沉 BatteryOptimization 边界（iOS 恒 true）
        BatteryOptimization.isIgnoring()

    private companion object {
        const val TAG = "SyncStatusVM"
    }
}
