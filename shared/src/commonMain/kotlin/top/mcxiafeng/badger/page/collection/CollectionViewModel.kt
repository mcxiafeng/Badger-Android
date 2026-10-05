package top.mcxiafeng.badger.page.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.sync.SyncEngineHolder
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModel(
    private val collectionRepository: CollectionRepository = CollectionRepository(),
    private val syncEngine: SyncEngine = SyncEngineHolder.get(),
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0L)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val uiState: StateFlow<CollectionUiState> = refreshTrigger
        .flatMapLatest {
            flow {
                _isRefreshing.value = true
                // 预期失败（离线/未登录）由引擎折叠进 outcome；
                // 其余异常在下方 catch 折叠为页面级 Error 呈现（用户可见，非静默）
                BadgerLog.d(TAG, "触发同步：${syncEngine.syncNow(EntityKind.COLLECTION)}")
                _isRefreshing.value = false
                emitAll(
                    collectionRepository.observeAll().map { collections ->
                        val state: CollectionUiState = CollectionUiState.Success(
                            collections.sortedByDescending { it.createTime },
                        )
                        state
                    }
                )
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            _isRefreshing.value = false
            BadgerLog.e(TAG, "加载名片夹数据失败", e)
            emit(CollectionUiState.Error(e.message ?: "加载名片夹数据失败"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = CollectionUiState.Loading,
        )

    fun refresh() {
        refreshTrigger.update { it + 1 }
    }

    /** 本地优先创建：先落库立即可见，入队后立即尝试推送（离线则留队等下次 kick）。 */
    fun createCollection(name: String, description: String?) {
        viewModelScope.launch {
            val collection = Collection(
                uuid = Uuid.random(),
                name = name,
                description = description?.takeIf { it.isNotBlank() },
            )
            BadgerLog.d(TAG, "createCollection: uuid=${collection.uuid}")
            collectionRepository.createLocal(collection)
            syncEngine.syncNow(EntityKind.COLLECTION)
        }
    }

    /** 本地优先修改：写前重读基线入队 from/to，冲突判定交给引擎。 */
    fun updateCollection(collection: Collection, name: String, description: String?) {
        viewModelScope.launch {
            // 写前重读最新行做基座：只覆盖 UI 编辑的字段，未编辑字段以本地库现值为准
            //（UI 快照可能落后于后台一轮落库，直接 copy 会把旧值回写）
            val latest = collectionRepository.get(collection.uuid) ?: run {
                BadgerLog.w(TAG, "updateCollection: 目标已不在本地库，放弃 uuid=${collection.uuid}")
                return@launch
            }
            val updated = latest.copy(
                name = name,
                description = description?.takeIf { it.isNotBlank() },
            )
            BadgerLog.d(TAG, "updateCollection: uuid=${updated.uuid}")
            collectionRepository.updateLocal(updated)
            syncEngine.syncNow(EntityKind.COLLECTION)
        }
    }

    /** 本地优先删除：本地删行立即消失，入队后立即尝试推送。 */
    fun deleteCollection(collection: Collection) {
        viewModelScope.launch {
            BadgerLog.d(TAG, "deleteCollection: uuid=${collection.uuid}")
            collectionRepository.deleteLocal(collection.uuid)
            syncEngine.syncNow(EntityKind.COLLECTION)
        }
    }

    companion object {
        private const val TAG = "CollectionViewModelTester"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
