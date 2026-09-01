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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository

/**
 * 搜索结果分组结构。
 *
 * - [nameHits]: 按联系人名字 / 字段值 FTS+LIKE 命中的联系人（去重）
 * - [tagHits]: 按 Tag 名字命中的标签 + 该标签下的联系人列表（每个 tag 独立一组）
 */
data class PersonSearchResult(
    val nameHits: List<Contact>,
    val tagHits: List<TagHitGroup>,
)

data class TagHitGroup(
    val tag: Tag,
    val contacts: List<Contact>,
)

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
    private val _contactsLoadedFromDb = MutableStateFlow(false)

    // [V2-P1.5] Paging 抽取: contacts 改为 StateFlow<List<Contact>> + LazyColumn(items(...))。
    // 删除走 mutate in-memory list + key-based diff，scroll position 零漂移。
    val contacts: StateFlow<List<Contact>> = _allContacts

    /**
     * 当前列表中所有联系人的 Tag 映射（contactId → 该联系人所有 showDot=true 的 Tag）。
     * Room 失效通知会自动刷新关联标签。
     */
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
     * - [PersonSearchResult.nameHits]: FTS+LIKE 命中的联系人
     * - [PersonSearchResult.tagHits]: Tag 名字命中的标签 + 该 Tag 下的联系人
     *
     * 同一联系人既被名字命中又被标签命中时只保留在 nameHits 中，避免重复展示。
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

    private val _refreshTick = MutableStateFlow(0L)
    val refreshTick: StateFlow<Long> = _refreshTick.asStateFlow()

    init {
        Log.d(TAG, "PersonViewModel: collecting userProfile")
        viewModelScope.launch {
            userProfileRepository.getUserProfile().collect { profile ->
                _userProfile.value = profile
            }
        }
        viewModelScope.launch {
            refreshTick
                .drop(1)
                .collect {
                    val latest = withContext(Dispatchers.IO) {
                        userProfileRepository.getUserProfileOnce()
                    }
                    _userProfile.value = latest
                    Log.d(
                        TAG,
                        "PersonViewModel: refresh tick reloaded profile name=${latest?.name} avatarPath=${latest?.avatarPath}",
                    )
                }
        }
        viewModelScope.launch {
            _userProfile.value = userProfileRepository.getUserProfileOnce()
        }

        // Room Flow is the source of truth. The in-memory mutation in deleteContacts()
        // only provides immediate visual feedback before the durable delete completes.
        repository.getAllContacts()
            .onEach { list ->
                _allContacts.value = list
                _contactsLoadedFromDb.value = true
                Log.d(TAG, "PersonViewModel.init: Room pushed fresh contacts count=${list.size}")
            }
            .launchIn(viewModelScope)
    }

    fun refreshUserProfile() {
        viewModelScope.launch {
            val latest = userProfileRepository.getUserProfileOnce()
            _userProfile.value = latest
            Log.d(
                TAG,
                "PersonViewModel: refreshUserProfile pulled name=${latest?.name} avatarPath=${latest?.avatarPath}",
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Bulk delete with optimistic in-memory removal followed by the repository's
     * durable delete + recovery path.
     */
    fun deleteContacts(ids: List<Long>) {
        if (ids.isEmpty()) return
        Log.d(TAG, "PersonViewModel.deleteContacts: count=${ids.size} ids=$ids")

        val current = _allContacts.value
        val idsSet = ids.toSet()
        _allContacts.value = current.filterNot { it.id in idsSet }
        Log.d(
            TAG,
            "PersonViewModel.deleteContacts: in-memory list mutated, " +
                "removed=${current.size - _allContacts.value.size}, now=${_allContacts.value.size}",
        )

        viewModelScope.launch(Dispatchers.IO) {
            for (id in ids) {
                try {
                    val result = repository.commitDelete(id)
                    Log.d(TAG, "PersonViewModel.deleteContacts: commitDelete($id) → $result")
                } catch (e: Exception) {
                    Log.e(TAG, "PersonViewModel.deleteContacts: commitDelete($id) failed", e)
                }
            }
        }
    }

    // ========== QAuxv 导入流程 ==========

    private val _qaImportState = MutableStateFlow<QAuxvImportState>(QAuxvImportState.Idle)
    val qaImportState: StateFlow<QAuxvImportState> = _qaImportState.asStateFlow()

    private val _qaImportResult = MutableStateFlow<QAuxvImportSummary?>(null)
    val qaImportResult: StateFlow<QAuxvImportSummary?> = _qaImportResult.asStateFlow()

    private val _qaImportError = MutableStateFlow<String?>(null)
    val qaImportError: StateFlow<String?> = _qaImportError.asStateFlow()

    /** 实时进度（写入阶段 / 头像下载阶段），null 表示无进度信息。 */
    private val _qaImportProgress = MutableStateFlow<QAuxvImportProgress?>(null)
    val qaImportProgress: StateFlow<QAuxvImportProgress?> = _qaImportProgress.asStateFlow()

    /** 用户在 SAF 中选中文件后触发：读取 → 解析 → 查重 → 切到 Preview 状态。 */
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
                Log.d(TAG, "onQAuxvFileSelected: parsed ${entries.size} entries")
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

    /** 取消整个导入流程（Preview/Importing 状态下均可调用，回到 Idle）。 */
    fun cancelImport() {
        val prev = _qaImportState.value
        Log.d(TAG, "cancelImport: previous state=$prev")
        _qaImportState.value = QAuxvImportState.Idle
    }

    /**
     * 提交导入。按用户在 Preview/Conflict Dialog 中的决定写入。
     *
     * @param decisions 三元组列表 (entry, existingContactId?, action)
     */
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
                Log.d(TAG, "commitImport: summary=$summary")
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

/**
 * QAuxv 导入流程状态机。
 *
 * Idle → Parsing → Preview → Importing → Idle
 *   ↑       │         │            │
 *   └───────┴─────────┴────────────┘  (cancel/错误/完成)
 */
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
