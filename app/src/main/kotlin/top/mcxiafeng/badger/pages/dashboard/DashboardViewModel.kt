package top.mcxiafeng.badger.pages.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.RecentPerson
import top.mcxiafeng.badger.network.ServerApi

/**
 * [C1] Dashboard 统计概览 VM。
 *
 * 优先从服务端 `GET /api/user/stats` 拉取；404 或网络异常时降级为本地 Room 计数。
 * 最近添加列表始终来自本地（服务端 recentPersons 可能不全）。
 */
class DashboardViewModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val serverApi: ServerApi = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val contactCacheDao: ContactCacheDao = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val tagCacheDao: TagCacheDao = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val collectionCacheDao: CardCollectionCacheDao = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    /** 最近添加的联系人（本地 Room，始终可用）。 */
    private val _recentContacts = MutableStateFlow<List<DashboardRecentItem>>(emptyList())

    private val localCounts = combine(
        contactCacheDao.observeRowCount(),
        tagCacheDao.observeRowCount(),
        collectionCacheDao.observeRowCount(),
    ) { contactCount, tagCount, collectionCount ->
        Triple(contactCount, tagCount, collectionCount)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        localCounts,
        _recentContacts,
        userAuthRepository.state,
        _loading,
        _error,
    ) { counts, recentContacts, auth, loading, error ->
        DashboardUiState(
            contactCount = counts.first,
            tagCount = counts.second,
            collectionCount = counts.third,
            recentContacts = recentContacts,
            loading = loading,
            error = error,
            isLoggedIn = auth is AuthState.SignedIn,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(isLoggedIn = userAuthRepository.state.value is AuthState.SignedIn),
    )

    /**
     * 刷新统计：先试 API，失败则仅刷新本地最近联系人。
     */
    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            // 始终刷新本地最近联系人
            runCatching {
                withContext(dispatcher) {
                    _recentContacts.value = contactCacheDao.getRecentContacts(10).map { it.toRecentItem() }
                }
            }.onFailure { e ->
                Log.w(TAG, "refresh local recent failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            // 试拉 API stats（404 降级不报错）
            runCatching {
                withContext(dispatcher) { serverApi.getStats() }
            }.onSuccess { stats ->
                if (stats != null) {
                    Log.d(TAG, "API stats: persons=${stats.personCount} tags=${stats.tagCount} collections=${stats.collectionCount}")
                    // API 成功时用 API 的 recentPersons 覆盖本地（如果有的话）
                    if (stats.recentPersons.isNotEmpty()) {
                        _recentContacts.value = stats.recentPersons.map { it.toLocalEntity() }
                    }
                } else {
                    Log.d(TAG, "API stats null (404 or parse error), using local counts")
                }
            }.onFailure { e ->
                Log.w(TAG, "API stats failed: ${e.javaClass.simpleName}: ${e.message}, using local counts")
                // 不写 _error，降级为本地计数
            }
            _loading.value = false
        }
    }

    fun clearError() {
        if (_error.value != null) _error.value = null
    }

    companion object {
        private const val TAG = "DashboardVM"
    }
}

/** 联系人 → 最近添加项（UI 展示用）。 */
private fun ContactCacheEntity.toRecentItem() = DashboardRecentItem(
    id = id,
    name = name,
    avatarUrl = avatarUrl,
    avatarPath = avatarPath,
)

/** API RecentPerson → 本地展示项（无本地 id/path，仅用于 API 成功时覆盖）。 */
private fun RecentPerson.toLocalEntity() = DashboardRecentItem(
    id = 0L,
    name = name,
    avatarUrl = avatarUrl,
    avatarPath = null,
)

data class DashboardUiState(
    val contactCount: Int = 0,
    val tagCount: Int = 0,
    val collectionCount: Int = 0,
    val recentContacts: List<DashboardRecentItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
)

data class DashboardRecentItem(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val avatarPath: String?,
)
