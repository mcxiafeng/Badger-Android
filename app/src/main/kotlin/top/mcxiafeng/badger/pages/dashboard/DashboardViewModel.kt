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
    private val serverApi: ServerApi,
    private val userAuthRepository: UserAuthRepository,
    private val contactCacheDao: ContactCacheDao,
    private val tagCacheDao: TagCacheDao,
    private val collectionCacheDao: CardCollectionCacheDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _loading = MutableStateFlow(false)

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
    ) { counts, recentContacts, auth, loading ->
        DashboardUiState(
            contactCount = counts.first,
            tagCount = counts.second,
            collectionCount = counts.third,
            recentContacts = recentContacts,
            loading = loading,
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
            try {
                // 始终刷新本地最近联系人。
                runCatching {
                    withContext(dispatcher) {
                        _recentContacts.value = contactCacheDao.getRecentContacts(10).map { it.toRecentItem() }
                    }
                }.onFailure { e ->
                    Log.w(TAG, "refresh local recent failed: ${e.javaClass.simpleName}: ${e.message}")
                }

                // 试拉 API stats（404 降级不报错）。
                runCatching {
                    withContext(dispatcher) { serverApi.getStats() }
                }.onSuccess { stats ->
                    if (stats != null) {
                        Log.d(TAG, "API stats: persons=${stats.persons} tags=${stats.tags} collections=${stats.collections}")
                        if (stats.recentPersons.isNotEmpty()) {
                            _recentContacts.value = stats.recentPersons.map { it.toLocalEntity() }
                        }
                    } else {
                        Log.d(TAG, "API stats null (404 or parse error), using local counts")
                    }
                }.onFailure { e ->
                    Log.w(TAG, "API stats failed: ${e.javaClass.simpleName}: ${e.message}, using local counts")
                }
            } finally {
                _loading.value = false
            }
        }
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
