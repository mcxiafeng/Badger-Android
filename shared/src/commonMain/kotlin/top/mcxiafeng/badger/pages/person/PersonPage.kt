package top.mcxiafeng.badger.pages.person

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import top.mcxiafeng.badger.AppViewModel
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.model.LetterCount
import top.mcxiafeng.badger.data.importer.QAuxvFriendEntry
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.ui.components.ToolbarAction
import top.mcxiafeng.badger.ui.components.BadgerFloatingBarList
import top.mcxiafeng.badger.ui.components.badgerBottomBarPadding
import top.mcxiafeng.badger.ui.components.badgerListContentPadding
import top.mcxiafeng.badger.ui.components.BadgerEmptyStateSimple
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
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
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.User
import com.composables.icons.lucide.X
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.platform.rememberDocumentPickLauncher

private const val TAG = "PersonPage"

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
        onAddContact = onScanContact,  
        onContactClick = onContactClick,
        onDeleteContacts = { ids -> viewModel.deleteContacts(ids) }
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
    onAddContact: () -> Unit = {},
    onContactClick: (Long) -> Unit = {},
    onDeleteContacts: suspend (List<Long>) -> Unit = {}
) {
    val profile by userProfile.collectAsStateWithLifecycle(initialValue = null)

    
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val refreshMessage by viewModel.refreshMessage.collectAsStateWithLifecycle()
    LaunchedEffect(refreshMessage) {
        refreshMessage?.let {
            showToast(it)
            viewModel.consumeRefreshMessage()
        }
    }

    
    
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

    
    

    
    
    val lastShownLetter = remember { Ref<String?>(null) }

    
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

    val qAuxvImportLauncher = rememberDocumentPickLauncher("application/json") { bytes ->
        if (bytes != null) viewModel.onQAuxvFileSelected(bytes)
    }

    
    val isImportingNow = qaImportState is QAuxvImportState.Importing
    val isParsingNow = qaImportState is QAuxvImportState.Parsing
    BackHandler(enabled = isParsingNow || isImportingNow) {
        
    }

    
    LaunchedEffect(qaImportResult) {
        qaImportResult?.let {
            showToast("新增 ${it.inserted} / 替换 ${it.replaced} / 跳过 ${it.skipped}")
            viewModel.consumeImportResult()
        }
    }
    LaunchedEffect(qaImportError) {
        qaImportError?.let {
            showToast("导入失败: $it")
            viewModel.consumeImportResult()
        }
    }

    
    val nameIds = remember(displayItems) { displayItems.map { it.id } }
    val tagIds = remember(tagHitGroups) { tagHitGroups.flatMap { it.contacts }.map { it.id } }
    val allFilteredIds = remember(nameIds, tagIds) {
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
                            Icon(
                                imageVector = Lucide.X,
                                contentDescription = "取消"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedIds = if (isAllSelected) emptySet() else allFilteredIds
                        }) {
                            Icon(
                                imageVector = Lucide.CircleCheck,
                                contentDescription = if (isAllSelected) "取消全选" else "全选",
                                tint = if (isAllSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = "联系人",
                    scrollBehavior = topAppBarScrollBehavior,
                    actions = {
                        Box {
                            IconButton(onClick = { showPersonOverflowMenu = true }) {
                                Icon(Lucide.EllipsisVertical, contentDescription = "更多")
                            }
                            OverlayListPopup(
                                show = showPersonOverflowMenu,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showPersonOverflowMenu = false }
                            ) {
                                ListPopupColumn {
                                    
                                    
                                    DropdownImpl(
                                        text = "手动新建联系人",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            showPersonOverflowMenu = false
                                            BadgerLog.d("PersonPage", "OverflowMenu: 手动新建联系人")
                                            onCreateContact()
                                        }
                                    )
                                    DropdownImpl(
                                        text = "从 QAuxiliary 导入 QQ 好友",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 1,
                                        onSelectedIndexChange = {
                                            showPersonOverflowMenu = false
                                            qAuxvImportLauncher.launch()
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isSelectMode,
                enter = fadeIn(tween(BadgerMotion.DURATION_FAST)) + slideInVertically(tween(BadgerMotion.DURATION_FAST)) { it },
                exit = fadeOut(tween(BadgerMotion.DURATION_FAST)) + slideOutVertically(tween(BadgerMotion.DURATION_FAST)) { it },
            ) {
                
                
                FloatingActionButton(
                    onClick = onScanContact,
                    modifier = Modifier.badgerBottomBarPadding()
                ) {
                    Icon(
                        imageVector = Lucide.Plus,
                        contentDescription = "添加",
                        tint = MiuixTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        floatingToolbar = {
            
            AnimatedVisibility(
                visible = isSelectMode && selectedIds.isNotEmpty(),
                enter = fadeIn(tween(BadgerMotion.DURATION_FAST)) + slideInVertically(tween(BadgerMotion.DURATION_FAST)) { it },
                exit = fadeOut(tween(BadgerMotion.DURATION_FAST)) + slideOutVertically(tween(BadgerMotion.DURATION_FAST)) { it },
            ) {
                Box(modifier = Modifier.badgerBottomBarPadding()) {
                    FloatingToolbar(cornerRadius = 16.dp) {
                        ToolbarAction(
                            icon = Lucide.Trash2,
                            label = "删除",
                            tint = MiuixTheme.colorScheme.error,
                            onClick = { showDeleteConfirmDialog = true }
                        )
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize()) {
            val hasContactsInDb = letterCounts.isNotEmpty()
            val isEmptyNoSearch = !hasContactsInDb && searchQuery.isBlank()
                && displayItems.isEmpty()
            
            val fixedItemCount = if (hasContactsInDb) 3 else 2

            if (isEmptyNoSearch) {
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    SearchBar(
                        inputField = {
                            InputField(
                                query = searchQuery,
                                onQueryChange = { onSearchQueryChange(it) },
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = "搜索联系人"
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = BadgerSpacing.lg, bottom = BadgerSpacing.lg)
                    ) {}

                    
                    MyProfileHeader(
                        profile = profile,
                        onClick = { onContactClick(-1L) }
                    )

                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BadgerEmptyStateSimple(
                                icon = Lucide.User,
                                title = "还没有联系人",
                                subtitle = "点击添加你的第一个联系人",
                            )
                            
                            Text(
                                text = "刷新同步云端数据",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        BadgerLog.d(TAG, "EmptyState: refresh tapped")
                                        viewModel.refreshFromServer()
                                    }
                                    .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.sm),
                            )
                        }
                    }
                }
            } else {
                
                val pullState = rememberPullToRefreshState()
                PullToRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        BadgerLog.d(TAG, "PersonPage: pull-to-refresh")
                        viewModel.refreshFromServer()
                    },
                    pullToRefreshState = pullState,
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                ) {
                    BadgerFloatingBarList(
                        state = listState,
                        contentPadding = badgerListContentPadding(
                            scaffoldTop = paddingValues.calculateTopPadding(),
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        
                        item(key = "search_bar") {
                            SearchBar(
                                inputField = {
                                    InputField(
                                        query = searchQuery,
                                        onQueryChange = { onSearchQueryChange(it) },
                                        onSearch = { searchExpanded = false },
                                        expanded = searchExpanded,
                                        onExpandedChange = { searchExpanded = it },
                                        label = "搜索联系人"
                                    )
                                },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = BadgerSpacing.lg, bottom = BadgerSpacing.lg)
                            ) {}
                        }
                        if (hasContactsInDb) {
                            item(key = "hint_long_press") {
                                FirstTimeHint(
                                    text = "长按联系人可多选删除",
                                    hintKey = "long_press_person",
                                    modifier = Modifier.padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.xs)
                                )
                            }
                        }

                        
                        item(key = "my_profile") {
                            MyProfileHeader(
                                profile = profile,
                                onClick = { onContactClick(-1L) }
                            )
                        }

                        personGroupedContactItems(
                            displayItems = displayItems,
                            tagHitGroups = tagHitGroups,
                            searchQuery = searchQuery,
                            lastShownLetter = lastShownLetter,
                            contactTagsMap = contactTagsMap,
                            selectedIds = selectedIds,
                            isSelectMode = isSelectMode,
                            onContactClick = onContactClick,
                            onToggleSelected = { id ->
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            },
                            onEnterSelectMode = { id ->
                                isSelectMode = true
                                selectedIds = setOf(id)
                            },
                        )
                    }
                }
            }

            if (!isSelectMode && hasContactsInDb && searchQuery.isBlank()) {
                PersonLetterIndexOverlay(
                    letterCounts = letterCounts,
                    displayItemCount = displayItems.size,
                    hasContactsInDb = hasContactsInDb,
                    fixedItemCount = fixedItemCount,
                    listState = listState,
                    topPadding = paddingValues.calculateTopPadding(),
                    bottomPadding = paddingValues.calculateBottomPadding(),
                )
            }
        }
    }

    PersonScreenDialogs(
        viewModel = viewModel,
        qaImportState = qaImportState,
        pendingSelected = pendingSelected,
        showConflictDialog = showConflictDialog,
        showDeleteConfirmDialog = showDeleteConfirmDialog,
        selectedIds = selectedIds,
        scope = scope,
        onPendingSelectedChange = { pendingSelected = it },
        onShowConflictDialogChange = { showConflictDialog = it },
        onShowDeleteConfirmDialogChange = { showDeleteConfirmDialog = it },
        onDeleteContacts = onDeleteContacts,
        exitSelectMode = { exitSelectMode() },
    )
}

