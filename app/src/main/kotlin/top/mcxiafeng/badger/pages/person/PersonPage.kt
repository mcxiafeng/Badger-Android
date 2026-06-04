package top.mcxiafeng.badger.pages.person

import android.util.Log
import android.widget.Toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.SortedMap
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.ui.components.AvatarPlaceholder
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.PinyinUtils
import top.mcxiafeng.badger.pages.person.PersonUiState
import top.mcxiafeng.badger.pages.person.PersonViewModel
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
import top.yukonga.miuix.kmp.theme.miuixShape
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.pages.person.contact.ToolbarAction
import top.mcxiafeng.badger.ui.components.DialogButtonRow

/**
 * 联系人页面
 *
 * 功能：
 * - 显示按拼音首字母分组的联系人列表
 * - 支持搜索过滤
 * - 右侧字母索引栏快速定位
 * - 拖动索引时显示字母气泡提示
 * - 悬浮添加按钮（点击打开扫描页）
 *
 * @param onAddContact 添加联系人回调（打开扫描页）
 */
@Composable
fun PersonRoute(onAddContact: () -> Unit = {}, onContactClick: (Long) -> Unit = {}) {
    val viewModel: PersonViewModel = hiltViewModel()
    val uiState: PersonUiState by viewModel.uiState.collectAsStateWithLifecycle()
    PersonScreen(
        uiState = uiState,
        repository = viewModel.repository,
        userProfileRepository = viewModel.userProfileRepository,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSortTypeChange = viewModel::updateSortType,
        onAddContact = onAddContact,
        onContactClick = onContactClick
    )
}

@Composable
fun PersonScreen(
    uiState: PersonUiState,
    repository: ContactRepository,
    userProfileRepository: UserProfileRepository,
    onSearchQueryChange: (String) -> Unit = {},
    onSortTypeChange: (Int) -> Unit = {},
    onAddContact: () -> Unit = {},
    onContactClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val userProfile by userProfileRepository.getUserProfile().collectAsStateWithLifecycle(initialValue = null)

    val successState = (uiState as? PersonUiState.Success)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var searchExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // 多选状态
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // ViewModel 已处理排序和搜索过滤，直接使用 filteredContacts
    // 按首字母分组
    val grouped: SortedMap<String, List<Contact>> = remember(successState?.filteredContacts) {
        (successState?.filteredContacts ?: emptyList()).groupBy { PinyinUtils.getContactPinyinInitial(it.name) }.toSortedMap()
    }
    
    // 字母索引列表
    val letters: List<String> = remember(grouped) { grouped.keys.toList() }

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

    // 全选/取消全选
    val allFilteredIds = remember(successState?.filteredContacts) { (successState?.filteredContacts ?: emptyList()).map { it.id }.toSet() }
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
            val emptyNoSearch = grouped.isEmpty() && (successState?.searchQuery ?: "").isBlank()
                && uiState !is PersonUiState.Loading && uiState !is PersonUiState.Error

            if (emptyNoSearch) {
                // 空状态：使用 Column 让空状态文本正确居中在搜索栏和名片下方
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    SearchBar(
                        inputField = {
                            InputField(
                                query = successState?.searchQuery ?: "",
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
                                    val avatarPath = userProfile?.avatarPath
                                    var profileAvatarBitmap by remember(avatarPath) {
                                        mutableStateOf<android.graphics.Bitmap?>(null)
                                    }
                                    LaunchedEffect(avatarPath) {
                                        profileAvatarBitmap = Methods.loadAvatarBitmap(avatarPath)
                                    }
                                    val avatarBmp = profileAvatarBitmap
                                    if (avatarBmp != null) {
                                        Image(
                                            bitmap = avatarBmp.asImageBitmap(),
                                            contentDescription = "头像",
                                            modifier = Modifier.size(40.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        AvatarPlaceholder(name = userProfile?.name ?: "用户", size = 40)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "我的名片",
                                        style = MiuixTheme.textStyles.body1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = userProfile?.name?.let { "查看和编辑 $it 的信息" } ?: "查看和编辑个人信息",
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
                                query = successState?.searchQuery ?: "",
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
                if (grouped.isNotEmpty()) {
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
                                    val avatarPath = userProfile?.avatarPath
                                    var profileAvatarBitmap by remember(avatarPath) {
                                        mutableStateOf<android.graphics.Bitmap?>(null)
                                    }
                                    LaunchedEffect(avatarPath) {
                                        profileAvatarBitmap = Methods.loadAvatarBitmap(avatarPath)
                                    }
                                    val avatarBmp = profileAvatarBitmap
                                    if (avatarBmp != null) {
                                        Image(
                                            bitmap = avatarBmp.asImageBitmap(),
                                            contentDescription = "头像",
                                            modifier = Modifier.size(40.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        AvatarPlaceholder(name = userProfile?.name ?: "用户", size = 40)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "我的名片",
                                        style = MiuixTheme.textStyles.body1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = userProfile?.name?.let { "查看和编辑 $it 的信息" } ?: "查看和编辑个人信息",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }
                    }
                }

                // 状态展示：加载中 / 错误 / 有搜索无结果 / 联系人分组列表
                if (uiState is PersonUiState.Loading) {
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
                } else if (uiState is PersonUiState.Error) {
                    item(key = "error") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "加载失败: ${(uiState as PersonUiState.Error).message}",
                                color = MiuixTheme.colorScheme.error,
                                style = MiuixTheme.textStyles.body1
                            )
                        }
                    }
                } else if (grouped.isEmpty()) {
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
                    // 按首字母分组展示联系人
                    grouped.forEach { (letter, contacts) ->
                        // 分组标题（首字母）
                        item(key = "h_$letter") {
                            Text(
                                text = letter,
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        // 该分组下的联系人列表
                        itemsIndexed(contacts, key = { _, c -> "c_${c.id}" }) { _, contact ->
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

            // 字母索引栏（仅在非选择模式且有联系人分组时显示）
            if (!isSelectMode && grouped.isNotEmpty()) {
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
                                // 在 grouped 中找到对应字母的分组
                                if (grouped.containsKey(letter)) {
                                    // 计算目标位置：搜索栏(1) + 名片(1) + 前面所有分组的总项数
                                    var offset = 2 // 搜索栏 + 名片项
                                    for ((key, contacts) in grouped) {
                                        if (key == letter) break
                                        offset += contacts.size + 1 // 标题 + 联系人数量
                                    }
                                    scope.launch { listState.animateScrollToItem(offset) }
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
                scope.launch(Dispatchers.IO) {
                    idsToDelete.forEach { id ->
                        val contact = repository.getContactById(id)
                        if (contact != null) {
                            repository.deleteContact(contact)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已删除 ${idsToDelete.size} 个联系人", Toast.LENGTH_SHORT).show()
                        exitSelectMode()
                    }
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

/** 字母索引栏
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
                    // 使用 detectDragGestures 但只在水平方向触发
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
