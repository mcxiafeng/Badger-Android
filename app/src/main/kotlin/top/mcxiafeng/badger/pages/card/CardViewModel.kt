package top.mcxiafeng.badger.pages.card

import android.util.Log
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
import top.mcxiafeng.badger.data.CardCollectionWithCount as CollectionWithCount
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.ImportConflict
import top.mcxiafeng.badger.data.ContactConflictAction
import top.mcxiafeng.badger.data.CollectionConflictAction
import top.mcxiafeng.badger.data.ImportResult
import top.mcxiafeng.badger.data.executeImport as executeImportTopLevel
import top.mcxiafeng.badger.data.analyzeImportConflicts as analyzeImportConflictsTopLevel
import top.mcxiafeng.badger.data.exportToJson as exportToJsonTopLevel
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository

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
                Log.e("CardViewModel", "加载名片夹失败", e)
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
                    createTime = System.currentTimeMillis(),
                )
            )
            Log.d("Tester", "createCollection: name=$name, bgPath=$backgroundImagePath, dominantColor=$dominantColor")
        }
    }

    suspend fun updateCollection(collection: CardCollection) {
        Log.d("Tester", "updateCollection: id=${collection.id}, name=${collection.name}, bgPath=${collection.backgroundImagePath}, dominantColor=${collection.dominantColor}")
        repository.updateCollection(collection)
    }

    fun deleteCollection(collection: CollectionWithCount) {
        viewModelScope.launch {
            repository.deleteCollection(collection.toCacheEntity())
            Log.d("Tester", "deleteCollection: id=${collection.id}")
        }
    }

    // --- Pass-through methods for CollectionDetailPage ---

    suspend fun getCollectionById(id: Long): CardCollection? {
        Log.d("Tester", "CardViewModel.getCollectionById: id=$id")
        return repository.getCollectionById(id)
    }

    fun getContactsByCollectionFlow(collectionId: Long): Flow<List<Contact>> {
        Log.d("Tester", "CardViewModel.getContactsByCollectionFlow: collectionId=$collectionId")
        return repository.getContactsByCollection(collectionId)
    }

    suspend fun getScanRecordCountsByCollection(collectionId: Long): Map<Long, Int> {
        Log.d("Tester", "CardViewModel.getScanRecordCountsByCollection: collectionId=$collectionId")
        return withContext(Dispatchers.IO) {
            repository.getScanRecordCountsByCollection(collectionId)
        }
    }

    suspend fun removeContactFromCollection(contactId: Long, collectionId: Long) {
        Log.d("Tester", "CardViewModel.removeContactFromCollection: contactId=$contactId, collectionId=$collectionId")
        repository.removeContactFromCollection(contactId, collectionId)
    }

    suspend fun removeContactsFromCollection(contactIds: List<Long>, collectionId: Long) {
        Log.d("Tester", "CardViewModel.removeContactsFromCollection: count=${contactIds.size}, collectionId=$collectionId")
        repository.removeContactsFromCollection(contactIds, collectionId)
    }

    suspend fun addContactToCollection(contactId: Long, collectionId: Long, sourceType: String = "manual") {
        Log.d("Tester", "CardViewModel.addContactToCollection: contactId=$contactId, collectionId=$collectionId")
        repository.addContactToCollection(contactId, collectionId, sourceType)
    }

    suspend fun deleteCollectionDirect(collection: CardCollection) {
        Log.d("Tester", "CardViewModel.deleteCollectionDirect: id=${collection.id}")
        repository.deleteCollection(collection)
    }

    suspend fun getContactById(id: Long): Contact? {
        return contactRepository.getContactById(id)
    }

    suspend fun analyzeImportConflicts(json: String): List<ImportConflict> {
        Log.d("Tester", "CardViewModel.analyzeImportConflicts")
        return analyzeImportConflictsTopLevel(contactRepository, fieldRepository, repository, json)
    }

    suspend fun exportCollectionToJson(ids: List<Long>): String {
        Log.d("Tester", "CardViewModel.exportCollectionToJson: ids=${ids.size}")
        return exportToJsonTopLevel(contactRepository, fieldRepository, repository, tagRepository, ids)
    }

    fun searchAvailableContacts(
        query: String,
        existingContactIds: Set<Long>
    ): Flow<List<Contact>> = contactRepository.searchContacts(query)
        .map { contacts -> contacts.filterNot { it.id in existingContactIds } }

    suspend fun executeImport(
        conflicts: List<ImportConflict>,
        collectionActions: Map<String, CollectionConflictAction>,
        contactActions: Map<String, ContactConflictAction>,
        renamedCollectionNames: Map<String, String>,
        contactAddStyle: Map<String, Boolean>
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
