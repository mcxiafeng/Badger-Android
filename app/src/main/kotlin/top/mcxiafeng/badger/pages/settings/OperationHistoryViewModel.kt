package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryRepository

/**
 * [V2-P7] OperationHistoryPage 的 ViewModel。
 *
 * [Phase 3] 降级为只读日志：移除多选 + 撤销 / 重发 / 冲突解决副作用，只保留
 * filter 切换（纯本地订阅）。瞬时消息 Channel 一并移除（只读页无操作反馈）。
 *
 * Repository 通过构造函数注入，避免 ViewModel 再依赖全局 Service Locator。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OperationHistoryViewModel(
    private val repository: OperationHistoryRepository,
) : ViewModel() {

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
            Log.e(TAG, "observeHistory failed", e)
            emit(OperationHistoryUiState.Error(e.message ?: "加载失败"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OperationHistoryUiState.Loading,
        )

    fun onEvent(event: OperationHistoryEvent) {
        Log.d(TAG, "onEvent: $event")
        when (event) {
            is OperationHistoryEvent.ChangeFilter -> {
                filter.value = event.filter
            }
            OperationHistoryEvent.Refresh -> {
                // 纯本地订阅,不需要主动 refresh;filter 切走再切回 即等价 refresh
                Log.d(TAG, "Refresh: no-op (本地订阅驱动,filter 切换触发)")
            }
        }
    }

    /** 当前 filter 值(供 Composable 在不想订阅 uiState 时读取)。 */
    fun currentFilter(): HistoryFilter = filter.value

    private companion object {
        const val TAG = "OpHistoryVM"
        const val DEFAULT_LIMIT = 100
    }
}
