package top.mcxiafeng.badger.pages.person

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.model.LetterCount
import top.mcxiafeng.badger.data.model.QAuxvConflictAction
import top.mcxiafeng.badger.data.importer.QAuxvFriendEntry
import top.mcxiafeng.badger.data.importer.QAuxvFriendImporter
import top.mcxiafeng.badger.data.model.QAuxvImportProgress
import top.mcxiafeng.badger.data.model.QAuxvImportSummary
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.domain.RefreshFromServerUseCase
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

data class PersonSearchResult(
    val nameHits: List<Contact>,
    val tagHits: List<TagHitGroup>
)
data class TagHitGroup(
    val tag: Tag,
    val contacts: List<Contact>
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PersonViewModel : ViewModel() {

    private val repository: ContactRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userProfileRepository: UserProfileRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val tagRepository: TagRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val refreshFromServerUseCase: RefreshFromServerUseCase = KoinComponentBy.get()

    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _contactsLoadedFromDb = MutableStateFlow(false)

    
    
    val contacts: StateFlow<List<Contact>> = _allContacts

    

    val contactTagsMap: StateFlow<Map<Long, List<Tag>>> = _allContacts
        .flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyMap())
            else tagRepository.observeTagsForContacts(list.map { it.id })
                .map { allMap ->
                    
                    
                    allMap.mapValues { (_, tags) -> tags.filter { it.showDot } }
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val letterCounts: Flow<List<LetterCount>> = repository.getLetterIndex()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    

    val searchResults: StateFlow<PersonSearchResult> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(PersonSearchResult(emptyList(), emptyList()))
            } else {
                flow {
                    val result = withContext(BadgerDispatchers.io) {
                        val names = repository.searchContacts(query).first()
                        val nameHitIds = names.map { it.id }.toSet()
                        val matchedTags = tagRepository.searchTagsByName(query)
                        
                        
                        val tagGroups = matchedTags.map { tag ->
                            val contacts = tagRepository.getContactsByTag(tag.id)
                                .filterNot { it.id in nameHitIds }
                            TagHitGroup(tag, contacts)
                        }.filter { it.contacts.isNotEmpty() }
                        PersonSearchResult(nameHits = names, tagHits = tagGroups)
                    }
                    BadgerLog.d(TAG, "PersonSearchResult built: nameHits=${result.nameHits.size} tagHits=${result.tagHits.size}")
                    emit(result)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PersonSearchResult(emptyList(), emptyList()))

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _refreshTick = MutableStateFlow(0L)
    val refreshTick: StateFlow<Long> = _refreshTick.asStateFlow()

    init {
        BadgerLog.d(TAG, "PersonViewModel: collecting userProfile")
        viewModelScope.launch {
            userProfileRepository.getUserProfile().collect { profile ->
                _userProfile.value = profile
            }
        }
        viewModelScope.launch {
            refreshTick
                .drop(1)
                .collect {
                    val latest = withContext(BadgerDispatchers.io) {
                        userProfileRepository.getUserProfileOnce()
                    }
                    _userProfile.value = latest
                    BadgerLog.d(TAG, "PersonViewModel: refresh tick reloaded profile name=${latest?.name} avatarPath=${latest?.avatarPath}")
                }
        }
        viewModelScope.launch {
            _userProfile.value = userProfileRepository.getUserProfileOnce()
        }

        
        
        
        repository.getAllContacts()
            .onEach { list ->
                _allContacts.value = list
                _contactsLoadedFromDb.value = true
                BadgerLog.d(
                    TAG,
                    "PersonViewModel.init: Room pushed fresh contacts count=${list.size}",
                )
            }
            .launchIn(viewModelScope)
    }

    fun refreshUserProfile() {
        viewModelScope.launch {
            val latest = userProfileRepository.getUserProfileOnce()
            _userProfile.value = latest
            BadgerLog.d(TAG, "PersonViewModel: refreshUserProfile pulled name=${latest?.name} avatarPath=${latest?.avatarPath}")
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    
    private val _refreshMessage = MutableStateFlow<String?>(null)
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()

    

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

    
    
    
    
    
    
    
    
    
    
    fun deleteContacts(ids: List<Long>) {
        if (ids.isEmpty()) return
        BadgerLog.d(TAG, "PersonViewModel.deleteContacts: count=${ids.size} ids=$ids")
        
        
        
        val current = _allContacts.value
        val idsSet = ids.toSet()
        _allContacts.value = current.filterNot { it.id in idsSet }
        BadgerLog.d(
            TAG,
            "PersonViewModel.deleteContacts: in-memory list mutated, removed=${current.size - _allContacts.value.size}, now=${_allContacts.value.size}",
        )
        
        viewModelScope.launch(BadgerDispatchers.io) {
            for (id in ids) {
                try {
                    val result = repository.commitDelete(id)
                    BadgerLog.d(TAG, "PersonViewModel.deleteContacts: commitDelete($id) → $result")
                } catch (e: Exception) {
                    
                    BadgerLog.e(TAG, "PersonViewModel.deleteContacts: commitDelete($id) failed", e)
                }
            }
        }
    }

    

    private val _qaImportState = MutableStateFlow<QAuxvImportState>(QAuxvImportState.Idle)
    val qaImportState: StateFlow<QAuxvImportState> = _qaImportState.asStateFlow()

    private val _qaImportResult = MutableStateFlow<QAuxvImportSummary?>(null)
    val qaImportResult: StateFlow<QAuxvImportSummary?> = _qaImportResult.asStateFlow()

    private val _qaImportError = MutableStateFlow<String?>(null)
    val qaImportError: StateFlow<String?> = _qaImportError.asStateFlow()

    
    private val _qaImportProgress = MutableStateFlow<QAuxvImportProgress?>(null)
    val qaImportProgress: StateFlow<QAuxvImportProgress?> = _qaImportProgress.asStateFlow()

    

    fun onQAuxvFileSelected(bytes: ByteArray) {
        viewModelScope.launch {
            _qaImportState.value = QAuxvImportState.Parsing
            _qaImportError.value = null
            try {
                val text = bytes.decodeToString()
                val entries = QAuxvFriendImporter.parse(text)
                BadgerLog.d(TAG, "onQAuxvFileSelected: parsed ${entries.size} entries")
                val existing = repository.findExistingQQContacts(entries)
                _qaImportState.value = QAuxvImportState.Preview(
                    entries = entries,
                    existingContactIdByUin = existing,
                    checkedUins = entries.map { it.uin }.toSet(),
                )
            } catch (e: Exception) {
                BadgerLog.e(TAG, "onQAuxvFileSelected failed", e)
                _qaImportError.value = e.message ?: "解析文件失败"
                _qaImportState.value = QAuxvImportState.Idle
            }
        }
    }

    fun togglePreviewCheck(uin: Long, checked: Boolean) {
        val current = _qaImportState.value as? QAuxvImportState.Preview ?: return
        val newSet = if (checked) current.checkedUins + uin else current.checkedUins - uin
        _qaImportState.value = current.copy(checkedUins = newSet)
    }

    fun selectAllPreview() {
        val current = _qaImportState.value as? QAuxvImportState.Preview ?: return
        _qaImportState.value = current.copy(checkedUins = current.entries.map { it.uin }.toSet())
    }

    fun deselectAllPreview() {
        val current = _qaImportState.value as? QAuxvImportState.Preview ?: return
        _qaImportState.value = current.copy(checkedUins = emptySet())
    }

    
    fun cancelImport() {
        val prev = _qaImportState.value
        BadgerLog.d(TAG, "cancelImport: previous state=$prev")
        _qaImportState.value = QAuxvImportState.Idle
    }

    

    fun commitImport(decisions: List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>) {
        viewModelScope.launch {
            _qaImportState.value = QAuxvImportState.Importing
            _qaImportProgress.value = null
            try {
                val summary = repository.importQAuxvFriends(
                    decisions = decisions,
                    onProgress = { progress -> _qaImportProgress.value = progress },
                )
                BadgerLog.d(TAG, "commitImport: summary=$summary")
                _qaImportResult.value = summary
                _qaImportState.value = QAuxvImportState.Idle
            } catch (e: Exception) {
                BadgerLog.e(TAG, "commitImport failed", e)
                _qaImportError.value = e.message ?: "导入失败"
                _qaImportState.value = QAuxvImportState.Idle
            } finally {
                _qaImportProgress.value = null
            }
        }
    }

    fun consumeImportResult() {
        _qaImportResult.value = null
        _qaImportError.value = null
        _qaImportProgress.value = null
    }

    companion object {
        private const val TAG = "PersonViewModel"
    }
}

@Immutable
sealed class QAuxvImportState {
    data object Idle : QAuxvImportState()
    data object Parsing : QAuxvImportState()
    data class Preview(
        val entries: List<QAuxvFriendEntry>,
        val existingContactIdByUin: Map<Long, Long>,
        val checkedUins: Set<Long>,
    ) : QAuxvImportState()
    data object Importing : QAuxvImportState()
}
