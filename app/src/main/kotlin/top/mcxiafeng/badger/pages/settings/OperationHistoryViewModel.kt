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
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OperationHistoryViewModel @Inject constructor(
    private val repository: OperationHistoryRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.All)

    val uiState: StateFlow<OperationHistoryUiState> = filter
        .flatMapLatest { currentFilter ->
            repository.observeHistory(filter = currentFilter, limit = DEFAULT_LIMIT)
                .map { records ->
                    if (records.isEmpty()) {
                        OperationHistoryUiState.Empty(filter = currentFilter)
                    } else {
                        OperationHistoryUiState.Success(
                            records = records,
                            filter = currentFilter,
                        )
                    }
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
        }
    }

    /**
     * 当前 filter 值(供 Composable 在不想订阅 uiState 时读取)。
     */
    fun currentFilter(): HistoryFilter = filter.value

    private companion object {
        const val TAG = "OpHistoryVM"
        const val DEFAULT_LIMIT = 100
    }
}