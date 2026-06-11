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
import kotlinx.coroutines.flow.drop
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

    private val _refreshTick = MutableStateFlow(0L)
    val refreshTick: StateFlow<Long> = _refreshTick.asStateFlow()

    init {
        Log.d("Tester", "PersonViewModel: collecting userProfile")
        // 直接订阅 Room 的 Flow，保证 DB 写入后能持续刷新（兜底）。
        viewModelScope.launch {
            userProfileRepository.getUserProfile().collect { profile ->
                _userProfile.value = profile
            }
        }
        // 显式刷新通道：详情页通过 onRefreshData 回调递增 tick，
        // 这里用 drop(1) 跳过初始值，再用 launchIn-style collect 拉一次最新 UserProfile。
        viewModelScope.launch {
            refreshTick
                .drop(1)
                .collect {
                    val latest = withContext(Dispatchers.IO) {
                        userProfileRepository.getUserProfileOnce()
                    }
                    _userProfile.value = latest
                    Log.d("Tester", "PersonViewModel: refresh tick reloaded profile name=${latest?.name} avatarPath=${latest?.avatarPath}")
                }
        }
        // 启动时立即拉一次，避免等待 Room Flow 异步首值。
        viewModelScope.launch {
            _userProfile.value = userProfileRepository.getUserProfileOnce()
        }
    }

    /** 详情页同步信息后调用，强制把 UserProfile 拉回最新值。 */
    fun refreshUserProfile() {
        viewModelScope.launch {
            val latest = userProfileRepository.getUserProfileOnce()
            _userProfile.value = latest
            Log.d("Tester", "PersonViewModel: refreshUserProfile pulled name=${latest?.name} avatarPath=${latest?.avatarPath}")
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
