package top.mcxiafeng.badger.viewmodels

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
import top.mcxiafeng.badger.data.ContactRepository

sealed interface CardUiState {
    data object Loading : CardUiState
    data class Success(
        val collections: List<CollectionWithCount> = emptyList()
    ) : CardUiState
    data class Error(val message: String) : CardUiState
}

@HiltViewModel
class CardViewModel @Inject constructor(
    val repository: ContactRepository
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

    /** 创建名片夹 */
    fun createCollection(name: String, description: String?) {
        viewModelScope.launch {
            repository.insertCollection(
                CardCollection(name = name, description = description)
            )
        }
    }

    /** 删除名片夹 */
    fun deleteCollection(collection: CollectionWithCount) {
        viewModelScope.launch {
            repository.deleteCollection(collection.collection)
        }
    }
}
