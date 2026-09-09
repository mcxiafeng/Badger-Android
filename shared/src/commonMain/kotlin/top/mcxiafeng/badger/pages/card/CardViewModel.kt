package top.mcxiafeng.badger.pages.card

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.model.CardCollectionWithCount as CollectionWithCount
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.importer.ImportConflict
import top.mcxiafeng.badger.data.importer.ContactConflictAction
import top.mcxiafeng.badger.data.importer.CollectionConflictAction
import top.mcxiafeng.badger.data.importer.ImportResult
import top.mcxiafeng.badger.data.importer.executeImport as executeImportTopLevel
import top.mcxiafeng.badger.data.importer.analyzeImportConflicts as analyzeImportConflictsTopLevel
import top.mcxiafeng.badger.data.importer.exportToJson as exportToJsonTopLevel
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.domain.RefreshFromServerUseCase
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs

sealed interface CardUiState {
    data object Loading : CardUiState
    data class Success(
        val collections: List<CollectionWithCount> = emptyList()
    ) : CardUiState
    data class Error(val message: String) : CardUiState
}

/**
 * [§14.2] 移除 `@HiltViewModel` 与 `@Inject` —— Koin 通过 `inject()` 字段注入。
 *
 * 注:`PlatformListViewModel` 同款模式,所有 VM 一致迁移。
 */
class CardViewModel : ViewModel() {

    private val repository: CollectionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val contactRepository: ContactRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val fieldRepository: FieldRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val tagRepository: TagRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val refreshFromServerUseCase: RefreshFromServerUseCase = KoinComponentBy.get()

    private val _uiState = MutableStateFlow<CardUiState>(CardUiState.Loading)
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

    // ========== 下拉刷新（触发服务端同步） ==========

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 一次性刷新结果提示（toast 消费后置空）。 */
    private val _refreshMessage = MutableStateFlow<String?>(null)
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()

    /**
     * 下拉刷新：触发一轮完整同步（push → pull），新数据落库后
     * [uiState] 的 Room Flow 自动推给 UI，无需手动重载列表。
     * 并发去重：已在刷新中再次下拉直接忽略。
     */
    fun refreshFromServer() {
        if (!_isRefreshing.compareAndSet(false, true)) {
            BadgerLog.d(TAG, "refreshFromServer: already refreshing, ignored")
            return
        }
        viewModelScope.launch {
            try {
                when (val result = refreshFromServerUseCase()) {
                    is RefreshFromServerUseCase.Result.Done -> {
                        BadgerLog.d(TAG, "refreshFromServer: applied=${result.applied}")
                        _refreshMessage.value =
                            if (result.applied > 0) "已同步 ${result.applied} 条变更" else "已是最新"
                    }
                    RefreshFromServerUseCase.Result.NotSignedIn -> {
                        BadgerLog.d(TAG, "refreshFromServer: not signed in, skipped")
                        _refreshMessage.value = "未登录，仅展示本地数据"
                    }
                    RefreshFromServerUseCase.Result.Failed -> {
                        BadgerLog.w(TAG, "refreshFromServer: sync failed")
                        _refreshMessage.value = "同步失败，请检查网络"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BadgerLog.e(TAG, "refreshFromServer failed", e)
                _refreshMessage.value = "同步失败，请检查网络"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun consumeRefreshMessage() {
        _refreshMessage.value = null
    }

    init {
        loadCollections()
    }

    fun loadCollections() {
        viewModelScope.launch {
            _uiState.value = CardUiState.Loading
            try {
                repository.getCollectionsWithCount().collect { list ->
                    _uiState.value = CardUiState.Success(collections = list)
                }
            } catch (e: Exception) {
                BadgerLog.e("CardViewModel", "加载名片夹失败", e)
                _uiState.value = CardUiState.Error(e.message ?: "加载名片夹失败")
            }
        }
    }

    fun createCollection(
        name: String,
        description: String?,
        backgroundImagePath: String? = null,
        dominantColor: Long? = null
    ) {
        viewModelScope.launch {
            repository.insertCollection(
                CardCollection(
                    name = name,
                    description = description,
                    backgroundImagePath = backgroundImagePath,
                    dominantColor = dominantColor,
                    createTime = nowMs(),
                )
            )
                    }
    }

    suspend fun updateCollection(collection: CardCollection) {
                repository.updateCollection(collection)
    }

    fun deleteCollection(collection: CollectionWithCount) {
        viewModelScope.launch {
            repository.deleteCollection(collection.toCacheEntity())
                    }
    }

    // --- Pass-through methods for CollectionDetailPage ---

    suspend fun getCollectionById(id: Long): CardCollection? {
                return repository.getCollectionById(id)
    }

    fun getContactsByCollectionFlow(collectionId: Long): Flow<List<Contact>> {
                return repository.getContactsByCollection(collectionId)
    }

    suspend fun getMemberCountsByCollection(collectionId: Long): Map<Long, Int> {
                return withContext(BadgerDispatchers.io) {
            repository.getMemberCountsByCollection(collectionId)
        }
    }

    suspend fun removeContactFromCollection(contactId: Long, collectionId: Long) {
                repository.removeContactFromCollection(contactId, collectionId)
    }

    suspend fun removeContactsFromCollection(contactIds: List<Long>, collectionId: Long) {
                repository.removeContactsFromCollection(contactIds, collectionId)
    }

    suspend fun addContactToCollection(contactId: Long, collectionId: Long, sourceType: String = "manual") {
                repository.addContactToCollection(contactId, collectionId, sourceType)
    }

    suspend fun deleteCollectionDirect(collection: CardCollection) {
                repository.deleteCollection(collection)
    }

    suspend fun getContactById(id: Long): Contact? {
        return contactRepository.getContactById(id)
    }

    suspend fun analyzeImportConflicts(json: String): List<ImportConflict> {
                return analyzeImportConflictsTopLevel(contactRepository, fieldRepository, repository, json)
    }

    suspend fun exportCollectionToJson(ids: List<Long>): String {
                return exportToJsonTopLevel(contactRepository, fieldRepository, repository, tagRepository, ids)
    }

    fun searchAvailableContacts(
        query: String,
        existingContactIds: Set<Long>
    ): Flow<List<Contact>> = contactRepository.searchContacts(query)
        .map { contacts -> contacts.filterNot { it.id in existingContactIds } }

    suspend fun executeImport(
        conflicts: List<ImportConflict>,
        collectionActions: Map<Int, CollectionConflictAction>,
        contactActions: Map<Int, ContactConflictAction>,
        renamedCollectionNames: Map<Int, String>,
        contactAddStyle: Map<Int, Boolean>
    ): ImportResult = executeImportTopLevel(
        contactRepository = contactRepository,
        fieldRepository = fieldRepository,
        collectionRepository = repository,
        tagRepository = tagRepository,
        conflicts = conflicts,
        collectionActions = collectionActions,
        contactActions = contactActions,
        renamedCollectionNames = renamedCollectionNames,
        contactAddStyle = contactAddStyle
    )

    private companion object {
        const val TAG = "CardViewModel"
    }
}
