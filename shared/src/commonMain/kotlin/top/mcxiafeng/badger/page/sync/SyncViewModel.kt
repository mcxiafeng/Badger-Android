package top.mcxiafeng.badger.page.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.user.database.CacheDbHolder
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.coroutines.cancellation.CancellationException

/** 手动同步动作的进行中/结果提示，与快照数据分开流转。 */
private data class SyncActionUi(val syncing: Boolean = false, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModel(
    private val repository: SyncStatusRepository = SyncStatusRepository(
        cache = CacheDbHolder.get(),
        system = SystemDbHolder.get(),
    ),
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0L)
    private val actionUi = MutableStateFlow(SyncActionUi())

    val uiState: StateFlow<SyncUiState> = combine(
        refreshTrigger,
        repository.observePendingCount(),
        actionUi,
    ) { trigger, _, action -> trigger to action }
        .flatMapLatest { (_, action) ->
            flow<SyncUiState> {
                val snapshot = repository.snapshot()
                emit(SyncUiState.Success(snapshot, action.syncing, action.message))
            }.catch { e ->
                if (e is CancellationException) throw e
                BadgerLog.e(TAG, "读取同步快照失败", e)
                emit(SyncUiState.Error(e.message ?: "加载同步状态失败"))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SyncUiState.Loading,
        )

    fun onEvent(event: SyncEvent) {
        BadgerLog.d(TAG, "onEvent: $event")
        when (event) {
            SyncEvent.Refresh -> refreshTrigger.update { it + 1 }
            SyncEvent.SyncNow -> syncNow()
        }
    }

    private fun syncNow() {
        if (actionUi.value.syncing) return
        viewModelScope.launch {
            actionUi.update { it.copy(syncing = true, message = null) }
            val message = try {
                repository.syncNow().summary()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BadgerLog.e(TAG, "syncNow 失败", e)
                "同步失败：${e.message}"
            }
            actionUi.update { it.copy(syncing = false, message = message) }
            refreshTrigger.update { it + 1 }
        }
    }

    companion object {
        private const val TAG = "SyncViewModelTester"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
