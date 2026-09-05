package top.mcxiafeng.badger.pages.card

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _uiState = MutableStateFlow<CardUiState>(CardUiState.Loading)
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

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
}
