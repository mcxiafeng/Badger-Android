package top.mcxiafeng.badger.pages.person

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.PersonContactAnchor
import top.mcxiafeng.badger.data.PersonContactRow
import top.mcxiafeng.badger.data.PersonContactSection
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.QAuxvFriendImporter
import top.mcxiafeng.badger.data.QAuxvImportProgress
import top.mcxiafeng.badger.data.QAuxvImportSummary
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.account.AccountScopedContext
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.utils.Methods
import javax.inject.Inject

@Immutable
data class PersonSelectionState(
    val enabled: Boolean = false,
    val ids: Set<Long> = emptySet(),
    val selectingAll: Boolean = false,
    val allSelected: Boolean = false,
    val deleting: Boolean = false,
)

@Immutable
internal data class ContactSharePlatform(
    val key: String,
    val label: String,
    val qrContent: String,
    val accountId: String?,
)

/** The scope owning this session is cancelled when the applied query changes. */
class PersonPagingSession(
    val query: String,
    val rows: Flow<PagingData<PersonContactRow>>,
    val sections: Flow<List<PersonContactSection>>,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class PersonViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val userProfileRepository: UserProfileRepository,
    private val tagRepository: TagRepository,
    @AccountScopedContext private val appContext: Context,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private var sessionScope = newSessionScope()
    private val _pagingSession = MutableStateFlow(createSession(""))
    val pagingSession = _pagingSession.asStateFlow()
    val letterCounts = repository.getLetterIndex()
    val contactCount: StateFlow<Int> = letterCounts
        .map { items ->
            val total = items.sumOf { letter -> letter.count }
            Log.d(TAG, "联系人总数更新为 $total")
            total
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)
    private val loadedIds = MutableStateFlow<List<Long>>(emptyList())
    val contactTagsMap: StateFlow<Map<Long, List<Tag>>> = loadedIds.flatMapLatest { ids ->
        if (ids.isEmpty()) flowOf(emptyMap()) else tagRepository.observeTagsForContacts(ids)
            .map { tags -> tags.mapValues { (_, values) -> values.filter { it.showDot } } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())
    private val _selection = MutableStateFlow(PersonSelectionState())
    val selection = _selection.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    init {
        viewModelScope.launch {
            _searchQuery.debounce(SEARCH_DEBOUNCE_MS).collect { raw ->
                val query = raw.trim()
                if (query != _pagingSession.value.query) changeSession(query)
            }
        }
        viewModelScope.launch {
            combine(userProfileRepository.getUserProfile(), repository.observeContactById(MY_CONTACT_ID)) { row, me ->
                UserProfile(name = me?.name ?: row?.name.orEmpty(), avatarPath = me?.avatarPath,
                    cardImagePath = row?.cardImagePath, bio = me?.bio, defaultPlatform = row?.defaultPlatform,
                    updateTime = me?.updateTime ?: row?.updateTime ?: 0L)
            }.collect { _userProfile.value = it }
        }
    }

    private fun newSessionScope() = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))

    private fun createSession(query: String): PersonPagingSession {
        Log.d(TAG, "Create bounded database pager; search=${query.isNotEmpty()}")
        return PersonPagingSession(query,
            Pager(PAGING_CONFIG) { repository.personPagingSource(query) }.flow.cachedIn(sessionScope),
            repository.observePersonSections(query))
    }

    private fun changeSession(query: String) {
        sessionScope.cancel()
        sessionScope = newSessionScope()
        loadedIds.value = emptyList()
        _selection.value = PersonSelectionState()
        _pagingSession.value = createSession(query)
        Log.d(TAG, "Search session changed; old pages and selection released")
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun updateLoadedContactIds(ids: List<Long>) {
        val bounded = ids.distinct().sorted()
        if (bounded != loadedIds.value) {
            loadedIds.value = bounded
            Log.d(TAG, "Observe tags for ${bounded.size} loaded contacts")
        }
    }

    /** A picker can open with an existing choice already checked. */
    fun enterSelection(initial: Set<Long> = emptySet()) {
        if (_selection.value.enabled) return
        _selection.value = PersonSelectionState(enabled = true, ids = initial.filter { it != MY_CONTACT_ID }.toSet())
        Log.d(TAG, "Entered contact selection; preselected=${_selection.value.ids.size}")
    }

    fun toggleSelection(id: Long) {
        val state = _selection.value
        if (id == MY_CONTACT_ID || !state.enabled || state.selectingAll || state.deleting) return
        Log.d(TAG, "Toggle contact selection")
        _selection.value = state.copy(ids = if (id in state.ids) state.ids - id else state.ids + id,
            allSelected = false)
    }

    fun exitSelection() { _selection.value = PersonSelectionState() }

    fun toggleSelectAll() {
        val selection = _selection.value
        if (!selection.enabled || selection.selectingAll || selection.deleting) return
        if (selection.allSelected) {
            _selection.value = PersonSelectionState(enabled = true)
            return
        }
        val query = _pagingSession.value.query
        val pending = selection.copy(selectingAll = true)
        _selection.value = pending
        viewModelScope.launch {
            try {
                val ids = repository.getPersonContactIds(query).toSet()
                if (_selection.value === pending) {
                    _selection.value = PersonSelectionState(enabled = true, ids = ids, allSelected = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Select-all failed", error)
                if (_selection.value === pending) _message.value = "全选失败，请重试"
            } finally {
                // Exiting and reopening selection can happen while the same query remains active.
                if (_selection.value === pending) _selection.value = selection
            }
        }
    }

    suspend fun resolveAnchor(query: String, anchor: PersonContactAnchor): Int = repository.getPersonAnchorPosition(query, anchor)

    internal suspend fun loadContactSharePlatforms(contactId: Long): Result<List<ContactSharePlatform>> = try {
        val platforms = repository.getContactPlatforms(contactId).mapNotNull { platform ->
            val qrContent = platform.jumpLink.ifBlank { platform.value.orEmpty() }
            if (qrContent.isBlank()) return@mapNotNull null
            val label = FIELD_DEF_MAP[platform.platformKey]?.displayName ?: platform.platformKey
            val accountId = platform.value?.trim()?.takeIf { value ->
                // Older records may store a URL in the account field too.
                platform.platformKey != "website" && ACCOUNT_ID_PATTERN.matches(value) &&
                    !value.startsWith("www.", ignoreCase = true)
            }
            ContactSharePlatform(platform.platformKey, label, qrContent, accountId)
        }
        Log.d(TAG, "Loaded ${platforms.size} shareable contact platforms; ${platforms.count { it.accountId != null }} with account IDs")
        Result.success(platforms)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "Could not load contact share platforms", error)
        Result.failure(error)
    }

    internal suspend fun renderContactShareQr(content: String, backgroundColor: Int): Result<Bitmap> = withContext(Dispatchers.Default) {
        try {
            val bitmap = Methods.generateQRCode(
                content, CONTACT_SHARE_QR_SIZE_PX, Color.BLACK,
                backgroundColor = backgroundColor,
                quietZoneModules = CONTACT_SHARE_QR_QUIET_ZONE,
            )
            Log.d(TAG, "Generated contact share QR; whiteBackground=${backgroundColor == Color.WHITE}")
            Result.success(bitmap)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not generate contact share QR (${error.javaClass.simpleName})")
            Result.failure(error)
        }
    }

    /** Returns only after the database write; failures leave selection and rows intact. */
    suspend fun deleteSelectedContacts(): Boolean {
        val selection = _selection.value
        if (!selection.enabled || selection.ids.isEmpty() || selection.selectingAll || selection.deleting) return false
        val ids = selection.ids.toList()
        val pending = selection.copy(deleting = true)
        _selection.value = pending
        return try {
            repository.deleteByIds(ids)
            Log.d(TAG, "Deleted ${ids.size} contacts in database")
            _message.value = "已删除 ${ids.size} 个联系人"
            if (_selection.value === pending) exitSelection()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Contact deletion failed", error)
            _message.value = "删除失败，请重试"
            false
        } finally {
            if (_selection.value === pending) _selection.value = selection
        }
    }

    fun consumeMessage() { _message.value = null }

    fun refreshUserProfile() {
        viewModelScope.launch {
            val row = userProfileRepository.getUserProfileOnce()
            val me = repository.getContactById(MY_CONTACT_ID)
            _userProfile.value = UserProfile(name = me?.name ?: row?.name.orEmpty(), avatarPath = me?.avatarPath,
                cardImagePath = row?.cardImagePath, bio = me?.bio, defaultPlatform = row?.defaultPlatform,
                updateTime = me?.updateTime ?: row?.updateTime ?: 0L)
            Log.d(TAG, "Refreshed profile header")
        }
    }

    // ========== QAuxv 导入流程 ==========
    private val _qaImportState = MutableStateFlow<QAuxvImportState>(QAuxvImportState.Idle)
    val qaImportState: StateFlow<QAuxvImportState> = _qaImportState.asStateFlow()
    private val _qaImportResult = MutableStateFlow<QAuxvImportSummary?>(null)
    val qaImportResult = _qaImportResult.asStateFlow()
    private val _qaImportError = MutableStateFlow<String?>(null)
    val qaImportError = _qaImportError.asStateFlow()
    private val _qaImportProgress = MutableStateFlow<QAuxvImportProgress?>(null)
    val qaImportProgress = _qaImportProgress.asStateFlow()

    fun onQAuxvFileSelected(uri: Uri) {
        viewModelScope.launch {
            _qaImportState.value = QAuxvImportState.Parsing
            _qaImportError.value = null
            try {
                val text = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalArgumentException("无法读取文件")
                }
                val entries = QAuxvFriendImporter.parse(text)
                Log.d(TAG, "onQAuxvFileSelected: parsed ${entries.size} entries")
                val existing = repository.findExistingQQContacts(entries)
                _qaImportState.value = QAuxvImportState.Preview(entries, existing, entries.map { it.uin }.toSet())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "onQAuxvFileSelected failed", error)
                _qaImportError.value = error.message ?: "解析文件失败"
                _qaImportState.value = QAuxvImportState.Idle
            }
        }
    }

    fun togglePreviewCheck(uin: Long, checked: Boolean) {
        val current = _qaImportState.value as? QAuxvImportState.Preview ?: return
        _qaImportState.value = current.copy(checkedUins = if (checked) current.checkedUins + uin else current.checkedUins - uin)
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
        Log.d(TAG, "Cancel import")
        _qaImportState.value = QAuxvImportState.Idle
    }

    fun commitImport(decisions: List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>) {
        viewModelScope.launch {
            _qaImportState.value = QAuxvImportState.Importing
            _qaImportProgress.value = null
            try {
                _qaImportResult.value = repository.importQAuxvFriends(decisions, appContext) { _qaImportProgress.value = it }
                Log.d(TAG, "QAuxv import completed")
                _qaImportState.value = QAuxvImportState.Idle
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "commitImport failed", error)
                _qaImportError.value = error.message ?: "导入失败"
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
        private const val TAG = "PersonViewModelTester"
        private const val CONTACT_SHARE_QR_SIZE_PX = 512
        private const val CONTACT_SHARE_QR_QUIET_ZONE = 4
        private val ACCOUNT_ID_PATTERN = Regex("""@?[\p{L}\p{N}_][\p{L}\p{N}_.-]*""")
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val STOP_TIMEOUT_MS = 5_000L
        const val PAGE_SIZE = 30
        const val MAX_CACHED_CONTACTS = 180
        val PAGING_CONFIG = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE * 2,
            prefetchDistance = PAGE_SIZE / 2, enablePlaceholders = true,
            maxSize = MAX_CACHED_CONTACTS, jumpThreshold = PAGE_SIZE * 3)
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
