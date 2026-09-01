package top.mcxiafeng.badger.pages.card

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.CardCollectionWithCount as CollectionWithCount
import top.mcxiafeng.badger.data.CollectionConflictAction
import top.mcxiafeng.badger.data.ContactConflictAction
import top.mcxiafeng.badger.data.ImportConflict
import top.mcxiafeng.badger.data.ImportResult
import top.mcxiafeng.badger.data.analyzeImportConflicts as analyzeImportConflictsTopLevel
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.executeImport as executeImportTopLevel
import top.mcxiafeng.badger.data.exportToJson as exportToJsonTopLevel
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository

sealed interface CardUiState {
    data object Loading : CardUiState
    data class Success(
        val collections: List<CollectionWithCount> = emptyList(),
    ) : CardUiState
    data class Error(val message: String) : CardUiState
}

/**
 * ViewModel for collection management and collection import/export flows.
 * Dependencies are provided by Koin constructor injection so the ViewModel
 * remains deterministic and straightforward to test.
 */
class CardViewModel(
    private val repository: CollectionRepository,
    private val contactRepository: ContactRepository,
    private val fieldRepository: FieldRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

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
        dominantColor: Long? = null,
    ) {
        viewModelScope.launch {
            repository.insertCollection(
                CardCollection(
                    name = name,
                    description = description,
                    backgroundImagePath = backgroundImagePath,
                    dominantColor = dominantColor,
                    createTime = System.currentTimeMillis(),
                ),
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

    suspend fun getCollectionById(id: Long): CardCollection? =
        repository.getCollectionById(id)

    fun getContactsByCollectionFlow(collectionId: Long): Flow<List<Contact>> =
        repository.getContactsByCollection(collectionId)

    suspend fun getMemberCountsByCollection(collectionId: Long): Map<Long, Int> =
        withContext(Dispatchers.IO) {
            repository.getMemberCountsByCollection(collectionId)
        }

    suspend fun removeContactFromCollection(contactId: Long, collectionId: Long) {
        repository.removeContactFromCollection(contactId, collectionId)
    }

    suspend fun removeContactsFromCollection(contactIds: List<Long>, collectionId: Long) {
        repository.removeContactsFromCollection(contactIds, collectionId)
    }

    suspend fun addContactToCollection(
        contactId: Long,
        collectionId: Long,
        sourceType: String = "manual",
    ) {
        repository.addContactToCollection(contactId, collectionId, sourceType)
    }

    suspend fun deleteCollectionDirect(collection: CardCollection) {
        repository.deleteCollection(collection)
    }

    suspend fun getContactById(id: Long): Contact? =
        contactRepository.getContactById(id)

    suspend fun analyzeImportConflicts(json: String): List<ImportConflict> =
        analyzeImportConflictsTopLevel(
            contactRepository,
            fieldRepository,
            repository,
            json,
        )

    suspend fun exportCollectionToJson(ids: List<Long>): String =
        exportToJsonTopLevel(
            contactRepository,
            fieldRepository,
            repository,
            tagRepository,
            ids,
        )

    fun searchAvailableContacts(
        query: String,
        existingContactIds: Set<Long>,
    ): Flow<List<Contact>> =
        contactRepository.searchContacts(query)
            .map { contacts -> contacts.filterNot { it.id in existingContactIds } }

    suspend fun executeImport(
        conflicts: List<ImportConflict>,
        collectionActions: Map<String, CollectionConflictAction>,
        contactActions: Map<String, ContactConflictAction>,
        renamedCollectionNames: Map<String, String>,
        contactAddStyle: Map<String, Boolean>,
    ): ImportResult =
        executeImportTopLevel(
            contactRepository = contactRepository,
            fieldRepository = fieldRepository,
            collectionRepository = repository,
            tagRepository = tagRepository,
            conflicts = conflicts,
            collectionActions = collectionActions,
            contactActions = contactActions,
            renamedCollectionNames = renamedCollectionNames,
            contactAddStyle = contactAddStyle,
        )
}
