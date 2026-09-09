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

/**
 * 联系人页（U13：列表分组/索引条/对话框已下沉）。
 *
 * @param onScanContact 扫二维码添加联系人
 * @param onCreateContact 手动新建联系人
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
    // 监听 AppViewModel 的全局 tick（详情页写完 DB 都会发），
    // 触发 PersonViewModel.refreshUserProfile() 拉一次最新 UserProfile。
    val appViewModel: AppViewModel = koinViewModel()
    val userProfileTick by appViewModel.userProfileTick.collectAsStateWithLifecycle()
    LaunchedEffect(userProfileTick) {
        viewModel.refreshUserProfile()
    }
    // [修复防御]: 见注释——改用 StateFlow<List> 后删除了 PagingSource invalidate 链，
    // 不再需要 PagerState 切页时的联动 onRefreshData 回调（PagingSource 数据已被 Room Flow 自动同步）。
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
        onAddContact = onScanContact,  // [V2-E2E #4] 旧调用方默认行为:扫码
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

    // 下拉刷新：触发一轮完整同步（push → pull），结果 toast 反馈
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val refreshMessage by viewModel.refreshMessage.collectAsStateWithLifecycle()
    LaunchedEffect(refreshMessage) {
        refreshMessage?.let {
            showToast(it)
            viewModel.consumeRefreshMessage()
        }
    }

    // [修复防御]: PersonScreen 每次重进 composition 时（包括 PagerState 切页导致重建），
    // 主动再拉一次最新 UserProfile，确保 ContactAvatar 的 avatarPath 立刻是最新的。
    LaunchedEffect(Unit) {
        onRefreshData()
    }

    // 使用 rememberSaveable + LazyListState.Saver，确保从详情页返回时滚动位置被保留
    // （自定义栈式导航 + AnimatedContent 会让 Composable 退出 composition，普通 remember 会丢状态）
    // [修复防御]: 不要在删除时强制重置 listState——用户期望"删除后保持原视觉位置"。
    // 强制归零（之前的 scrollGeneration++ 方案）会被用户感知为"删除后跳到顶"。
    // 删除联系人后 LazyColumn 因 key 集合变化会自动重新布局，listState 自然跟随；
    // 仅在 savedIndex 越界时（典型：从详情页删除联系人后返回 PersonPage）才需要兜底归零。
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var searchExpanded by remember { mutableStateOf(false) }

    // 多选状态
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 确定使用哪个 List 展示
    // [修复防御]: 搜索态下展示 nameHits(因为 PersonSearchResult 是分组结构,
    // tagHits 在搜索头部加一个独立 section 渲染)。
    val displayItems = if (searchQuery.isBlank()) contacts else searchResults.nameHits
    val tagHitGroups = if (searchQuery.isBlank()) emptyList() else searchResults.tagHits

    // [V2-P1.5] Paging 抽取后,删除走 in-memory mutate + key-based diff,scroll position 自然稳定。
    // PersonScrollRestorePolicy 整套兜底逻辑已删除;恢复目标越界时由 LazyColumn 自身处理。

    // 跟踪已显示的字母标题，避免跨页重复
    // 使用普通对象而非 mutableStateOf，避免在组合阶段写入 State 导致首项字母标题被刷掉
    val lastShownLetter = remember { Ref<String?>(null) }

    // 退出多选模式
    fun exitSelectMode() {
        isSelectMode = false
        selectedIds = emptySet()
    }

    // 系统返回键：多选模式优先于搜索栏
    BackHandler(enabled = isSelectMode || searchExpanded) {
        when {
            isSelectMode -> exitSelectMode()
            searchExpanded -> searchExpanded = false
        }
    }

    // ========== QAuxv 导入流程 ==========

    val qaImportState by viewModel.qaImportState.collectAsStateWithLifecycle()
    val qaImportResult by viewModel.qaImportResult.collectAsStateWithLifecycle()
    val qaImportError by viewModel.qaImportError.collectAsStateWithLifecycle()
    var showPersonOverflowMenu by remember { mutableStateOf(false) }
    var pendingSelected by remember { mutableStateOf<List<QAuxvFriendEntry>>(emptyList()) }
    var showConflictDialog by remember { mutableStateOf(false) }

    val qAuxvImportLauncher = rememberDocumentPickLauncher("application/json") { bytes ->
        if (bytes != null) viewModel.onQAuxvFileSelected(bytes)
    }

    // Parsing / Importing 时返回键拦截，防止进行中数据被中断
    val isImportingNow = qaImportState is QAuxvImportState.Importing
    val isParsingNow = qaImportState is QAuxvImportState.Parsing
    BackHandler(enabled = isParsingNow || isImportingNow) {
        // noop：进度 Dialog 内部也不响应外部关闭
    }

    // 导入完成 Toast
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

    // 搜索态下 allFilteredIds = nameHits + tagHits 并集，确保全选不遗漏
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
                // 多选模式顶部栏
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
                                    // [V2-E2E #4] 手动新建联系人入口 — 不打扰扫码用户的 FAB,
                                    // 放在"更多"菜单里,符合"非高频操作收纳到次级入口"的设计。
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
                // [V2-E2E #4 修复]: 遵循用户原行为 — FAB 直接跳扫码页(走 Route.Scanner)。
                // 弹菜单会打断用户习惯,改为"手动新建联系人"放到 TopAppBar 更多菜单。
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
            // 多选模式底部操作栏
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
            // 固定项数：搜索栏(1) + 提示(1，仅数据库有联系人时显示) + 名片(1)
            val fixedItemCount = if (hasContactsInDb) 3 else 2

            if (isEmptyNoSearch) {
                // 空状态：使用 Column 让空状态文本正确居中在搜索栏和名片下方
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

                    // 我的名片
                    MyProfileHeader(
                        profile = profile,
                        onClick = { onContactClick(-1L) }
                    )

                    // 居中空状态文本
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
                            // 空页面没有可滚动元素，下拉手势无法触发；给一个明确的刷新入口
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
                // 有联系人或有搜索词：使用 LazyColumn 展示列表
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
                        // 搜索栏 - 始终显示
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

                        // 我的名片（常驻在搜索栏下方）
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

