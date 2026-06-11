package top.mcxiafeng.badger.pages.person

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import javax.inject.Inject

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    val contactsPagingData: Flow<PagingData<Contact>> = Pager(
        config = PagingConfig(pageSize = 30, enablePlaceholders = false)
    ) {
        repository.getAllContactsPagingSource()
    }.flow.cachedIn(viewModelScope)

    val letterCounts: Flow<List<LetterCount>> = repository.getLetterIndex()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResultsPagingData: Flow<PagingData<Contact>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(PagingData.empty())
            else repository.searchContactsPagingSource(query)
        }
        .cachedIn(viewModelScope)

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    init {
        Log.d("Tester", "PersonViewModel: collecting userProfile")
        viewModelScope.launch {
            userProfileRepository.getUserProfile().collect { profile ->
                _userProfile.value = profile
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun deleteContacts(ids: List<Long>) {
        Log.d("Tester", "PersonViewModel.deleteContacts: count=${ids.size}")
        withContext(Dispatchers.IO) {
            repository.deleteByIds(ids)
        }
    }
}
