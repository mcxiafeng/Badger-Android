package top.mcxiafeng.badger.pages.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
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
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.network.RecentPerson
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

class DashboardViewModel(
    private val dispatcher: CoroutineDispatcher = BadgerDispatchers.io,
) : ViewModel() {

    private val serverApi: ServerApi = KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = KoinComponentBy.get()
    private val contactCacheDao: ContactCacheDao = KoinComponentBy.get()
    private val tagCacheDao: TagCacheDao = KoinComponentBy.get()
    private val collectionCacheDao: CardCollectionCacheDao = KoinComponentBy.get()

    private val _loading = MutableStateFlow(false)

    
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

    

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            
            runCatching {
                withContext(dispatcher) {
                    _recentContacts.value = contactCacheDao.getRecentContacts(10).map { it.toRecentItem() }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                BadgerLog.w(TAG, "refresh local recent failed: ${e::class.simpleName}: ${e.message}")
            }
            
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
                if (e is CancellationException) throw e
                BadgerLog.w(TAG, "API stats failed: ${e::class.simpleName}: ${e.message}, using local counts")
                
            }
            _loading.value = false
        }
    }

    
    private suspend fun RecentPerson.toLocalEntity(): DashboardRecentItem {
        val localContact = contactCacheDao.getContactByServerId(uuid)
        return DashboardRecentItem(
            id = localContact?.id ?: 0L,
            name = name,
            avatarUrl = avatarURL,
            avatarPath = localContact?.avatarPath,
            serverUuid = uuid,
        )
    }

    companion object {
        private const val TAG = "DashboardVM"
    }
}

private fun ContactCacheEntity.toRecentItem() = DashboardRecentItem(
    id = id,
    name = name,
    avatarUrl = avatarUrl,
    avatarPath = avatarPath,
)

@Immutable
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
    
    val serverUuid: String? = null,
)
