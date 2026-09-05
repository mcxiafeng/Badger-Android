package top.mcxiafeng.badger.pages.settings.history

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.BackHandler

private const val TAG = "OpHistoryPage"

/**
 * [V2-P7] 操作历史页。
 *
 * [Phase 3] 降级为**只读本地日志**：队列退役后不再提供撤销 / 重发 / 冲突解决入口，
 * 列表只读展示历史写操作；详情 dialog 仅显示信息，无操作按钮；多选态一并移除。
 *
 * 入口位于 `SettingsPage` 的配置卡"标签管理"与"界面与导航"之间。
 */
@Composable
internal fun OperationHistoryPage(onBack: () -> Unit) {
    val viewModel: OperationHistoryViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    var selectedEntity by remember { mutableStateOf<OperationHistoryWithContact?>(null) }

    // BackHandler:详情 dialog 打开时拦截,关闭 dialog 而非退出页面
    val isInDetailMode by remember {
        derivedStateOf { selectedEntity != null }
    }
    BackHandler(enabled = isInDetailMode) {
        BadgerLog.d(TAG, "BackHandler: close detail dialog")
        selectedEntity = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "历史操作",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "返回",
                        )
                    }
                },
            )
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
                        onClickRecord = { item ->
                            selectedEntity = item
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

    // 详情 dialog(只读,两条关闭路径:dismissRequest / 关闭按钮 → 都重置 selectedEntity)
    selectedEntity?.let { entity ->
        OperationHistoryDetailDialog(
            entity = entity,
            onDismiss = { selectedEntity = null },
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
    onClickRecord: (OperationHistoryWithContact) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(records, key = { it.history.opId }) { item ->
            OperationHistoryRow(
                item = item,
                onClick = { onClickRecord(item) },
            )
        }
    }
}

@Composable
private fun OperationHistoryRow(
    item: OperationHistoryWithContact,
    onClick: () -> Unit,
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
            val lastError = op.lastError
            if (lastError != null && lastError.isNotBlank()) {
                Text(
                    text = "错误:${lastError.take(60)}${if (lastError.length > 60) "..." else ""}",
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
 * 状态徽章颜色:
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
                text = "对联系人的修改会以只读日志形式出现在这里。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/**
 * 详情 dialog（只读）：显示 opLabel / 联系人 / 时间 / status / payload / lastError，
 * 无撤销 / 重发 / 冲突解决按钮（Phase 3 队列退役）。
 */
@Composable
private fun OperationHistoryDetailDialog(
    entity: OperationHistoryWithContact,
    onDismiss: () -> Unit,
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
            val detailLastError = op.lastError
            if (detailLastError != null && detailLastError.isNotBlank()) {
                DetailField(label = "错误", value = detailLastError, isError = true)
            }
            val inversePayload = op.inversePayloadJson
            if (inversePayload != null && inversePayload.isNotBlank()) {
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
                        text = inversePayload,
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = cs.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(
                text = "关闭",
                onClick = onDismiss,
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
