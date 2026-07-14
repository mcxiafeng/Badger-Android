package top.mcxiafeng.badger.pages.card

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.CollectionWithCount
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ImportConflict
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

@HiltViewModel
class CardViewModel @Inject constructor(
    private val repository: CollectionRepository,
    private val contactRepository: ContactRepository,
    private val fieldRepository: FieldRepository,
    private val tagRepository: TagRepository
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
        dominantColor: Long? = null
    ) {
        viewModelScope.launch {
            repository.insertCollection(
                CardCollection(
                    name = name,
                    description = description,
                    backgroundImagePath = backgroundImagePath,
                    dominantColor = dominantColor
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
            repository.deleteCollection(collection.collection)
            Log.d("Tester", "deleteCollection: id=${collection.collection.id}")
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

    // Internal accessors for dialogs that need repository objects
    internal fun getContactRepository(): ContactRepository {
        Log.d("Tester", "CardViewModel.getContactRepository")
        return contactRepository
    }

    internal fun getFieldRepository(): FieldRepository {
        Log.d("Tester", "CardViewModel.getFieldRepository")
        return fieldRepository
    }

    internal fun getCollectionRepository(): CollectionRepository {
        Log.d("Tester", "CardViewModel.getCollectionRepository")
        return repository
    }

    internal fun getTagRepository(): TagRepository {
        Log.d("Tester", "CardViewModel.getTagRepository")
        return tagRepository
    }
}
