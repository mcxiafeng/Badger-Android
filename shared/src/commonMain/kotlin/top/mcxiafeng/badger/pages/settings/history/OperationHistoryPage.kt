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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact
import top.mcxiafeng.badger.pages.settings.components.SettingsSubPageScaffold
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.ui.components.BadgerDialog
import top.mcxiafeng.badger.ui.components.BadgerEmptyStateSimple
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.BackHandler

private const val TAG = "OpHistoryPage"

/**
 * 操作历史页（重写：共享脚手架 + 只读日志视图）。
 *
 * 队列退役后为只读本地日志：列表只读展示历史写操作，详情 dialog 仅显示信息。
 * 入口位于设置主页「数据与同步」分组。
 */
@Composable
internal fun OperationHistoryPage(onBack: () -> Unit) {
    val viewModel: OperationHistoryViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedEntity by remember { mutableStateOf<OperationHistoryWithContact?>(null) }

    // BackHandler：详情 dialog 打开时拦截，关闭 dialog 而非退出页面
    val isInDetailMode by remember { derivedStateOf { selectedEntity != null } }
    BackHandler(enabled = isInDetailMode) {
        BadgerLog.d(TAG, "BackHandler: close detail dialog")
        selectedEntity = null
    }

    SettingsSubPageScaffold(
        title = SettingsPage.OperationHistory.title,
        onBack = onBack,
    ) { innerPadding ->
        val scope = rememberCoroutineScope()
        // 双向绑定 HorizontalPager（与 TagManager 同款）：滑动切 filter，点 Tab 滑动对齐
        val pagerState = rememberPagerState(
            initialPage = HistoryFilter.entries.indexOf(viewModel.currentFilter()).coerceAtLeast(0),
        ) { HistoryFilter.entries.size }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                viewModel.onEvent(OperationHistoryEvent.ChangeFilter(HistoryFilter.entries[page]))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OperationHistoryFilterTab(
                selectedTabIndex = pagerState.currentPage,
                onTabSelected = { idx ->
                    viewModel.onEvent(OperationHistoryEvent.ChangeFilter(HistoryFilter.entries[idx]))
                    scope.launch { pagerState.animateScrollToPage(idx) }
                },
            )

            // pager 包住所有状态：空态/加载态也能左右滑动切 filter（与 TagManager 一致）
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) {
                when (val currentState = uiState) {
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

                    is OperationHistoryUiState.Empty -> BadgerEmptyStateSimple(
                        icon = Lucide.History,
                        title = if (currentState.filter == HistoryFilter.Pending) "没有需要处理的操作" else "还没有任何操作记录",
                        subtitle = "对联系人的修改会以只读日志形式出现在这里",
                        modifier = Modifier.fillMaxSize(),
                    )

                    is OperationHistoryUiState.Success -> {
                        OperationHistoryList(
                            records = currentState.records,
                            onClickRecord = { item -> selectedEntity = item },
                        )
                    }

                    is OperationHistoryUiState.Error -> {
                        // 只读本地订阅，错误来自 DB 查询，无 retry 必要
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "加载失败：${currentState.message}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    // 详情 dialog（只读）
    selectedEntity?.let { entity ->
        OperationHistoryDetailDialog(
            entity = entity,
            onDismiss = { selectedEntity = null },
        )
    }
}

/** 顶部 filter tab（全部 / 待处理），selectedTabIndex 由 pagerState 驱动以跟随滑动。 */
@Composable
private fun OperationHistoryFilterTab(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    TabRowWithContour(
        tabs = HistoryFilter.entries.map { OperationHistoryOpFormatter.formatFilterLabel(it) },
        selectedTabIndex = selectedTabIndex.coerceIn(0, HistoryFilter.entries.lastIndex),
        onTabSelected = onTabSelected,
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = cs.surface,
            contentColor = cs.onSurfaceVariantSummary,
            selectedBackgroundColor = cs.surface,
            selectedContentColor = cs.primary,
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

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
            OperationHistoryRow(item = item, onClick = { onClickRecord(item) })
        }
    }
}

@Composable
private fun OperationHistoryRow(
    item: OperationHistoryWithContact,
    onClick: () -> Unit,
) {
    val op = item.history
    val lastError = op.lastError
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = OperationHistoryOpFormatter.formatContactName(item.contactName),
            summary = OperationHistoryOpFormatter.formatListSubtitle(item),
            endActions = { StatusBadge(opStatus = op.opStatus) },
            bottomAction = if (lastError != null && lastError.isNotBlank()) {
                {
                    Text(
                        text = "错误：${lastError.take(60)}${if (lastError.length > 60) "..." else ""}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else null,
            onClick = onClick,
        )
    }
}

/** 状态徽章（带颜色背景的小方块 + 中文 label）。 */
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
 * 详情 dialog（只读）：opLabel / 联系人 / 时间 / status / payload / lastError，无操作按钮。
 */
@Composable
private fun OperationHistoryDetailDialog(
    entity: OperationHistoryWithContact,
    onDismiss: () -> Unit,
) {
    val op = entity.history
    val cs = MiuixTheme.colorScheme
    BadgerDialog(
        show = true,
        title = OperationHistoryOpFormatter.formatContactName(entity.contactName),
        summary = OperationHistoryOpFormatter.formatDetailSummary(entity),
        onDismissRequest = onDismiss,
        negativeText = null,
        positiveText = "关闭",
        onPositive = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            DetailField(label = "状态", value = OperationHistoryOpFormatter.formatStatusLabel(op.opStatus))
            DetailField(label = "时间", value = OperationHistoryOpFormatter.formatTimestamp(op.createdAt))
            DetailField(label = "操作", value = OperationTypes.labelOf(op.opType))
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
