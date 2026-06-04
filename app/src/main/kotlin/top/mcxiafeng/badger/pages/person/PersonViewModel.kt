package top.mcxiafeng.badger.pages.person

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.domain.FilterContactsUseCase

sealed interface PersonUiState {
    data object Loading : PersonUiState
    data class Success(
        val contacts: List<Contact> = emptyList(),
        val filteredContacts: List<Contact> = emptyList(),
        val searchQuery: String = "",
        val sortType: Int = 0
    ) : PersonUiState
    data class Error(val message: String) : PersonUiState
}

@HiltViewModel
class PersonViewModel @Inject constructor(
    val repository: ContactRepository,
    val userProfileRepository: UserProfileRepository,
    private val filterContactsUseCase: FilterContactsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _sortType = MutableStateFlow(0)

    init {
        loadContacts()
        observeSearchAndSort()
    }

    private fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = PersonUiState.Loading
            try {
                repository.getAllContacts().collect { contacts ->
                    _uiState.value = PersonUiState.Success(contacts = contacts)
                    applyFilter(contacts, _searchQuery.value, _sortType.value)
                }
            } catch (e: Exception) {
                _uiState.value = PersonUiState.Error(e.message ?: "加载联系人失败")
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchAndSort() {
        viewModelScope.launch {
            combine(
                _searchQuery.debounce(300).distinctUntilChanged(),
                _sortType,
                ::Pair
            ).collect { (query, sort) ->
                val state = _uiState.value
                if (state is PersonUiState.Success) {
                    applyFilter(state.contacts, query, sort)
                }
            }
        }
    }

    private fun applyFilter(contacts: List<Contact>, query: String, sortType: Int) {
        val sorted = filterContactsUseCase(contacts, query, sortType)
        val current = _uiState.value
        if (current is PersonUiState.Success) {
            _uiState.value = current.copy(
                filteredContacts = sorted,
                searchQuery = query,
                sortType = sortType
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortType(sortType: Int) {
        _sortType.value = sortType
    }
}
