package top.mcxiafeng.badger.pages.settings.tags

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.ui.components.BatchColorPickerDialog
import top.mcxiafeng.badger.ui.components.TagColorChangeDialog
import top.mcxiafeng.badger.ui.components.TagCreateDialog
import top.mcxiafeng.badger.ui.components.TagDeleteChoiceDialog
import top.mcxiafeng.badger.ui.components.TagMergeTargetPickerDialog
import top.mcxiafeng.badger.ui.components.TagRenameDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「设置 → 标签管理」顶级页面。
 *
 * 设计目标（参见 plan: 标签管理界面重写）：
 * - 单一入口，承载「列表 + 搜索 + 筛选 + 排序 + 多选 + 全部 CRUD + 反馈」。
 * - 移除旧的「弹窗版」按钮——所有操作都能在此页面内完成。
 * - 状态走 [TagManagerSettingsViewModel.uiState]（StateFlow），旋转屏不丢。
 * - 反馈走 [TagManagerSettingsViewModel.messages] Channel → Snackbar。
 *
 * 与历史 [top.mcxiafeng.badger.pages.person.contact.TagManagerDialog] 关系：
 * 旧 Dialog 已删除，其内嵌的"改名/换色/删除/合并"子 Dialog 统一挪到
 * [TagManagerDialogs.kt]，本页与详情页入口的 TagQuickManageDialog 共享。
 */
@Composable
fun TagManagerSettingsPage(
    onBack: () -> Unit,
    viewModel: TagManagerSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    // Dialog flag 全部在这里集中管理（遵循 feedback_dialog_rules.md 的 flag 重置规则）
    var showSearch by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Tag?>(null) }
    var colorTarget by remember { mutableStateOf<Tag?>(null) }
    var deleteTarget by remember { mutableStateOf<Tag?>(null) }
    var showMergeForDelete by remember { mutableStateOf<Tag?>(null) }
    var showBatchColor by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // 把 VM 的消息流转成 Snackbar
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(
                message = msg.text,
                duration = SnackbarDuration.Custom(1800),
            )
        }
    }

    // BackHandler：多选 / 搜索 / 任一 Dialog 打开 / 排序菜单 → 退出当前模式，不退出页面
    val isInSpecialMode by remember {
        derivedStateOf {
            val s = uiState
            (s is TagManagerUiState.Success && (s.multiSelect || s.selectedIds.isNotEmpty())) ||
                showSearch || showSortMenu || showCreate || renameTarget != null || colorTarget != null ||
                deleteTarget != null || showMergeForDelete != null || showBatchColor
        }
    }
    BackHandler(enabled = isInSpecialMode) {
        Log.d(TAG, "BackHandler: exit special mode")
        when {
            showMergeForDelete != null -> showMergeForDelete = null
            deleteTarget != null -> deleteTarget = null
            renameTarget != null -> renameTarget = null
            colorTarget != null -> colorTarget = null
            showBatchColor -> showBatchColor = false
            showCreate -> showCreate = false
            showSearch -> showSearch = false
            showSortMenu -> showSortMenu = false
            else -> {
                val s = uiState
                if (s is TagManagerUiState.Success && (s.multiSelect || s.selectedIds.isNotEmpty())) {
                    viewModel.onEvent(TagManagerEvent.ExitMultiSelect)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "标签管理",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = { TagManagerTopActions(
                    isMultiSelect = (uiState as? TagManagerUiState.Success)?.multiSelect == true,
                    onExitMultiSelect = { viewModel.onEvent(TagManagerEvent.ExitMultiSelect) },
                    showSortMenu = showSortMenu,
                    onOpenSortMenu = { showSortMenu = true },
                    onDismissSortMenu = { showSortMenu = false },
                    onSelectSort = { mode ->
                        viewModel.onEvent(TagManagerEvent.ChangeSort(mode))
                        showSortMenu = false
                    },
                    currentSort = (uiState as? TagManagerUiState.Success)?.sortMode ?: TagSortMode.Alphabetical,
                    onToggleSearch = {
                        showSearch = !showSearch
                        if (!showSearch) query = ""
                    },
                ) }
            )
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        floatingActionButton = {
            // 多选态不显示 FAB（避免和批量操作视觉冲突）。
            // 不再附加 Modifier.padding —— Scaffold 的 FabPosition.End 已经处理好
            // 避让 bottomBar / 浮动工具栏的距离；手动加 padding 会造成双重偏移。
            // 空态始终显示：FAB 是唯一的「新建标签」入口，避免和列表元素双入口挤在一起
            // 让用户觉得"FAB 外多了一圈"。
            val s = uiState
            val inMultiSelect = s is TagManagerUiState.Success && s.multiSelect
            if (!inMultiSelect) {
                FloatingActionButton(
                    onClick = { showCreate = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建标签",
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        // 批量操作栏挂底部，避免与 FAB/Snackbar 互挡。
        // empty lambda 的 box 不渲染内容，占位保持高度稳定（FAB 位置不会跳）。
        bottomBar = {
            val s = uiState
            if (s is TagManagerUiState.Success && s.multiSelect) {
                BatchActionBar(
                    totalCount = s.visibleTags.size,
                    selectedCount = s.selectedIds.size,
                    onSelectAll = { viewModel.onEvent(TagManagerEvent.SelectAll) },
                    onClear = { viewModel.onEvent(TagManagerEvent.ClearSelection) },
                    onColor = { showBatchColor = true },
                    onDelete = {
                        val ids = s.selectedIds.toList()
                        if (ids.isNotEmpty()) {
                            viewModel.onEvent(TagManagerEvent.BatchDelete(ids))
                        }
                    },
                )
            }
        },
    ) { padding ->
        val currentState = uiState
        when {
            currentState is TagManagerUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            }

            currentState is TagManagerUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "加载失败：${currentState.message}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(12.dp))
                        TextButton(text = "重试", onClick = { viewModel.onEvent(TagManagerEvent.Refresh) })
                    }
                }
            }

            currentState is TagManagerUiState.Success -> TagManagerSuccessBody(
                state = currentState,
                paddingValues = padding,
                showSearch = showSearch,
                query = query,
                onQueryChange = { query = it },
                onCloseSearch = { showSearch = false; query = "" },
                onClickTag = { tag ->
                    if (currentState.multiSelect) {
                        viewModel.onEvent(TagManagerEvent.ToggleSelect(tag.id))
                    } else {
                        renameTarget = tag
                    }
                },
                onLongClickTag = { tag ->
                    if (!currentState.multiSelect) {
                        viewModel.onEvent(TagManagerEvent.EnterMultiSelect(initialSelectedId = tag.id))
                    }
                },
                onSetShowDot = { id, v -> viewModel.onEvent(TagManagerEvent.SetShowDot(id, v)) },
                onClickColor = { tag -> colorTarget = tag },
                onClickDelete = { tag -> deleteTarget = tag },
                onChangeFilter = { viewModel.onEvent(TagManagerEvent.ChangeFilter(it)) },
            )
        }
    }

    // ========== Dialog 弹出 ==========

    if (showCreate) {
        TagCreateDialog(
            show = true,
            onDismiss = { showCreate = false },
            onCreate = { name, color ->
                viewModel.onEvent(TagManagerEvent.Create(name, color))
                showCreate = false
            },
        )
    }
    renameTarget?.let { tag ->
        TagRenameDialog(
            show = true,
            tag = tag,
            onDismiss = { renameTarget = null },
            onSave = { newName ->
                viewModel.onEvent(TagManagerEvent.Rename(tag.id, newName))
                renameTarget = null
            },
        )
    }
    colorTarget?.let { tag ->
        TagColorChangeDialog(
            show = true,
            tag = tag,
            onDismiss = { colorTarget = null },
            onSave = { argb ->
                viewModel.onEvent(TagManagerEvent.SetColor(tag.id, argb))
                colorTarget = null
            },
        )
    }
    deleteTarget?.let { tag ->
        TagDeleteChoiceDialog(
            show = true,
            tag = tag,
            onDismiss = { deleteTarget = null },
            onConfirmMerge = {
                // 进入合并目标选择：保留 deleteTarget 直到用户选定目标
                showMergeForDelete = tag
            },
            onConfirmForceDelete = {
                viewModel.onEvent(TagManagerEvent.ForceDelete(tag.id))
                deleteTarget = null
            },
        )
    }
    showMergeForDelete?.let { source ->
        val candidates = (uiState as? TagManagerUiState.Success)
            ?.tags
            ?.filter { it.id != source.id }
            .orEmpty()
        TagMergeTargetPickerDialog(
            show = true,
            sourceTag = source,
            candidates = candidates,
            onDismiss = { showMergeForDelete = null },
            onPicked = { target ->
                viewModel.onEvent(TagManagerEvent.Merge(source.id, target.id))
                showMergeForDelete = null
                deleteTarget = null
            },
        )
    }

    if (showBatchColor) {
        val current = uiState
        val selectedIds = (current as? TagManagerUiState.Success)?.selectedIds?.toList().orEmpty()
        if (selectedIds.isNotEmpty()) {
            BatchColorPickerDialog(
                show = true,
                onDismiss = { showBatchColor = false },
                onPick = { argb ->
                    viewModel.onEvent(TagManagerEvent.BatchSetColor(selectedIds, argb))
                    showBatchColor = false
                },
            )
        } else {
            showBatchColor = false
        }
    }
}


private const val TAG = "TagManagerSettingsPage"
