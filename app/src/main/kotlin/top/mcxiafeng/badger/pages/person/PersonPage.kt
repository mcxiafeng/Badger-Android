package top.mcxiafeng.badger.pages.person

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.AppViewModel
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.pages.person.contact.ToolbarAction
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.BadgerConfirmDialog
import top.mcxiafeng.badger.ui.components.BadgerEmptyStateCompact
import top.mcxiafeng.badger.ui.components.BadgerEmptyStateSimple
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.PinyinUtils
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "PersonPage"

/**
 * 联系人页面
 *
 * 功能：
 * - 显示按拼音首字母分组的联系人列表
 * - 支持搜索过滤（FTS 全文检索）
 * - 右侧字母索引栏快速定位
 * - 拖动索引时显示字母气泡提示
 * - 悬浮添加按钮（点击打开扫描页）
 *
 * @param onScanContact 扫二维码添加联系人(打开扫描页)
 * @param onCreateContact 手动新建联系人(打开 CreateContactPage)
 * @param onContactClick 联系人点击回调
 */
@Composable
fun PersonRoute(
    onScanContact: () -> Unit = {},
    onCreateContact: () -> Unit = {},
    onContactClick: (Long) -> Unit = {},
) {
    val viewModel: PersonViewModel = koinViewModel()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val contactTagsMap by viewModel.contactTagsMap.collectAsStateWithLifecycle()
    val letterCounts by viewModel.letterCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val appViewModel: AppViewModel = koinViewModel()
    val userProfileTick by appViewModel.userProfileTick.collectAsStateWithLifecycle()
    LaunchedEffect(userProfileTick) {
        viewModel.refreshUserProfile()
    }
    PersonScreen(
        viewModel = viewModel,
        contacts = contacts,
        searchResults = searchResults,
        contactTagsMap = contactTagsMap,
        searchQuery = searchQuery,
        letterCounts = letterCounts,
        userProfile = viewModel.userProfile,
        onRefreshData = { viewModel.refreshUserProfile() },
        onSearchQueryChange = viewModel::updateSearchQuery,
        onScanContact = onScanContact,
        onCreateContact = onCreateContact,
        onContactClick = onContactClick,
        onDeleteContacts = { ids -> viewModel.deleteContacts(ids) },
    )
}

@Composable
fun PersonScreen(
    viewModel: PersonViewModel,
    contacts: List<Contact>,
    searchResults: PersonSearchResult,
    contactTagsMap: Map<Long, List<TagCacheEntity>>,
    searchQuery: String,
    letterCounts: List<LetterCount>,
    userProfile: StateFlow<UserProfile?>,
    onRefreshData: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onScanContact: () -> Unit = {},
    onCreateContact: () -> Unit = {},
    onContactClick: (Long) -> Unit = {},
    onDeleteContacts: suspend (List<Long>) -> Unit = {},
) {
    val context = LocalContext.current
    val profile by userProfile.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(Unit) {
        onRefreshData()
    }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var searchExpanded by remember { mutableStateOf(false) }
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val displayItems = if (searchQuery.isBlank()) contacts else searchResults.nameHits
    val tagHitGroups = if (searchQuery.isBlank()) emptyList() else searchResults.tagHits

    fun exitSelectMode() {
        isSelectMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = isSelectMode || searchExpanded) {
        when {
            isSelectMode -> exitSelectMode()
            searchExpanded -> searchExpanded = false
        }
    }

    val qaImportState by viewModel.qaImportState.collectAsStateWithLifecycle()
    val qaImportResult by viewModel.qaImportResult.collectAsStateWithLifecycle()
    val qaImportError by viewModel.qaImportError.collectAsStateWithLifecycle()
    var showPersonOverflowMenu by remember { mutableStateOf(false) }
    var pendingSelected by remember { mutableStateOf<List<QAuxvFriendEntry>>(emptyList()) }
    var showConflictDialog by remember { mutableStateOf(false) }
    val qAuxvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onQAuxvFileSelected(uri)
    }
    val isImportingNow = qaImportState is QAuxvImportState.Importing
    val isParsingNow = qaImportState is QAuxvImportState.Parsing
    BackHandler(enabled = isParsingNow || isImportingNow) {}

    LaunchedEffect(qaImportResult) {
        qaImportResult?.let {
            Toast.makeText(context, "新增 ${it.inserted} / 替换 ${it.replaced} / 跳过 ${it.skipped}", Toast.LENGTH_LONG).show()
            viewModel.consumeImportResult()
        }
    }
    LaunchedEffect(qaImportError) {
        qaImportError?.let {
            Toast.makeText(context, "导入失败: $it", Toast.LENGTH_LONG).show()
            viewModel.consumeImportResult()
        }
    }

    val allFilteredIds = remember(displayItems, tagHitGroups) {
        val nameIds = displayItems.map { it.id }
        val tagIds = tagHitGroups.flatMap { it.contacts }.map { it.id }
        (nameIds + tagIds).toSet()
    }
    val isAllSelected = remember(selectedIds, allFilteredIds) {
        allFilteredIds.isNotEmpty() && allFilteredIds.all { it in selectedIds }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = remember { SnackbarHostState() }) },
        topBar = {
            if (isSelectMode) {
                TopAppBar(
                    title = "已选择 ${selectedIds.size} 项",
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { exitSelectMode() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "取消")
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedIds = if (isAllSelected) emptySet() else allFilteredIds }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = if (isAllSelected) "取消全选" else "全选",
                                tint = if (isAllSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = "联系人",
                    scrollBehavior = topAppBarScrollBehavior,
                    actions = {
                        Box {
                            IconButton(onClick = { showPersonOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            OverlayListPopup(
                                show = showPersonOverflowMenu,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showPersonOverflowMenu = false },
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "手动新建联系人",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            showPersonOverflowMenu = false
                                            Log.d(TAG, "OverflowMenu: 手动新建联系人")
                                            onCreateContact()
                                        },
                                    )
                                    DropdownImpl(
                                        text = "从 QAuxiliary 导入 QQ 好友",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 1,
                                        onSelectedIndexChange = {
                                            showPersonOverflowMenu = false
                                            qAuxvImportLauncher.launch("*/*")
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
            AnimatedVisibility(
                visible = !isSelectMode,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                FloatingActionButton(
                    onClick = onScanContact,
                    modifier = Modifier.padding(bottom = floatingBarBottomPadding),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "添加", tint = MiuixTheme.colorScheme.onPrimary)
                }
            }
        },
        floatingToolbar = {
            AnimatedVisibility(
                visible = isSelectMode && selectedIds.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                Box(modifier = Modifier.padding(bottom = LocalFloatingBarBottomPadding.current)) {
                    FloatingToolbar(cornerRadius = 16.dp) {
                        ToolbarAction(
                            icon = Icons.Default.Delete,
                            label = "删除",
                            tint = MiuixTheme.colorScheme.error,
                            onClick = { showDeleteConfirmDialog = true },
                        )
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            val hasContactsInDb = letterCounts.isNotEmpty()
            val isEmptyNoSearch = !hasContactsInDb && searchQuery.isBlank() && displayItems.isEmpty()
            val fixedItemCount = if (hasContactsInDb) 3 else 2

            if (isEmptyNoSearch) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()),
                ) {
                    SearchBar(
                        inputField = {
                            InputField(
                                query = searchQuery,
                                onQueryChange = onSearchQueryChange,
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = "搜索联系人",
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        modifier = Modifier.fillMaxWidth().padding(top = BadgerSpacing.lg, bottom = BadgerSpacing.lg),
                    ) {}
                    MyProfileHeader(profile = profile, onClick = { onContactClick(-1L) })
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BadgerEmptyStateSimple(
                            icon = Icons.Default.Person,
                            title = "还没有联系人",
                            subtitle = "点击添加你的第一个联系人",
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = LocalFloatingBarBottomPadding.current,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "search_bar") {
                        SearchBar(
                            inputField = {
                                InputField(
                                    query = searchQuery,
                                    onQueryChange = onSearchQueryChange,
                                    onSearch = { searchExpanded = false },
                                    expanded = searchExpanded,
                                    onExpandedChange = { searchExpanded = it },
                                    label = "搜索联系人",
                                )
                            },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            modifier = Modifier.fillMaxWidth().padding(top = BadgerSpacing.lg, bottom = BadgerSpacing.lg),
                        ) {}
                    }
                    if (hasContactsInDb) {
                        item(key = "hint_long_press") {
                            FirstTimeHint(
                                text = "长按联系人可多选删除",
                                hintKey = "long_press_person",
                                modifier = Modifier.padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.xs),
                            )
                        }
                    }
                    item(key = "my_profile") {
                        MyProfileHeader(profile = profile, onClick = { onContactClick(-1L) })
                    }
                    if (displayItems.isEmpty() && tagHitGroups.isEmpty()) {
                        item(key = "empty_search") {
                            BadgerEmptyStateCompact(text = "未找到联系人", modifier = Modifier.padding(vertical = BadgerSpacing.xxxl))
                        }
                    } else {
                        if (searchQuery.isNotBlank() && displayItems.isNotEmpty()) {
                            item(key = "search_header_names") {
                                Text(
                                    text = "匹配名字（${displayItems.size}）",
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(start = BadgerSpacing.lgx, top = BadgerSpacing.md, bottom = BadgerSpacing.xs),
                                )
                            }
                        }

                        val showAlphabetHeaders = searchQuery.isBlank()
                        items(
                            count = displayItems.size,
                            key = { index -> "c_${displayItems[index].id}" },
                            contentType = { "contact" },
                        ) { index ->
                            val contact = displayItems[index]
                            val currentLetter = PinyinUtils.getContactPinyinInitial(contact.name)
                            val previousLetter = if (index > 0) {
                                displayItems.getOrNull(index - 1)?.let { PinyinUtils.getContactPinyinInitial(it.name) }
                            } else null
                            val showHeader = showAlphabetHeaders && (index == 0 || currentLetter != previousLetter)
                            Column {
                                if (showHeader) {
                                    Text(
                                        text = currentLetter,
                                        style = MiuixTheme.textStyles.subtitle,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(start = BadgerSpacing.lgx, top = BadgerSpacing.sm, bottom = BadgerSpacing.xs),
                                    )
                                }
                                ContactRow(
                                    contact = contact,
                                    contactTags = contactTagsMap,
                                    selectedIds = selectedIds,
                                    isSelectMode = isSelectMode,
                                    onContactClick = onContactClick,
                                    onToggleSelected = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                                    onEnterSelectMode = { id -> isSelectMode = true; selectedIds = setOf(id) },
                                )
                            }
                        }

                        tagHitGroups.forEach { group ->
                            item(key = "search_header_tag_${group.tag.id}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = BadgerSpacing.lgx, top = BadgerSpacing.md, bottom = BadgerSpacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(group.tag.color)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "标签「${group.tag.name}」（${group.contacts.size}）",
                                        style = MiuixTheme.textStyles.subtitle,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            items(
                                count = group.contacts.size,
                                key = { idx -> "tag_${group.tag.id}_${group.contacts[idx].id}" },
                                contentType = { "contact" },
                            ) { idx ->
                                val contact = group.contacts[idx]
                                ContactRow(
                                    contact = contact,
                                    contactTags = contactTagsMap,
                                    selectedIds = selectedIds,
                                    isSelectMode = isSelectMode,
                                    onContactClick = onContactClick,
                                    onToggleSelected = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                                    onEnterSelectMode = { id -> isSelectMode = true; selectedIds = setOf(id) },
                                )
                            }
                        }
                    }
                }
            }

            if (!isSelectMode && hasContactsInDb && searchQuery.isBlank()) {
                var isIndexDragging by remember { mutableStateOf(false) }
                var currentIndexLetter by remember { mutableStateOf("") }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(28.dp)
                        .padding(top = paddingValues.calculateTopPadding() + 48.dp, bottom = paddingValues.calculateBottomPadding() + 72.dp),
                ) {
                    val indexLetters = remember { listOf("⭐") + ('A'..'Z').map { it.toString() } }
                    LetterIndexBar(
                        letters = indexLetters,
                        onSelectLetter = { letter ->
                            when (letter) {
                                "⭐" -> {
                                    val myProfileIndex = if (hasContactsInDb) 2 else 1
                                    scope.launch { listState.animateScrollToItem(myProfileIndex) }
                                }
                                else -> {
                                    val target = letterCounts.firstOrNull { it.letter == letter }?.let {
                                        fixedItemCount + letterCounts.takeWhile { lc -> lc.letter < letter }.sumOf { lc -> lc.count }
                                    }
                                    if (target != null) {
                                        scope.launch { listState.animateScrollToItem(target) }
                                    }
                                }
                            }
                        },
                        onDragStateChange = { dragging, letter ->
                            isIndexDragging = dragging
                            currentIndexLetter = letter
                        },
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
                LetterTooltip(visible = isIndexDragging, letter = currentIndexLetter)
            }
        }
    }

    val qaImportProgress by viewModel.qaImportProgress.collectAsStateWithLifecycle()
    val importingSummary = qaImportProgress?.let { "${it.displayLabel()} ${it.current}/${it.total}" } ?: "正在写入联系人…"
    QAuxvProgressDialog(title = "正在解析", summary = "正在读取并解析文件…", show = qaImportState is QAuxvImportState.Parsing)
    QAuxvProgressDialog(title = "正在导入", summary = importingSummary, show = qaImportState is QAuxvImportState.Importing)
    val previewState = qaImportState as? QAuxvImportState.Preview
    if (previewState != null) {
        QAuxvPreviewDialog(
            state = previewState,
            show = true,
            onToggleCheck = viewModel::togglePreviewCheck,
            onSelectAll = viewModel::selectAllPreview,
            onDeselectAll = viewModel::deselectAllPreview,
            onCancel = {
                showConflictDialog = false
                pendingSelected = emptyList()
                viewModel.cancelImport()
            },
            onConfirm = { selected ->
                val hasConflict = selected.any { it.uin in previewState.existingContactIdByUin }
                if (!hasConflict) {
                    val decisions = selected.map { entry -> Triple(entry, null, QAuxvConflictAction.InsertAnyway) }
                    viewModel.commitImport(decisions)
                } else {
                    pendingSelected = selected
                    showConflictDialog = true
                }
            },
        )
    }
    if (showConflictDialog) {
        val conflictMap = previewState?.existingContactIdByUin ?: emptyMap()
        QAuxvConflictDialog(
            show = true,
            selectedEntries = pendingSelected,
            existingContactIdByUin = conflictMap,
            onCancel = {
                showConflictDialog = false
                pendingSelected = emptyList()
            },
            onResolve = { decisions ->
                showConflictDialog = false
                pendingSelected = emptyList()
                viewModel.commitImport(decisions)
            },
        )
    }
    if (showDeleteConfirmDialog) {
        BadgerConfirmDialog(
            show = true,
            title = "删除联系人",
            message = "确定要删除选中的 ${selectedIds.size} 个联系人吗？此操作不可撤销。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirmDialog = false
                val idsToDelete = selectedIds.toList()
                scope.launch {
                    onDeleteContacts(idsToDelete)
                    Toast.makeText(context, "已删除 ${idsToDelete.size} 个联系人", Toast.LENGTH_SHORT).show()
                    exitSelectMode()
                }
            },
            onDismiss = { showDeleteConfirmDialog = false },
        )
    }
}

@Composable
private fun MyProfileHeader(
    profile: UserProfile?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = BadgerSpacing.xl),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = MiuixTheme.colorScheme.surface, shape = miuixShape(BadgerRadius.lg))
                .clickable {
                    Log.d(TAG, "My Profile clicked!")
                    onClick()
                }
                .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.md),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    ContactAvatar(name = profile?.name ?: "用户", avatarPath = profile?.avatarPath, size = 40)
                }
                Spacer(modifier = Modifier.width(BadgerSpacing.lg))
                Column {
                    Text(text = "我的名片", style = MiuixTheme.textStyles.body1)
                    Spacer(modifier = Modifier.height(BadgerSpacing.xxs))
                    Text(
                        text = profile?.name?.let { "查看和编辑 $it 的信息" } ?: "查看和编辑个人信息",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactItem(
    contact: Contact,
    showDots: List<TagCacheEntity>,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        BasicComponent(
            title = contact.name,
            startAction = { ContactAvatar(name = contact.name, avatarUrl = contact.avatarUrl, avatarPath = contact.avatarPath) },
            onClick = null,
        )
        if (showDots.isNotEmpty() && !isSelectMode) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = BadgerSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                showDots.take(3).forEach { tag ->
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(tag.color)))
                }
                if (showDots.size > 3) {
                    Text(text = "+${showDots.size - 3}", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        if (isSelectMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "已选" else "未选",
                tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = BadgerSpacing.lg).size(24.dp),
            )
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    contactTags: Map<Long, List<TagCacheEntity>>,
    selectedIds: Set<Long>,
    isSelectMode: Boolean,
    onContactClick: (Long) -> Unit,
    onToggleSelected: (Long) -> Unit,
    onEnterSelectMode: (Long) -> Unit,
) {
    val dots = contactTags[contact.id].orEmpty()
    val isSelected = contact.id in selectedIds
    ContactItem(
        contact = contact,
        showDots = dots,
        isSelectMode = isSelectMode,
        isSelected = isSelected,
        onClick = {
            if (isSelectMode) onToggleSelected(contact.id) else onContactClick(contact.id)
        },
        onLongClick = {
            if (!isSelectMode) onEnterSelectMode(contact.id)
        },
    )
}

@Composable
fun LetterIndexBar(
    letters: List<String>,
    onSelectLetter: (String) -> Unit,
    onDragStateChange: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .padding(vertical = BadgerSpacing.sm)
                .padding(horizontal = BadgerSpacing.xs)
                .pointerInput(letters) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (letters.isEmpty() || size.height <= 0) return@detectDragGestures
                            val itemHeight = size.height / letters.size
                            val index = (offset.y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                            val letter = letters[index]
                            onDragStateChange(true, letter)
                            onSelectLetter(letter)
                        },
                        onDrag = { change, _ ->
                            if (letters.isEmpty() || size.height <= 0) return@detectDragGestures
                            change.consume()
                            val itemHeight = size.height / letters.size
                            val index = (change.position.y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                            val letter = letters[index]
                            onDragStateChange(true, letter)
                            onSelectLetter(letter)
                        },
                        onDragEnd = { onDragStateChange(false, "") },
                    )
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable {
                            onDragStateChange(true, letter)
                            onSelectLetter(letter)
                            coroutineScope.launch {
                                delay(300)
                                onDragStateChange(false, "")
                            }
                        }
                        .padding(horizontal = BadgerSpacing.xs),
                )
            }
        }
    }
}

@Composable
fun LetterTooltip(visible: Boolean, letter: String) {
    if (visible && letter.isNotEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {}.zIndex(1f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.7f), miuixShape(BadgerRadius.md))
                    .wrapContentSize(Alignment.Center),
            ) {
                Text(text = letter, style = MiuixTheme.textStyles.title1, color = MiuixTheme.colorScheme.onBackground)
            }
        }
    }
}