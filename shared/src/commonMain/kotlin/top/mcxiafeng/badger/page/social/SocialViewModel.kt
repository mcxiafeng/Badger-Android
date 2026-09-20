package top.mcxiafeng.badger.page.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.SocialRepository
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.user.database.CacheDbHolder
import top.mcxiafeng.badger.utils.BadgerLog

@Immutable
data class SocialUiState(
    val personCount: Int = 0,
    val collectionCount: Int = 0,
    val tagCount: Int = 0,
    val pendingCount: Int = 0,
    val refreshing: Boolean = false,
    val message: String? = null,
)

private data class RefreshUi(val refreshing: Boolean = false, val message: String? = null)

class SocialViewModel(
    private val repo: SocialRepository = SocialRepository(CacheDbHolder.get(), SystemDbHolder.get()),
) : ViewModel() {

    private val refreshUi = MutableStateFlow(RefreshUi())
    private var refreshJob: Job? = null

    val uiState: StateFlow<SocialUiState> = combine(
        repo.observePersons(),
        repo.observeCollections(),
        repo.observeTags(),
        repo.observePendingCount(),
        refreshUi,
    ) { persons, collections, tags, pending, refresh ->
        SocialUiState(
            personCount = persons.size,
            collectionCount = collections.size,
            tagCount = tags.size,
            pendingCount = pending,
            refreshing = refresh.refreshing,
            message = refresh.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SocialUiState())

    init {
        // 本地优先：Flow 立即出本地数据；同时后台拉服务端刷新（规则1/3，pending 时自动跳过=规则2）
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshUi.update { it.copy(refreshing = true, message = null) }
            val message = try {
                repo.refreshAll().summary()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                BadgerLog.e(TAG, "refresh 失败", e)
                "同步失败：${e.message}"
            }
            refreshUi.update { it.copy(refreshing = false, message = message) }
            BadgerLog.d(TAG, "refresh 结束: $message")
        }
    }

    companion object {
        private const val TAG = "SocialViewModelTester"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
