package top.mcxiafeng.badger.pages.dashboard

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
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

/** Dashboard 统计概览 VM，优先 API 拉取，失败降级本地计数。 */
class DashboardViewModel(
    private val dispatcher: CoroutineDispatcher = BadgerDispatchers.io,
) : ViewModel() {

    private val serverApi: ServerApi = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val contactCacheDao: ContactCacheDao = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val tagCacheDao: TagCacheDao = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val collectionCacheDao: CardCollectionCacheDao = top.mcxiafeng.badger.di.KoinComponentBy.get()

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
            // 始终刷新本地最近联系人
            runCatching {
                withContext(dispatcher) {
                    _recentContacts.value = contactCacheDao.getRecentContacts(10).map { it.toRecentItem() }
                }
            }.onFailure { e ->
                BadgerLog.w(TAG, "refresh local recent failed: ${e::class.simpleName}: ${e.message}")
            }
            // 试拉 API stats（404 降级不报错）
            runCatching {
                withContext(dispatcher) {
                    val stats = serverApi.getStats()
                    if (stats != null) {
                        BadgerLog.d(TAG, "API stats: persons=${stats.persons} tags=${stats.tags} collections=${stats.collections}")
                        if (stats.recentPersons.isNotEmpty()) {
                            stats.recentPersons.map { it.toLocalEntity() }
                        } else null
                    } else {
                        BadgerLog.d(TAG, "API stats null (404 or parse error), using local counts")
                        null
                    }
                }
            }.onSuccess { mapped ->
                if (mapped != null) _recentContacts.value = mapped
            }.onFailure { e ->
                BadgerLog.w(TAG, "API stats failed: ${e::class.simpleName}: ${e.message}, using local counts")
                // 不写 _error，降级为本地计数
            }
            _loading.value = false
        }
    }

    /** API RecentPerson → 本地展示项，用 serverId 查本地 id。 */
    private suspend fun RecentPerson.toLocalEntity(): DashboardRecentItem {
        val localContact = contactCacheDao.getContactByServerId(uuid)
        return DashboardRecentItem(
            id = localContact?.id ?: 0L,
            name = name,
            avatarUrl = avatarURL,
            avatarPath = localContact?.avatarPath,
        )
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

data class DashboardUiState(
    val contactCount: Int = 0,
    val tagCount: Int = 0,
    val collectionCount: Int = 0,
    val recentContacts: List<DashboardRecentItem> = emptyList(),
    val loading: Boolean = false,
    val isLoggedIn: Boolean = false,
)

data class DashboardRecentItem(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val avatarPath: String?,
)
