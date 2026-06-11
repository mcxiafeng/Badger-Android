package top.mcxiafeng.badger.pages.person

import android.util.Log
import android.widget.Toast

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.UserProfile
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.utils.PinyinUtils
import top.mcxiafeng.badger.pages.person.contact.ToolbarAction
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.miuixShape
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
    val lazyPagingItems = viewModel.contactsPagingData.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults = viewModel.searchResultsPagingData.collectAsLazyPagingItems()
    val letterCounts by viewModel.letterCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    PersonScreen(
        lazyPagingItems = lazyPagingItems,
        searchResults = searchResults,
        searchQuery = searchQuery,
        letterCounts = letterCounts,
        userProfile = viewModel.userProfile,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onAddContact = onAddContact,
        onContactClick = onContactClick,
        onDeleteContacts = { ids -> scope.launch { viewModel.deleteContacts(ids) } }
    )
}

@Composable
fun PersonScreen(
    lazyPagingItems: LazyPagingItems<Contact>,
    searchResults: LazyPagingItems<Contact>,
    searchQuery: String,
    letterCounts: List<LetterCount>,
    userProfile: StateFlow<UserProfile?>,
    onSearchQueryChange: (String) -> Unit = {},
    onAddContact: () -> Unit = {},
    onContactClick: (Long) -> Unit = {},
    onDeleteContacts: suspend (List<Long>) -> Unit = {}
) {
    val context = LocalContext.current
    val profile by userProfile.collectAsStateWithLifecycle(initialValue = null)

    // 使用 rememberSaveable + LazyListState.Saver，确保从详情页返回时滚动位置被保留
    // （自定义栈式导航 + AnimatedContent 会让 Composable 退出 composition，普通 remember 会丢状态）
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var searchExpanded by remember { mutableStateOf(false) }

    // 多选状态
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 确定使用哪个 PagingItems 展示
    val displayItems = if (searchQuery.isBlank()) lazyPagingItems else searchResults

    // 根因修复：rememberSaveable 恢复了 LazyListState 的 firstVisibleItemIndex/Offset，但
    // PersonRoute 重新进入 composition 时，collectAsLazyPagingItems() 会创建全新的 LazyPagingItems
    // 实例，items 还没加载到 savedIndex，LazyColumn 只能滚到 0。等 itemCount >= savedIndex 后再
    // 显式 scrollToItem 即可。用普通 remember 记录"已恢复"标志，因为 Composable 每次重新进入时都
    // 需要重新执行一次恢复（rememberSaveable 会让标志跨次保留，但 listState 也保留了，无需重复）。
    var hasRestoredScroll by remember { mutableStateOf(false) }
    LaunchedEffect(displayItems.itemCount) {
        if (!hasRestoredScroll && displayItems.itemCount > 0) {
            val savedIndex = listState.firstVisibleItemIndex
            val savedOffset = listState.firstVisibleItemScrollOffset
            if (savedIndex > 0 || savedOffset > 0) {
                if (displayItems.itemCount > savedIndex) {
                    listState.scrollToItem(savedIndex, savedOffset)
                    Log.d("Tester", "PersonScreen: restored scroll index=$savedIndex offset=$savedOffset itemCount=${displayItems.itemCount}")
                }
            }
            hasRestoredScroll = true
        }
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

    // 全选/取消全选（基于当前已加载的分页项）
    val allFilteredIds = remember(displayItems.itemCount) {
        (0 until displayItems.itemCount).mapNotNull { displayItems.peek(it)?.id }.toSet()
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
                && displayItems.loadState.refresh !is LoadState.Loading
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
                                    onContactClick(-1L)
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
                                        onContactClick(-1L)
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

                    // 状态展示：加载中 / 错误 / 有搜索无结果 / 联系人分页列表
                    if (displayItems.loadState.refresh is LoadState.Loading) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("加载中...", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body1)
                            }
                        }
                    } else if (displayItems.loadState.refresh is LoadState.Error) {
                        item(key = "error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "加载失败: ${(displayItems.loadState.refresh as LoadState.Error).error.message}",
                                    color = MiuixTheme.colorScheme.error,
                                    style = MiuixTheme.textStyles.body1
                                )
                            }
                        }
                    } else if (displayItems.itemCount == 0) {
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
                            count = displayItems.itemCount,
                            key = displayItems.itemKey { "c_${it.id}" },
                            contentType = displayItems.itemContentType { "contact" }
                        ) { index ->
                            val contact = displayItems[index] ?: return@items

                            // 检测是否需要显示字母标题
                            val currentLetter = PinyinUtils.getContactPinyinInitial(contact.name)
                            val prevLetter = if (index > 0) {
                                displayItems.peek(index - 1)?.let { PinyinUtils.getContactPinyinInitial(it.name) }
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
                                    // 跳转到"我的名片"（index 1，搜索栏是 index 0）
                                    scope.launch { listState.animateScrollToItem(1) }
                                }
                                else -> {
                                    // 计算目标位置：固定项 + 前面所有字母分组的联系人数量
                                    val offset = fixedItemCount +
                                        letterCounts
                                            .takeWhile { it.letter < letter }
                                            .sumOf { it.count }
                                    scope.launch { listState.animateScrollToItem(offset) }
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
