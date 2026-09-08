package top.mcxiafeng.badger.pages.settings.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryRepository
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * OperationHistoryPage 的 ViewModel（只读日志视图）。
 *
 * filter 切换驱动 Flow 重订阅；无副作用事件。瞬时消息 Channel 移除（只读页无反馈）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OperationHistoryViewModel : ViewModel() {

    private val repository: OperationHistoryRepository = KoinComponentBy.get()

    private val filter = MutableStateFlow(HistoryFilter.All)

    val uiState: StateFlow<OperationHistoryUiState> = filter
        .flatMapLatest { currentFilter ->
            repository.observeHistory(filter = currentFilter, limit = DEFAULT_LIMIT)
        }
        .map { records ->
            if (records.isEmpty()) {
                OperationHistoryUiState.Empty(filter = filter.value)
            } else {
                OperationHistoryUiState.Success(
                    records = records,
                    filter = filter.value,
                )
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            BadgerLog.e(TAG, "observeHistory failed", e)
            emit(OperationHistoryUiState.Error(e.message ?: "加载失败"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OperationHistoryUiState.Loading,
        )

    fun onEvent(event: OperationHistoryEvent) {
        BadgerLog.d(TAG, "onEvent: $event")
        when (event) {
            is OperationHistoryEvent.ChangeFilter -> {
                filter.value = event.filter
            }
        }
    }

    /** 当前 filter 值（供 Composable 在不想订阅 uiState 时读取）。 */
    fun currentFilter(): HistoryFilter = filter.value

    private companion object {
        const val TAG = "OpHistoryVM"
        const val DEFAULT_LIMIT = 100
    }
}
