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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.AppViewModel
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.pages.person.contact.ToolbarAction
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.components.FirstTimeHint
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
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 联系人页面
 *
 * 功能：
 * - 显示按拼音首字母分组的联系人列表（Paging 3 分页加载）
 * - 支持搜索过滤（FTS 全文检索）
 * - 右侧字母索引栏快速定位
 * - 拖动索引时显示字母气泡提示
 * - 悬浮添加按钮（点击打开扫描页）
 *
 * @param onAddContact 添加联系人回调（打开扫描页）
 */
@Composable
fun PersonRoute(onAddContact: () -> Unit = {}, onContactClick: (Long) -> Unit = {}) {
    val viewModel: PersonViewModel = hiltViewModel()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val letterCounts by viewModel.letterCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    // 监听 AppViewModel 的全局 tick（详情页写完 DB 都会发），
    // 触发 PersonViewModel.refreshUserProfile() 拉一次最新 UserProfile。
    val appViewModel: AppViewModel = hiltViewModel()
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
        searchQuery = searchQuery,
        letterCounts = letterCounts,
        userProfile = viewModel.userProfile,
        onRefreshData = { viewModel.refreshUserProfile() },
        onSearchQueryChange = viewModel::updateSearchQuery,
        onAddContact = onAddContact,
        onContactClick = onContactClick,
        onDeleteContacts = { ids -> viewModel.deleteContacts(ids) }
    )
}

@Composable
fun PersonScreen(
    viewModel: PersonViewModel,
    contacts: List<Contact>,
    searchResults: List<Contact>,
    searchQuery: String,
    letterCounts: List<LetterCount>,
    userProfile: StateFlow<UserProfile?>,
    onRefreshData: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onAddContact: () -> Unit = {},
    onContactClick: (Long) -> Unit = {},
    onDeleteContacts: suspend (List<Long>) -> Unit = {}
) {
    val context = LocalContext.current
    val profile by userProfile.collectAsStateWithLifecycle(initialValue = null)

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
    val displayItems = if (searchQuery.isBlank()) contacts else searchResults

    // 根因修复：PagingSource 在外部写库（删除联系人）后会被 Room invalidate，
    // 导致 itemCount 短暂从 N 跌到 0 再回到 N-1，期间 LazyColumn 的 firstVisibleItemIndex
    // 会被自然归零。需要在 invalidate 之前锁定当前滚动位置，等新数据 itemCount 足够时
    // 再显式 scrollToItem 恢复，避免「删除后跳到顶」。
    // savedIndex 越界时（典型：从详情页删除联系人后返回 PersonPage，savedIndex 还在旧值上）
    // 直接归零。
    // [修复防御]: 现在 contacts 是普通 List（非 Paging），删除走 mutate in-memory list + key-based
    // diff，scroll position 自然保持。这一兜底逻辑仅用于"从详情页删除并返回"场景——避免
    // savedIndex 越界时停留在旧位置（典型：从 ContactDetailPage 删除联系人后返回 PersonPage）。
    var pendingRestoreIndex by remember { mutableStateOf<Int?>(null) }
    var pendingRestoreOffset by remember { mutableStateOf(0) }
    var pendingItemCount by remember { mutableStateOf(0) }
    var stableTicks by remember { mutableStateOf(0) }

    // [修复防御]: 现在 data 是 List<Paging 取消后用 List>，删除走 in-memory mutate + key-based
    // diff 路径，scroll position 自然稳定。本 effect 仅在 savedIndex 越界时（典型：
    // 从详情页删除联系人后返回 PersonPage）做归零兜底。
    LaunchedEffect(displayItems.size) {
        val currentIndex = listState.firstVisibleItemIndex
        val currentOffset = listState.firstVisibleItemScrollOffset
        val decision = PersonScrollRestorePolicy.decide(
            itemCount = displayItems.size,
            currentIndex = currentIndex,
            currentOffset = currentOffset,
            pendingIndex = pendingRestoreIndex,
            pendingOffset = pendingRestoreOffset,
            pendingItemCount = pendingItemCount,
            stableTicks = stableTicks,
        )
        pendingRestoreIndex = decision.pendingIndex
        pendingRestoreOffset = decision.pendingOffset
        pendingItemCount = decision.pendingItemCount
        stableTicks = decision.stableTicks
        when (val action = decision.Action) {
            is PersonScrollRestorePolicy.Action.ScrollTo -> {
                listState.scrollToItem(action.index, action.offset)
                Log.d(
                    "Tester",
                    "PersonScreen: restore action index=${action.index} offset=${action.offset} " +
                        "itemCount=${displayItems.size} fromIndex=$currentIndex fromOffset=$currentOffset",
                )
            }
            PersonScrollRestorePolicy.Action.Noop -> {
                if (decision.pendingIndex != null) {
                    Log.d(
                        "Tester",
                        "PersonScreen: pending restore index=${decision.pendingIndex} offset=${decision.pendingOffset} " +
                            "pendingItemCount=${decision.pendingItemCount} currentItemCount=${displayItems.size} " +
                            "stableTicks=${decision.stableTicks}",
                    )
                }
            }
        }
    }

    // [修复防御]: 删除联系人时 PagingSource.invalidate 后 LazyColumn 的 firstVisibleItemIndex
    // 会被 reset 到 0，等主 effect 跑（key 触发后）才 scrollToItem 恢复——中间有一帧 listState=0/0
    // 被用户感知为"闪一下"。这个独立 effect 在 pendingRestoreIndex 变成非 null 的瞬间立刻
    // scrollToItem 一次，把位置"压住"，避免那一帧的视觉跳变。
    LaunchedEffect(pendingRestoreIndex) {
        val idx = pendingRestoreIndex ?: return@LaunchedEffect
        // 第一次锁存瞬间，listState 此刻仍是用户真实位置；后续 Paging 重查可能把它 reset 成 0，
        // 这里在重组的第一帧就重新锚定。
        listState.scrollToItem(idx, pendingRestoreOffset)
        Log.d(
            "Tester",
            "PersonScreen: pre-restore scrollToItem index=$idx offset=${pendingRestoreOffset} (immediate, before Paging invalidates)",
        )
    }

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

    val qAuxvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onQAuxvFileSelected(uri)
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
            Toast.makeText(
                context,
                "新增 ${it.inserted} / 替换 ${it.replaced} / 跳过 ${it.skipped}",
                Toast.LENGTH_LONG,
            ).show()
            viewModel.consumeImportResult()
        }
    }
    LaunchedEffect(qaImportError) {
        qaImportError?.let {
            Toast.makeText(context, "导入失败: $it", Toast.LENGTH_LONG).show()
            viewModel.consumeImportResult()
        }
    }

    // 全选/取消全选（基于当前已加载的分页项）
    val allFilteredIds = remember(displayItems.size) {
        displayItems.map { it.id }.toSet()
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
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedIds = if (isAllSelected) emptySet() else allFilteredIds
                        }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
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
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            OverlayListPopup(
                                show = showPersonOverflowMenu,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showPersonOverflowMenu = false }
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "从 QAuxiliary 导入 QQ 好友",
                                        optionSize = 1,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            showPersonOverflowMenu = false
                                            qAuxvImportLauncher.launch("*/*")
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
            val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
            AnimatedVisibility(
                visible = !isSelectMode,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                // 点击打开扫描页添加联系人
                FloatingActionButton(
                    onClick = onAddContact,
                    modifier = Modifier.padding(bottom = floatingBarBottomPadding)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
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
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Box(modifier = Modifier.padding(bottom = LocalFloatingBarBottomPadding.current)) {
                    FloatingToolbar(cornerRadius = 16.dp) {
                        ToolbarAction(
                            icon = Icons.Default.Delete,
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
                            .padding(top = 16.dp, bottom = 16.dp)
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
                            Text(
                                text = "还没有联系人",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.body1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击添加",
                                color = MiuixTheme.colorScheme.primary,
                                style = MiuixTheme.textStyles.body1,
                                modifier = Modifier.clickable { onAddContact() }
                            )
                        }
                    }
                }
            } else {
                // 有联系人或有搜索词：使用 LazyColumn 展示列表

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = LocalFloatingBarBottomPadding.current
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
                                .padding(top = 16.dp, bottom = 16.dp)
                        ) {}
                    }
                    if (hasContactsInDb) {
                        item(key = "hint_long_press") {
                            FirstTimeHint(
                                text = "长按联系人可多选删除",
                                hintKey = "long_press_person",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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

                    // [修复防御]: 用 List<Contact> 直接渲染，跳过 Paging 3 全部 LoadState。
                    // 删除联系人时 in-memory mutate + items(key=…) 让 LazyColumn 自然 diff。
                    if (displayItems.isEmpty()) {
                        item(key = "empty_search") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "未找到联系人",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    } else {
                        // 按首字母分页展示联系人（内联字母标题）
                        items(
                            count = displayItems.size,
                            key = { index -> "c_${displayItems[index].id}" },
                            contentType = { "contact" }
                        ) { index ->
                            val contact = displayItems[index]

                            // 检测是否需要显示字母标题
                            val currentLetter = PinyinUtils.getContactPinyinInitial(contact.name)
                            val prevLetter = if (index > 0) {
                                displayItems.getOrNull(index - 1)?.let { PinyinUtils.getContactPinyinInitial(it.name) }
                            } else null

                            // 确定是否显示字母标题：
                            // - prevLetter != null && currentLetter != prevLetter → 字母变了，显示
                            // - prevLetter == null && currentLetter != lastShownLetter → 跨页边界，且字母没重复，显示
                            // - prevLetter == null && currentLetter == lastShownLetter → 跨页但同字母，跳过
                            val showHeader = if (prevLetter != null) {
                                currentLetter != prevLetter
                            } else {
                                currentLetter != lastShownLetter.v
                            }

                            Column {
                                if (showHeader) {
                                    lastShownLetter.v = currentLetter
                                    Text(
                                        text = currentLetter,
                                        style = MiuixTheme.textStyles.subtitle,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                                    )
                                }

                                val isSelected = contact.id in selectedIds
                                ContactItem(
                                    contact = contact,
                                    isSelectMode = isSelectMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectMode) {
                                            selectedIds = if (isSelected) {
                                                selectedIds - contact.id
                                            } else {
                                                selectedIds + contact.id
                                            }
                                        } else {
                                            onContactClick(contact.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectMode) {
                                            isSelectMode = true
                                            selectedIds = setOf(contact.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 字母索引栏（仅在非选择模式、非搜索、有联系人时显示）
            if (!isSelectMode && hasContactsInDb && searchQuery.isBlank()) {
                var isIndexDragging by remember { mutableStateOf(false) }
                var currentIndexLetter by remember { mutableStateOf("") }

                // 字母索引栏
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(28.dp)
                        .padding(
                            top = paddingValues.calculateTopPadding() + 48.dp,
                            bottom = paddingValues.calculateBottomPadding() + 72.dp
                        )
                ) {
                    // 字母索引栏 - 固定显示 ⭐(我的名片) + A-Z
                    val indexLetters = remember {
                        listOf("⭐") + ('A'..'Z').map { it.toString() }
                    }
                    LetterIndexBar(
                        letters = indexLetters,
                        onSelectLetter = { letter ->
                            when (letter) {
                                "⭐" -> {
                                    // [修复防御]: 我的名片在 LazyColumn 中的索引随提示条是否存在而变化：
                                    //   search_bar(0) + hint_long_press(1, 条件) + my_profile(2 或 1)
                                    // 之前硬编码 1，hasContactsInDb=true 时落在 hint 上而非 my_profile。
                                    val myProfileIndex = if (hasContactsInDb) 2 else 1
                                    scope.launch { listState.animateScrollToItem(myProfileIndex) }
                                }
                                else -> {
                                    // [修复防御]: 目标索引 = 固定项 + 前面所有字母分组的联系人数量。
                                    // 字母标题是内联在每个分组第一个联系人 Column 里的 Text，不占独立 item，
                                    // 所以"前面组的人数之和 + 固定项"恰好等于目标字母分组的第一个联系人索引。
                                    val target = letterCounts.firstOrNull { it.letter == letter }?.let {
                                        fixedItemCount +
                                            letterCounts
                                                .takeWhile { lc -> lc.letter < letter }
                                                .sumOf { lc -> lc.count }
                                    }
                                    if (target != null) {
                                        // [修复防御]: Paging 是惰性的；如果用户点远端字母（比如第 4 页的 S），
                                        // 此时 itemCount 可能只有 60，但 target 是 200+。animateScrollToItem
                                        // 会触发 Paging 加载更多，但"动画滚动"在加载完成前会先把列表锚定在当前
                                        // 已加载的最大位置，造成视觉上的"瞎跳"再回弹。
                                        // 用 scrollToItem（无动画）先触发 Paging 拉到目标位置，再让滚动跟随。
                                        if (displayItems.size > target) {
                                            scope.launch { listState.animateScrollToItem(target) }
                                        } else {
                                            scope.launch {
                                                listState.scrollToItem(target)
                                                listState.animateScrollToItem(target)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onDragStateChange = { dragging, letter ->
                            isIndexDragging = dragging
                            currentIndexLetter = letter
                        },
                        modifier = Modifier.fillMaxHeight()
                    )
                }

                // 拖动索引时显示的字母气泡
                LetterTooltip(visible = isIndexDragging, letter = currentIndexLetter)
            }
        }
    }

    // ========== QAuxv 导入 Dialogs ==========

    val qaImportProgress by viewModel.qaImportProgress.collectAsStateWithLifecycle()
    val importingSummary = qaImportProgress?.let { "${it.displayLabel()} ${it.current}/${it.total}" }
        ?: "正在写入联系人…"

    // Parsing 进度
    QAuxvProgressDialog(
        title = "正在解析",
        summary = "正在读取并解析文件…",
        show = qaImportState is QAuxvImportState.Parsing,
    )
    // Importing 进度（含头像下载阶段实时显示）
    QAuxvProgressDialog(
        title = "正在导入",
        summary = importingSummary,
        show = qaImportState is QAuxvImportState.Importing,
    )
    // 预览 Dialog
    val previewState = qaImportState as? QAuxvImportState.Preview
    if (previewState != null) {
        QAuxvPreviewDialog(
            state = previewState,
            show = true,
            onToggleCheck = viewModel::togglePreviewCheck,
            onSelectAll = viewModel::selectAllPreview,
            onDeselectAll = viewModel::deselectAllPreview,
            onCancel = {
                // [修复防御]: 取消预览时把冲突 Dialog 一并关掉、清掉 pendingSelected，
                // 否则下次再打开预览时可能闪出上一次的选择。
                showConflictDialog = false
                pendingSelected = emptyList()
                viewModel.cancelImport()
            },
            onConfirm = { selected ->
                // 选中项里若没有 QQ 冲突，直接全部 InsertAnyway 提交，跳过 ConflictDialog。
                val hasConflict = selected.any { it.uin in previewState.existingContactIdByUin }
                if (!hasConflict) {
                    val decisions = selected.map { entry ->
                        Triple(entry, null, QAuxvConflictAction.InsertAnyway)
                    }
                    viewModel.commitImport(decisions)
                } else {
                    pendingSelected = selected
                    showConflictDialog = true
                }
            },
        )
    }
    // 冲突 Dialog：用户在 Preview 中点确认后弹出（仅当有冲突时才弹）
    // [修复防御]: 不要依赖 previewState != null；commitImport 会把状态切到 Importing → previewState 变 null，
    // 此时 Dialog 会瞬间消失。改为只依赖 showConflictDialog，Commit 由 viewModel.cancelImport() 关闭。
    if (showConflictDialog) {
        // 防御性兜底：previewState 丢失时使用空 map，保证 Dialog 不闪退
        val conflictMap = previewState?.existingContactIdByUin ?: emptyMap()
        QAuxvConflictDialog(
            show = true,
            selectedEntries = pendingSelected,
            existingContactIdByUin = conflictMap,
            onCancel = {
                showConflictDialog = false
                pendingSelected = emptyList()
                // 保留 previewState，用户可再次点确认
            },
            onResolve = { decisions ->
                showConflictDialog = false
                pendingSelected = emptyList()
                viewModel.commitImport(decisions)
            },
        )
    }

    // 批量删除确认对话框
    if (showDeleteConfirmDialog) {
        WindowDialog(
            show = true,
            title = "删除联系人",
            summary = "确定要删除选中的 ${selectedIds.size} 个联系人吗？此操作不可撤销。",
            onDismissRequest = { showDeleteConfirmDialog = false },
        ) {
            DialogButtonRow(
                positiveText = "删除",
                onNegative = { showDeleteConfirmDialog = false },
                onPositive = {
                    showDeleteConfirmDialog = false
                    val idsToDelete = selectedIds.toList()
                    // [修复防御]: 在用户点"删除"的这一帧就同步锁存真实 listState 位置与
                    // 当前 itemCount。等 IO 线程删完、PagingSource invalidate、LazyColumn
                    // 把 firstVisibleItemIndex 重置为 0，LaunchedEffect 拿到的是已被破坏的
                    // 状态。锁存必须在这里完成，不能依赖后续 effect。
                    val beforeIndex = listState.firstVisibleItemIndex
                    val beforeOffset = listState.firstVisibleItemScrollOffset
                    val beforeItemCount = displayItems.size
                    pendingRestoreIndex = beforeIndex
                    pendingRestoreOffset = beforeOffset
                    pendingItemCount = beforeItemCount
                    stableTicks = 0
                    Log.d(
                        "Tester",
                        "PersonScreen: delete confirm pressed, LATCHED index=$beforeIndex offset=$beforeOffset itemCount=$beforeItemCount",
                    )
                    // [修复防御]: onPositive 里立刻同步锚定当前位置（launch 异步，等下帧 Paging
                    // 重查完成后该 scrollToItem 已 dispatch）。下面独立 LaunchedEffect 会兜底再压一次。
                    scope.launch {
                        listState.scrollToItem(beforeIndex, beforeOffset)
                        Log.d(
                            "Tester",
                            "PersonScreen: pre-launch scrollToItem index=$beforeIndex offset=$beforeOffset",
                        )
                    }
                    scope.launch {
                        onDeleteContacts(idsToDelete)
                        Toast.makeText(context, "已删除 ${idsToDelete.size} 个联系人", Toast.LENGTH_SHORT).show()
                        exitSelectMode()
                    }
                },
                isDestructive = true
            )
        }
    }
}

/**
 * 「我的名片」头部组件（独立 Composable 以确保 avatarPath 变化时稳定重组）
 */
@Composable
private fun MyProfileHeader(
    profile: UserProfile?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MiuixTheme.colorScheme.surface,
                    shape = miuixShape(16.dp)
                )
                .clickable {
                    Log.d("PersonPage", "My Profile clicked!")
                    onClick()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ContactAvatar(name = profile?.name ?: "用户", avatarPath = profile?.avatarPath, size = 40)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "我的名片",
                        style = MiuixTheme.textStyles.body1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = profile?.name?.let { "查看和编辑 $it 的信息" } ?: "查看和编辑个人信息",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

/**
 * 联系人列表项（支持长按进入多选、多选模式下的勾选状态）
 */
@Composable
private fun ContactItem(
    contact: Contact,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        BasicComponent(
            title = contact.name,
            startAction = {
                ContactAvatar(name = contact.name, avatarUrl = contact.avatarUrl, avatarPath = contact.avatarPath)
            },
            onClick = null // 由外层 combinedClickable 处理
        )
        // 多选模式下显示勾选标记
        if (isSelectMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "已选" else "未选",
                tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(24.dp)
            )
        }
    }
}

/**
 * 字母索引栏
 *
 * 显示在列表右侧，支持：
 * - 拖动快速定位到对应首字母分组
 * - 点击单个字母跳转（自动延迟300ms隐藏气泡）
 *
 * @param letters 可选的字母列表
 * @param onSelectLetter 选中字母时的回调（触发列表滚动）
 * @param onDragStateChange 拖动状态变化回调 (isDragging, currentLetter)
 * @param modifier 修饰符
 */
@Composable
fun LetterIndexBar(
    letters: List<String>,
    onSelectLetter: (String) -> Unit,
    onDragStateChange: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .padding(vertical = 8.dp)
                .padding(horizontal = 4.dp)
                .pointerInput(letters) {
                    // 拖动手势：根据触摸位置计算对应的字母索引
                    detectDragGestures(
                        onDragStart = { offset ->
                            val index = (offset.y / (size.height / letters.size)).toInt().coerceIn(0, letters.size - 1)
                            val letter = letters[index]
                            onDragStateChange(true, letter)
                            onSelectLetter(letter)
                        },
                        onDrag = { change, _ ->
                            change.consume() // 消费事件，防止传播
                            val index = (change.position.y / (size.height / letters.size)).toInt().coerceIn(0, letters.size - 1)
                            val letter = letters[index]
                            onDragStateChange(true, letter)
                            onSelectLetter(letter)
                        },
                        onDragEnd = {
                            onDragStateChange(false, "")
                        }
                    )
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
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
                            // 点击后300ms自动隐藏气泡
                            coroutineScope.launch {
                                delay(300)
                                onDragStateChange(false, "")
                            }
                        }
                        .padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * 字母气泡提示
 *
 * 拖动字母索引栏时在屏幕中央显示当前字母的大号气泡。
 *
 * @param visible 是否显示
 * @param letter 当前字母
 */
@Composable
fun LetterTooltip(visible: Boolean, letter: String) {
    if (visible && letter.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {} // 拦截触摸事件，防止穿透到下层列表
                .zIndex(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.7f), miuixShape(12.dp))
                    .wrapContentSize(Alignment.Center)
            ) {
                Text(
                    text = letter,
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/** 简单可变引用包装，不触发 Compose 重组合 */
private class Ref<T>(var v: T)
