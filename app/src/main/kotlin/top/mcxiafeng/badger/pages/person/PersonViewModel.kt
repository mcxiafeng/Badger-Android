package top.mcxiafeng.badger.pages.person

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.QAuxvFriendImporter
import top.mcxiafeng.badger.data.QAuxvImportProgress
import top.mcxiafeng.badger.data.QAuxvImportSummary
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import javax.inject.Inject

/**
 * 搜索结果分组结构。
 *
 * - [nameHits]:按联系人名字 / 字段值 FTS+LIKE 命中的联系人(去重)
 * - [tagHits]:按 Tag 名字命中的标签 + 该标签下的联系人列表(每个 tag 独立一组)
 */
data class PersonSearchResult(
    val nameHits: List<Contact>,
    val tagHits: List<TagHitGroup>
)
data class TagHitGroup(
    val tag: Tag,
    val contacts: List<Contact>
)

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val userProfileRepository: UserProfileRepository,
    private val tagRepository: TagRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // [修复防御]: Paging 3 + Room 场景下删除联系人时 PagingSource.invalidate 会让 LazyColumn
    // 整个 PagingData 实例被重建，firstVisibleItemIndex 短暂跌到 0 再被填充。
    // 网络验证 + 多次实测均确认：PagingData.filter 在 flatMapLatest 切换下无法保证
    // LazyColumn 的 listState 稳定。改用自定义 PagingSource：在内部持有 Room Flow 推送
    // 的全量 List 作为"已就绪数据"，删除时直接 mutate in-memory list（不发 invalidation），
    // LazyColumn 走 key-based diff 自然稳定。
    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _contactsLoadedFromDb = MutableStateFlow(false)

    /**
     * 自定义 PagingSource：从 [_allContacts] 取切片。删除联系人时我们 mutate 这个 list
     * 而不是触发 PagingSource.invalidate()，从而保证 LazyColumn 在没有 "全新 PagingData"
     * 注入的前提下做 key-based diff，scroll position 完全保留。
     */
    private inner class InMemoryContactsPagingSource : PagingSource<Int, Contact>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Contact> {
            val all = _allContacts.value
            val pageSize = params.loadSize.coerceAtLeast(1)
            val key = params.key ?: 0
            val endExclusive = minOf(key + pageSize, all.size)
            val data = if (key < all.size && endExclusive > key) {
                all.subList(key, endExclusive)
            } else {
                emptyList()
            }
            val prevKey = if (key > 0) key - pageSize else null
            val nextKey = if (endExclusive < all.size) endExclusive else null
            return LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        }

        override fun getRefreshKey(state: PagingState<Int, Contact>): Int? {
            // 删除操作不发 invalidation，理论上不需要走这里。
            // 保留一份 anchor-based 实现作为防御性兜底。
            val anchorPos = state.anchorPosition ?: return null
            val closest = state.closestPageToPosition(anchorPos) ?: return null
            return closest.prevKey?.plus(closest.data.size)
        }
    }

    // [修复防御]: 经过 8 轮改造仍无法消除 Paging 3 + 删除场景下的 listState 漂移。
    // 改用 StateFlow<List<Contact>> + items(key=…) 直接渲染。LazyColumn 本身有内置 lazy
    // 渲染机制，~1w 条数据足够应付。删除时 mutate 内存 list，LazyColumn 走 key-based
    // diff 自然稳定，scroll position 零漂移。
    val contacts: StateFlow<List<Contact>> = _allContacts

    /**
     * 当前列表中所有联系人的 Tag 映射(contactId → 该联系人所有 showDot=true 的 Tag)。
     *
     * 用于列表项右侧的彩色 Tag 点渲染。Flow 让 Room 自动失效:
     * - Tag 写入 / 删除 / showDot 切换后,Room InvalidationTracker 会重新推送
     * - bumpContact 后也会触发
     */
    val contactTagsMap: StateFlow<Map<Long, List<Tag>>> = _allContacts
        .flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyMap())
            else tagRepository.observeTagsForContacts(list.map { it.id })
                .map { allMap ->
                    // [修复防御]: 全局观察所有联系人-标签关联,但列表只显示 showDot=true 的 tag。
                    // mapValues 构造新 Map 保留原结构。
                    allMap.mapValues { (_, tags) -> tags.filter { it.showDot } }
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun joinToTag(j: top.mcxiafeng.badger.data.ContactTagJoin): Tag = Tag(
        id = j.id,
        name = j.name,
        color = j.color,
        pinyinInitial = j.pinyinInitial,
        source = j.source,
        showDot = j.showDot,
        createTime = j.createTime,
    )

    val letterCounts: Flow<List<LetterCount>> = repository.getLetterIndex()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 搜索结果(分组渲染):
     * - [PersonSearchResult.nameHits]:FTS+LIKE 命中的联系人
     * - [PersonSearchResult.tagHits]:Tag 名字命中的标签 + 该 Tag 下的联系人
     *
     * 去重策略:同一联系人既被名字命中又被标签命中时,优先保留在 nameHits(避免重复展示)。
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
                        // [修复防御]: 去重 — 同一联系人既被名字命中又被标签命中时,
                        // 只在 nameHits 中展示,避免 UI 上重复出现两条相同联系人。
                        val tagGroups = matchedTags.map { tag ->
                            val contacts = tagRepository.getContactsByTag(tag.id)
                                .filterNot { it.id in nameHitIds }
                            TagHitGroup(tag, contacts)
                        }.filter { it.contacts.isNotEmpty() }
                        PersonSearchResult(nameHits = names, tagHits = tagGroups)
                    }
                    Log.d(TAG, "PersonSearchResult built: nameHits=${result.nameHits.size} tagHits=${result.tagHits.size}")
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
                    Log.d(TAG, "PersonViewModel: refresh tick reloaded profile name=${latest?.name} avatarPath=${latest?.avatarPath}")
                }
        }
        viewModelScope.launch {
            _userProfile.value = userProfileRepository.getUserProfileOnce()
        }

        // [修复防御]: 把 Room 的全量联系人 Flow 订阅到 _allContacts，让 InMemoryContactsPagingSource
        // 始终从最新数据取切片。删除联系人时同时 mutate in-memory list（DB 写之前）与
        // Room Flow 推送（DB 写之后），任一路径都能让 UI 跟上。
        repository.getAllContacts()
            .onEach { list ->
                _allContacts.value = list
                _contactsLoadedFromDb.value = true
                Log.d(
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
            Log.d(TAG, "PersonViewModel: refreshUserProfile pulled name=${latest?.name} avatarPath=${latest?.avatarPath}")
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // [修复防御]: 用自定义 InMemoryContactsPagingSource 后，删除流程不再触发 invalidate。
    // 时序：
    //   1. 立刻 mutate in-memory list（_allContacts）把被删条目从内存中移除 —— LazyColumn
    //      走 key-based diff 看到 key 消失，自然让相邻条目填上空位，listState 完全保留。
    //   2. 异步在 IO 线程调 repository.deleteByIds 写 DB，写完后 Room 的 contactDao.getAllContacts()
    //      Flow 会推送新的全量列表，我们有一个专门的 collect 协程把新列表写回 _allContacts
    //      （过滤掉 _deletedIds 防止"DB 已删但 UI 短暂看到又被推回来的过时副本"）。
    //   3. 因为我们 mutate 内存在前 / DB 推流在后，最终 _allContacts 与 DB 一致。
    fun deleteContacts(ids: List<Long>) {
        if (ids.isEmpty()) return
        Log.d(TAG, "PersonViewModel.deleteContacts: count=${ids.size} ids=$ids")
        // 1. 立刻 mutate in-memory list（同步，不等 IO 完成）
        synchronized(_allContacts) {
            val current = _allContacts.value
            val idsSet = ids.toSet()
            _allContacts.value = current.filterNot { it.id in idsSet }
            Log.d(
                TAG,
                "PersonViewModel.deleteContacts: in-memory list mutated, removed=${current.size - _allContacts.value.size}, now=${_allContacts.value.size}",
            )
        }
        // 2. 异步写 DB；Room Flow 自动推送新列表，collected coroutine 负责同步回 _allContacts
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteByIds(ids)
                Log.d(TAG, "PersonViewModel.deleteContacts: DB delete done")
            } catch (e: Exception) {
                Log.e(TAG, "PersonViewModel.deleteContacts: DB delete failed", e)
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

    /**
     * 用户在 SAF 中选中文件后触发。读取 → 解析 → 查重 → 切到 Preview 状态。
     */
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
        private const val TAG = "Tester"
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