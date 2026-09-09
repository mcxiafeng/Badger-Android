package top.mcxiafeng.badger.pages.settings.tags

import androidx.compose.foundation.background
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
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
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.pages.settings.components.SettingsMessageEffect
import top.mcxiafeng.badger.pages.settings.components.SettingsSubPageScaffold
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
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.BackHandler

private const val TAG = "TagManagerSettingsPage"

/**
 * 「设置 → 标签管理」页（重写：共享脚手架 + SettingsMessageEffect + 删假 Refresh）。
 *
 * 单页承载列表 + 搜索 + 筛选 + 排序 + 多选 + 全部 CRUD + 反馈。
 * 状态走 [TagManagerSettingsViewModel.uiState]（StateFlow）；反馈走 messages Channel（SettingsUiMessage）。
 */
@Composable
fun TagManagerSettingsPage(
    onBack: () -> Unit,
    viewModel: TagManagerSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSearch by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Tag?>(null) }
    var colorTarget by remember { mutableStateOf<Tag?>(null) }
    var deleteTarget by remember { mutableStateOf<Tag?>(null) }
    var showMergeForDelete by remember { mutableStateOf<Tag?>(null) }
    var showBatchColor by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    SettingsMessageEffect(snackbarHostState, viewModel.messages)

    // BackHandler：多选 / 搜索 / 任一 Dialog 打开 / 排序菜单 → 退出当前模式
    val isInSpecialMode by remember {
        derivedStateOf {
            val s = uiState
            (s is TagManagerUiState.Success && (s.multiSelect || s.selectedIds.isNotEmpty())) ||
                showSearch || showSortMenu || showCreate || renameTarget != null || colorTarget != null ||
                deleteTarget != null || showMergeForDelete != null || showBatchColor
        }
    }
    BackHandler(enabled = isInSpecialMode) {
        BadgerLog.d(TAG, "BackHandler: exit special mode")
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

    SettingsSubPageScaffold(
        title = SettingsPage.TagManager.title,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            TagManagerTopActions(
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
            )
        },
        floatingActionButton = {
            // 多选态不显示 FAB（避免和批量操作视觉冲突）。
            val s = uiState
            val inMultiSelect = s is TagManagerUiState.Success && s.multiSelect
            if (!inMultiSelect) {
                FloatingActionButton(onClick = { showCreate = true }) {
                    Icon(
                        imageVector = Lucide.Plus,
                        contentDescription = "新建标签",
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
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
                // 只读本地订阅（DB Flow），错误来自查询，无 retry 必要
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "加载失败：${currentState.message}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }

            currentState is TagManagerUiState.Success -> TagManagerSuccessBody(
                state = currentState,
                paddingValues = padding,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshFromServer() },
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

    // ========== Dialog 弹出（Pattern A）==========

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
