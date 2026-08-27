package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
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

    private val context: Context = top.mcxiafeng.badger.di.KoinComponentBy.get()
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
                    Log.e(tag, "uiState: 读 snapshot/battery 失败", e)
                    emit(SyncStatusUiState.Error(e.message ?: "加载失败"))
                }
            }
        }
        .catch { e ->
            Log.e(tag, "uiState: catch 外层异常", e)
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
        Log.d(tag, "onEvent: $event")
        when (event) {
            SyncStatusEvent.Refresh -> triggerRefresh()
            SyncStatusEvent.RetryAll -> retryAll()
            SyncStatusEvent.DismissMessage -> {
                Log.d(tag, "DismissMessage: no-op(由 Snackbar duration 控制)")
            }
        }
    }

    private fun triggerRefresh() {
        refreshTrigger.value = System.currentTimeMillis()
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
            Log.d(tag, "retryAll: applied=$count")
            triggerRefresh()
        }
    }

    private fun readBatteryOptimized(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (e: Exception) {
            Log.w(tag, "readBatteryOptimized: 读 isIgnoringBatteryOptimizations 失败,fallback false", e)
            false
        }
    }

    private companion object {
        const val TAG = "SyncStatusVM"
    }
}
