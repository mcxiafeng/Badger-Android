package top.mcxiafeng.badger.pages.card

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.CollectionWithCount
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository

sealed interface CardUiState {
    data object Loading : CardUiState
    data class Success(
        val collections: List<CollectionWithCount> = emptyList()
    ) : CardUiState
    data class Error(val message: String) : CardUiState
}

@HiltViewModel
class CardViewModel @Inject constructor(
    val repository: CollectionRepository,
    val contactRepository: ContactRepository,
    val fieldRepository: FieldRepository
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
}
