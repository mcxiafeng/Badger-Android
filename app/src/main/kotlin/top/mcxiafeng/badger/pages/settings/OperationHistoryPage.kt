package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "OpHistoryPage"

/**
 * [V2-P7] 操作历史页(对应 `docs/BADGER_V2_CLIENT_PLAN.md` §6)。
 *
 * 入口位于 `SettingsPage` 的"标签管理"与"服务器设置"之间,与 §6.1 拍板一致。
 *
 * 视觉/交互规范:
 * - 列表 LazyColumn + Card(Miuix),间距 12.dp(纵向)+ 8.dp(横向)。
 * - 顶部两个 Tab:"全部" / "待处理"(对应 HistoryFilter.All / Pending)。
 * - 列表项:联系人名 + opLabel + 时间 + 状态徽章。
 * - 点击列表项 → 弹出 [OperationHistoryDetailDialog](显示 payload/snapshotBefore/lastError + 操作按钮)。
 * - 状态徽章颜色集中在 [statusBadgeColor] 函数,不在 Composable 散落判断。
 *
 * Dialog flag 重置:严格按 feedback_dialog_rules.md,详情 dialog 三条路径
 * (dismissRequest / 关闭按钮 / 操作按钮 onClick)都重置 `selectedEntity = null`。
 *
 * BackHandler:详情 dialog 打开时拦截,关闭 dialog 而非退出页面。
 */
@Composable
internal fun OperationHistoryPage(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: OperationHistoryViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    var selectedEntity by remember { mutableStateOf<OperationHistoryWithContact?>(null) }

    // Snackbar 桥接:VM 推 message → Composable 消费
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(
                message = msg.text,
                duration = SnackbarDuration.Custom(1800),
            )
        }
    }

    // BackHandler:详情 dialog 打开时按返回键关闭 dialog 而非退出页面
    val isInDetailMode by remember {
        derivedStateOf { selectedEntity != null }
    }
    BackHandler(enabled = isInDetailMode) {
        Log.d(TAG, "BackHandler: close detail dialog")
        selectedEntity = null
    }

    // [V2-P10] 多选态下按返回键:退出多选模式而非退出页面
    val isInMultiSelect by remember {
        derivedStateOf {
            val s = uiState
            s is OperationHistoryUiState.Success && s.multiSelect
        }
    }
    BackHandler(enabled = isInMultiSelect) {
        Log.d(TAG, "BackHandler: exit multi select")
        viewModel.onEvent(OperationHistoryEvent.ExitMultiSelect)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "历史操作",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    // [V2-P10] 多选态切换:正常态显示 DoneAll IconButton,多选态显示"取消" TextButton
                    val s = uiState
                    if (s is OperationHistoryUiState.Success && s.multiSelect) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                Log.d(TAG, "TopAppBar: cancel multi select")
                                viewModel.onEvent(OperationHistoryEvent.ExitMultiSelect)
                            },
                        )
                    } else {
                        IconButton(onClick = {
                            Log.d(TAG, "TopAppBar: enter multi select")
                            viewModel.onEvent(OperationHistoryEvent.EnterMultiSelect())
                        }) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "进入多选",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        bottomBar = {
            // [V2-P10] 多选态显示底部 BatchActionBar(全选/重发/撤销)
            val s = uiState
            if (s is OperationHistoryUiState.Success && s.multiSelect) {
                OpHistoryBatchActionBar(
                    totalCount = s.records.size,
                    selectedIds = s.selectedIds,
                    records = s.records,
                    onSelectAll = { viewModel.onEvent(OperationHistoryEvent.SelectAll) },
                    onClear = { viewModel.onEvent(OperationHistoryEvent.ClearSelection) },
                    onBatchRetry = {
                        viewModel.onEvent(OperationHistoryEvent.BatchRetry(s.selectedIds.toList()))
                    },
                    onBatchWithdraw = {
                        viewModel.onEvent(OperationHistoryEvent.BatchWithdraw(s.selectedIds.toList()))
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = floatingBarBottomPadding),
        ) {
            OperationHistoryFilterTab(
                currentFilter = viewModel.currentFilter(),
                onFilterChange = { viewModel.onEvent(OperationHistoryEvent.ChangeFilter(it)) },
            )

            val currentState = uiState
            when (currentState) {
                is OperationHistoryUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "加载中...",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                is OperationHistoryUiState.Empty -> {
                    OperationHistoryEmptyState(filter = currentState.filter)
                }

                is OperationHistoryUiState.Success -> {
                    OperationHistoryList(
                        records = currentState.records,
                        selectedIds = currentState.selectedIds,
                        multiSelect = currentState.multiSelect,
                        onClickRecord = { item ->
                            if (currentState.multiSelect) {
                                Log.d(TAG, "Row click in multiSelect: opId=${item.history.opId.take(8)}")
                                viewModel.onEvent(OperationHistoryEvent.ToggleSelect(item.history.opId))
                            } else {
                                selectedEntity = item
                            }
                        },
                        onLongClickRecord = { item ->
                            if (!currentState.multiSelect) {
                                Log.d(TAG, "Row long click: enter multi select opId=${item.history.opId.take(8)}")
                                viewModel.onEvent(OperationHistoryEvent.EnterMultiSelect(item.history.opId))
                            }
                        },
                    )
                }

                is OperationHistoryUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "加载失败:${currentState.message}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.error,
                            )
                            Spacer(Modifier.size(12.dp))
                            TextButton(
                                text = "重试",
                                onClick = { viewModel.onEvent(OperationHistoryEvent.Refresh) },
                            )
                        }
                    }
                }
            }
        }
    }

    // 详情 dialog(三路径关闭:dismissRequest / 关闭按钮 / 操作按钮 → 都重置 selectedEntity)
    selectedEntity?.let { entity ->
        OperationHistoryDetailDialog(
            entity = entity,
            onDismiss = { selectedEntity = null },
            onRetry = {
                viewModel.onEvent(OperationHistoryEvent.Retry(entity.history.opId))
                selectedEntity = null
            },
            onWithdraw = {
                viewModel.onEvent(OperationHistoryEvent.Withdraw(entity.history.opId))
                selectedEntity = null
            },
            onAdoptLocal = {
                viewModel.onEvent(OperationHistoryEvent.AdoptLocal(entity.history.opId))
                selectedEntity = null
            },
            onAdoptServer = {
                // P7 阶段:服务端 contact JSON 由 PendingUploadExecutor 写入 history.lastError 中
                // (ConflictException 内部 catch 写 markConflict 把 serverContact JSON 带到 lastError).
                // 这里直接取 lastError 字段作为 serverContactJson。
                val serverContactJson = entity.history.lastError ?: ""
                viewModel.onEvent(
                    OperationHistoryEvent.AdoptServer(
                        opId = entity.history.opId,
                        serverContactJson = serverContactJson,
                    )
                )
                selectedEntity = null
            },
        )
    }
}

/**
 * 顶部 filter tab(全部 / 待处理)。
 */
@Composable
private fun OperationHistoryFilterTab(
    currentFilter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val primary = MiuixTheme.colorScheme.primary
    val index = HistoryFilter.entries.indexOf(currentFilter).coerceAtLeast(0)
    TabRowWithContour(
        tabs = HistoryFilter.entries.map { OperationHistoryOpFormatter.formatFilterLabel(it) },
        selectedTabIndex = index,
        onTabSelected = { idx -> onFilterChange(HistoryFilter.entries[idx]) },
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = cs.surface,
            contentColor = cs.onSurfaceVariantSummary,
            selectedBackgroundColor = cs.surface,
            selectedContentColor = primary,
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * 列表项。
 */
@Composable
private fun OperationHistoryList(
    records: List<OperationHistoryWithContact>,
    selectedIds: Set<String>,
    multiSelect: Boolean,
    onClickRecord: (OperationHistoryWithContact) -> Unit,
    onLongClickRecord: (OperationHistoryWithContact) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(records, key = { it.history.opId }) { item ->
            OperationHistoryRow(
                item = item,
                selected = item.history.opId in selectedIds,
                multiSelect = multiSelect,
                onClick = { onClickRecord(item) },
                onLongClick = { onLongClickRecord(item) },
            )
        }
    }
}

@Composable
private fun OperationHistoryRow(
    item: OperationHistoryWithContact,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val op = item.history
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // [V2-P10] 多选态显示 CheckBox
                if (multiSelect) {
                    androidx.compose.material3.Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = cs.primary,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = OperationHistoryOpFormatter.formatContactName(item.contactName),
                    style = MiuixTheme.textStyles.body1,
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(opStatus = op.opStatus)
            }
            Text(
                text = OperationHistoryOpFormatter.formatListSubtitle(item),
                style = MiuixTheme.textStyles.footnote1,
                color = cs.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (op.lastError != null && op.lastError.isNotBlank()) {
                Text(
                    text = "错误:${op.lastError.take(60)}${if (op.lastError.length > 60) "..." else ""}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * [V2-P10] 多选态底部 BatchActionBar(全选 + 重发 + 撤销)。
 *
 * 风格与 TagManagerSettingsPage.BatchActionBar 一致:
 * - background = surfaceContainer
 * - padding 注入 LocalFloatingBarBottomPadding 避开 NavigationBar
 * - "全选"按钮:全部已选时变 "取消全选"
 * - "重发":enabled = 选中 ≥1 条且存在可重试(FAILED)的 op;否则灰
 * - "撤销":enabled = 选中 ≥1 条且存在可撤销(canUndo + 非 WITHDRAWN + 非 CONFLICT)的 op;否则灰
 */
@Composable
private fun OpHistoryBatchActionBar(
    totalCount: Int,
    selectedIds: Set<String>,
    records: List<OperationHistoryWithContact>,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onBatchRetry: () -> Unit,
    onBatchWithdraw: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val floatingBarBottomPadding = top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding.current
    val allSelected = selectedIds.size == totalCount && totalCount > 0
    // [修复防御]: 用 selectedIds 实际对应的 records 计算 enabled,而不是 selectedIds.size > 0
    // (避免选中 0 条 FAILED 时误以为可重发,或选中全部 CONFLICT 时误以为可撤销)。
    val selectedRecords = remember(records, selectedIds) {
        records.filter { it.history.opId in selectedIds }
    }
    val retryEligible = selectedRecords.any { OperationHistoryOpFormatter.canBatchRetry(it.history) }
    val withdrawEligible = selectedRecords.any { OperationHistoryOpFormatter.canBatchWithdraw(it.history) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer)
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp + floatingBarBottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            text = "已选 ${selectedIds.size}",
            enabled = false,
            onClick = {},
        )
        TextButton(
            text = if (allSelected) "取消全选" else "全选",
            onClick = if (allSelected) onClear else onSelectAll,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            text = "重发",
            enabled = retryEligible,
            onClick = onBatchRetry,
        )
        TextButton(
            text = "撤销",
            enabled = withdrawEligible,
            onClick = onBatchWithdraw,
            colors = ButtonDefaults.textButtonColors(
                color = cs.error,
                disabledColor = cs.disabledSecondaryVariant,
                textColor = cs.onError,
                disabledTextColor = cs.disabledOnSecondaryVariant,
            ),
        )
    }
}

/**
 * 状态徽章(带颜色背景的小方块 + 中文 label)。
 */
@Composable
private fun StatusBadge(opStatus: String) {
    val color = statusBadgeColor(opStatus)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = OperationHistoryOpFormatter.formatStatusLabel(opStatus),
            style = MiuixTheme.textStyles.footnote2,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 状态徽章颜色,按 §6.2 拍板:
 * - PENDING/IN_FLIGHT:蓝灰
 * - DONE:primary(成功)
 * - CONFLICT/FAILED/FAILED_PERMANENT:error 红
 * - WITHDRAWN:onSurfaceVariantSummary 灰
 */
@Composable
private fun statusBadgeColor(opStatus: String): Color {
    val cs = MiuixTheme.colorScheme
    return when (opStatus) {
        "PENDING", "IN_FLIGHT" -> cs.onSurfaceVariantSummary
        "DONE" -> cs.primary
        "CONFLICT", "FAILED", "FAILED_PERMANENT" -> cs.error
        "WITHDRAWN" -> cs.outline
        else -> cs.onSurfaceVariantSummary
    }
}

/**
 * 空态。
 */
@Composable
private fun OperationHistoryEmptyState(filter: HistoryFilter) {
    val text = when (filter) {
        HistoryFilter.All -> "还没有任何操作记录"
        HistoryFilter.Pending -> "没有需要处理的操作"
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = text,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "对联系人的修改会出现在这里,你可以在此撤销、重发或解决冲突。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/**
 * 详情 dialog。显示 opLabel / 联系人 / 时间 / status / payload / lastError,以及
 * 撤销 / 重发 / 采用本地 / 采用服务端 按钮(按状态置灰)。
 */
@Composable
private fun OperationHistoryDetailDialog(
    entity: OperationHistoryWithContact,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onWithdraw: () -> Unit,
    onAdoptLocal: () -> Unit,
    onAdoptServer: () -> Unit,
) {
    val op = entity.history
    val cs = MiuixTheme.colorScheme
    WindowDialog(
        show = true,
        title = OperationHistoryOpFormatter.formatContactName(entity.contactName),
        summary = OperationHistoryOpFormatter.formatDetailSummary(entity),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            DetailField(label = "状态", value = OperationHistoryOpFormatter.formatStatusLabel(op.opStatus))
            DetailField(label = "时间", value = OperationHistoryOpFormatter.formatTimestampLong(op.createdAt))
            DetailField(label = "操作", value = top.mcxiafeng.badger.data.queue.OperationTypes.labelOf(op.opType))
            if (op.serverVersion != null) {
                DetailField(label = "服务端版本", value = op.serverVersion.toString())
            }
            DetailField(label = "尝试次数", value = op.attempts.toString())
            if (op.lastError != null && op.lastError.isNotBlank()) {
                DetailField(label = "错误", value = op.lastError, isError = true)
            }
            if (op.inversePayloadJson != null && op.inversePayloadJson.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "反向 Payload",
                    style = MiuixTheme.textStyles.footnote1,
                    color = cs.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(cs.surfaceVariant)
                        .padding(8.dp),
                ) {
                    Text(
                        text = op.inversePayloadJson,
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = cs.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 按钮组:按 opStatus 拼装
            val isUndoDisabled = OperationHistoryOpFormatter.isUndoDisabled(op)
            val isRetryDisabled = OperationHistoryOpFormatter.isRetryDisabled(op)
            val isResolveDisabled = OperationHistoryOpFormatter.isResolveDisabled(op)

            // 主要按钮:CONFLICT 走"采用本地 / 采用服务端",否则走"重发"
            when {
                !isResolveDisabled -> {
                    DialogButtonRow(
                        negativeText = "关闭",
                        positiveText = "采用本地",
                        onNegative = onDismiss,
                        onPositive = onAdoptLocal,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        text = "采用服务端",
                        onClick = onAdoptServer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                !isRetryDisabled -> {
                    DialogButtonRow(
                        negativeText = "关闭",
                        positiveText = "重发",
                        onNegative = onDismiss,
                        onPositive = onRetry,
                    )
                }
                else -> {
                    DialogButtonRow(
                        negativeText = "关闭",
                        positiveText = "关闭",
                        onNegative = onDismiss,
                        onPositive = onDismiss,
                    )
                }
            }

            // 撤销按钮:CONFLICT / WITHDRAWN / FAILED_PERMANENT / DONE+!canUndo 时置灰
            Spacer(Modifier.height(4.dp))
            TextButton(
                text = "撤销",
                onClick = onWithdraw,
                enabled = !isUndoDisabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DetailField(label: String, value: String, isError: Boolean = false) {
    val cs = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = cs.onSurfaceVariantSummary,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = if (isError) cs.error else cs.onSurface,
        )
    }
}
