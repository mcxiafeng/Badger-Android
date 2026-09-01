package top.mcxiafeng.badger.pages.person

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.QAuxvFriendImporter
import top.mcxiafeng.badger.data.QAuxvImportProgress
import top.mcxiafeng.badger.data.QAuxvImportSummary
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.CommitResult
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository

data class PersonSearchResult(
    val nameHits: List<Contact>,
    val tagHits: List<TagHitGroup>,
)

data class TagHitGroup(
    val tag: Tag,
    val contacts: List<Contact>,
)

data class DeleteContactsResult(
    val requested: Int,
    val succeeded: Int,
    val failed: Int,
) {
    val skipped: Int get() = (requested - succeeded - failed).coerceAtLeast(0)
}

/**
 * ViewModel for the people list, search, bulk deletion and QAuxv import flow.
 * Dependencies are constructor-injected so UI state remains independent of the
 * global Koin service locator and the class stays deterministic in tests.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PersonViewModel(
    private val repository: ContactRepository,
    private val userProfileRepository: UserProfileRepository,
    private val tagRepository: TagRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())

    // Room Flow is the source of truth for the contact list.
    val contacts: StateFlow<List<Contact>> = _allContacts

    /** 当前列表中所有联系人的 Tag 映射（contactId → 该联系人所有 showDot=true 的 Tag）。 */
    val contactTagsMap: StateFlow<Map<Long, List<Tag>>> = _allContacts
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyMap())
            } else {
                tagRepository.observeTagsForContacts(list.map { it.id })
                    .map { allMap ->
                        allMap.mapValues { (_, tags) -> tags.filter { it.showDot } }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val letterCounts: Flow<List<LetterCount>> = repository.getLetterIndex()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 搜索结果（分组渲染）：
     * - nameHits: FTS+LIKE 命中的联系人
     * - tagHits: Tag 名字命中的标签 + 该 Tag 下的联系人
     */
    val searchResults: StateFlow<PersonSearchResult> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(PersonSearchResult(emptyList(), emptyList()))
            } else {
                flow {
                    val result = withContext(Dispatchers.IO) {
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
                    Log.d(
                        TAG,
                        "PersonSearchResult built: nameHits=${result.nameHits.size} tagHits=${result.tagHits.size}",
                    )
                    emit(result)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PersonSearchResult(emptyList(), emptyList()))

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    init {
        viewModelScope.launch {
            userProfileRepository.getUserProfile().collect { profile ->
                _userProfile.value = profile
            }
        }

        repository.getAllContacts()
            .onEach { list ->
                _allContacts.value = list
                Log.d(TAG, "PersonViewModel: Room pushed fresh contacts count=${list.size}")
            }
            .launchIn(viewModelScope)
    }

    fun refreshUserProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            _userProfile.value = userProfileRepository.getUserProfileOnce()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Bulk delete with optimistic in-memory removal followed by the repository's durable delete.
     * The caller awaits the result so UI feedback reflects the actual outcome instead of the
     * fire-and-forget coroutine finishing immediately.
     */
    suspend fun deleteContacts(ids: List<Long>): DeleteContactsResult {
        val uniqueIds = ids.distinct()
        if (uniqueIds.isEmpty()) return DeleteContactsResult(0, 0, 0)

        val current = _allContacts.value
        val idsSet = uniqueIds.toSet()
        _allContacts.value = current.filterNot { it.id in idsSet }

        var succeeded = 0
        var failed = 0
        for (id in uniqueIds) {
            try {
                when (repository.commitDelete(id)) {
                    CommitResult.SentSuccess, CommitResult.NotFound -> succeeded++
                    is CommitResult.SentFailed -> failed++
                }
            } catch (e: Exception) {
                failed++
                Log.e(TAG, "PersonViewModel.deleteContacts: commitDelete($id) failed", e)
            }
        }
        return DeleteContactsResult(
            requested = uniqueIds.size,
            succeeded = succeeded,
            failed = failed,
        )
    }

    // ========== QAuxv 导入流程 ==========

    private val _qaImportState = MutableStateFlow<QAuxvImportState>(QAuxvImportState.Idle)
    val qaImportState: StateFlow<QAuxvImportState> = _qaImportState.asStateFlow()

    private val _qaImportResult = MutableStateFlow<QAuxvImportSummary?>(null)
    val qaImportResult: StateFlow<QAuxvImportSummary?> = _qaImportResult.asStateFlow()

    private val _qaImportError = MutableStateFlow<String?>(null)
    val qaImportError: StateFlow<String?> = _qaImportError.asStateFlow()

    private val _qaImportProgress = MutableStateFlow<QAuxvImportProgress?>(null)
    val qaImportProgress: StateFlow<QAuxvImportProgress?> = _qaImportProgress.asStateFlow()

    fun onQAuxvFileSelected(uri: Uri) {
        viewModelScope.launch {
            _qaImportState.value = QAuxvImportState.Parsing
            _qaImportError.value = null
            try {
                val text = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: throw IllegalArgumentException("无法读取文件")
                }
                val entries = QAuxvFriendImporter.parse(text)
                val existing = repository.findExistingQQContacts(entries)
                _qaImportState.value = QAuxvImportState.Preview(
                    entries = entries,
                    existingContactIdByUin = existing,
                    checkedUins = entries.map { it.uin }.toSet(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "onQAuxvFileSelected failed", e)
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
        _qaImportState.value = QAuxvImportState.Idle
    }

    fun commitImport(decisions: List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>) {
        viewModelScope.launch {
            _qaImportState.value = QAuxvImportState.Importing
            _qaImportProgress.value = null
            try {
                val summary = repository.importQAuxvFriends(
                    decisions = decisions,
                    context = appContext,
                    onProgress = { progress -> _qaImportProgress.value = progress },
                )
                _qaImportResult.value = summary
                _qaImportState.value = QAuxvImportState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "commitImport failed", e)
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
