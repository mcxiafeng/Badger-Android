package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryRepository
import javax.inject.Inject

/**
 * [V2-P7] OperationHistoryPage 的 ViewModel。
 *
 * 严格按 NowInAndroid 模式:
 * - 持久状态走 [uiState]: `StateFlow<OperationHistoryUiState>`,viewModelScope 内 `stateIn`
 *   保证旋转屏 / 切深色模式不丢状态。
 * - 瞬时反馈走 [messages]: `Channel<OperationHistoryMessage>`,Composable 用 `LaunchedEffect`
 *   收集后调 `SnackbarHostState.showSnackbar`。
 * - [filter] 走 `MutableStateFlow`,filter 变化 `flatMapLatest` 重订阅 Repository Flow;
 *   列表空/非空分发 Success/Empty。
 * - 副作用(retry / withdraw / adoptLocal / adoptServer)通过 [onEvent] 转发给 Repository,
 *   不在 VM 里直接持有 DAO。
 *
 * [V2-P10] 多选模式:
 * - 多选状态由 [multiSelect](开/关) + [selectedIds](当前选中 opId 集合)承载。
 * - uiState 通过 `combine(records, filter, multiSelect, selectedIds)` 4 Flow 合并
 *   成单一 Success / Empty,UI 端 `collectAsState` 一次拿到所有。
 * - 切换 filter **不**清空 selectedIds(用户跨 filter 选需注意)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OperationHistoryViewModel @Inject constructor(
    private val repository: OperationHistoryRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.All)
    private val multiSelect = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<OperationHistoryUiState> = combine(
        filter.flatMapLatest { currentFilter ->
            repository.observeHistory(filter = currentFilter, limit = DEFAULT_LIMIT)
        },
        filter,
        multiSelect,
        selectedIds,
    ) { records, currentFilter, ms, sel ->
        if (records.isEmpty()) {
            OperationHistoryUiState.Empty(
                filter = currentFilter,
            )
        } else {
            OperationHistoryUiState.Success(
                records = records,
                filter = currentFilter,
                multiSelect = ms,
                selectedIds = sel,
            )
        }
    }
        .catch { e ->
            Log.e(TAG, "observeHistory failed", e)
            val errorState: OperationHistoryUiState = OperationHistoryUiState.Error(
                e.message ?: "加载失败",
            )
            emit(errorState)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OperationHistoryUiState.Loading,
        )

    private val _messages = Channel<OperationHistoryMessage>(Channel.BUFFERED)
    val messages: Flow<OperationHistoryMessage> = _messages.receiveAsFlow()

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
            is OperationHistoryEvent.Retry -> {
                viewModelScope.launch {
                    val result = repository.retry(event.opId)
                    val message = result.toMessage()
                    _messages.send(message)
                    Log.d(TAG, "Retry: opId=${event.opId.take(8)} result=$result")
                }
            }
            is OperationHistoryEvent.Withdraw -> {
                viewModelScope.launch {
                    val result = repository.withdraw(event.opId)
                    val message = result.toMessage()
                    _messages.send(message)
                    Log.d(TAG, "Withdraw: opId=${event.opId.take(8)} result=$result")
                }
            }
            is OperationHistoryEvent.AdoptLocal -> {
                viewModelScope.launch {
                    val result = repository.adoptLocal(event.opId)
                    val message = result.toMessage()
                    _messages.send(message)
                    Log.d(TAG, "AdoptLocal: opId=${event.opId.take(8)} result=$result")
                }
            }
            is OperationHistoryEvent.AdoptServer -> {
                viewModelScope.launch {
                    val result = repository.adoptServer(
                        opId = event.opId,
                        serverContactJson = event.serverContactJson,
                    )
                    val message = result.toMessage()
                    _messages.send(message)
                    Log.d(TAG, "AdoptServer: opId=${event.opId.take(8)} result=$result")
                }
            }

            // ============ [V2-P10] 多选模式事件 ============

            is OperationHistoryEvent.EnterMultiSelect -> {
                multiSelect.value = true
                val cur = selectedIds.value
                val initial = event.initialSelectedId
                selectedIds.value = if (initial != null) cur + initial else cur
                Log.d(TAG, "EnterMultiSelect: initial=$initial currentSelected=${selectedIds.value.size}")
            }
            OperationHistoryEvent.ExitMultiSelect -> {
                multiSelect.value = false
                selectedIds.value = emptySet()
                Log.d(TAG, "ExitMultiSelect")
            }
            is OperationHistoryEvent.ToggleSelect -> {
                val cur = selectedIds.value
                selectedIds.value = if (event.opId in cur) cur - event.opId else cur + event.opId
                Log.d(TAG, "ToggleSelect: opId=${event.opId.take(8)} now=${selectedIds.value.size}")
            }
            OperationHistoryEvent.SelectAll -> {
                val s = uiState.value
                if (s is OperationHistoryUiState.Success) {
                    selectedIds.value = s.records.map { it.history.opId }.toSet()
                    Log.d(TAG, "SelectAll: count=${selectedIds.value.size}")
                }
            }
            OperationHistoryEvent.ClearSelection -> {
                selectedIds.value = emptySet()
                Log.d(TAG, "ClearSelection")
            }
            is OperationHistoryEvent.BatchRetry -> {
                viewModelScope.launch {
                    val result = repository.batchRetry(event.opIds)
                    val message = result.toMessage()
                    _messages.send(message)
                    Log.d(TAG, "BatchRetry: opIds=${event.opIds.size} result=$result")
                    // 批量重试完退出多选(避免 UI 误点)
                    exitMultiSelect()
                }
            }
            is OperationHistoryEvent.BatchWithdraw -> {
                viewModelScope.launch {
                    val result = repository.batchWithdraw(event.opIds)
                    val message = result.toMessage()
                    _messages.send(message)
                    Log.d(TAG, "BatchWithdraw: opIds=${event.opIds.size} result=$result")
                    exitMultiSelect()
                }
            }
        }
    }

    /**
     * 当前 filter 值(供 Composable 在不想订阅 uiState 时读取)。
     */
    fun currentFilter(): HistoryFilter = filter.value

    /**
     * 批量操作后统一退出多选(用 private helper 集中收敛,避免重复 set)
     */
    private fun exitMultiSelect() {
        multiSelect.value = false
        selectedIds.value = emptySet()
    }

    private companion object {
        const val TAG = "OpHistoryVM"
        const val DEFAULT_LIMIT = 100
    }
}