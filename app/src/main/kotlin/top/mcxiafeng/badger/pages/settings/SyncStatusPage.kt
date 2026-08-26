package top.mcxiafeng.badger.pages.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.data.repository.SyncStatusSnapshot
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "SyncStatusPage"

/**
 * [V2-P9] 同步状态页(对应 `docs/BADGER_V2_CLIENT_PLAN.md` §4.3 抗 OEM 兜底)。
 *
 * 入口位于 `SettingsPage` 合并设置卡顶部(早于"标签管理"以提示抗 OEM 价值),
 * 内部三段 Card:
 * 1. **状态卡**: 6 种状态徽章 + 数字 3×2 网格(无数字显示 0)。
 * 2. **操作卡**: "立即重试 / 清理历史"两个按钮(立即重试 = kick Worker;清理历史 = purgeDone)。
 * 3. **电池优化卡**: 显示是否加入白名单;点击跳系统设置,返回后 LaunchedEffect 自动重读状态。
 *
 * Dialog flag 规范: 本页面无 WindowDialog,所有弹窗走 SnackbarHost,无需重置 flag。
 * BackHandler: 单返回键关闭页面(由 Route.SettingsSubPage 上游背压,这里不拦截)。
 */
@Composable
internal fun SyncStatusPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: SyncStatusViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    // Snackbar 桥接:VM 推 message → Composable 消费
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(
                message = msg.text,
                duration = SnackbarDuration.Custom(1800),
            )
        }
    }

    // [修复防御]: 从电池优化设置返回后 LaunchedEffect 触发 Refresh,
    // 让 VM 重新读 isIgnoringBatteryOptimizations 刷新"已加入白名单"状态。
    // ProcessLifecycleOwner ON_START 也可复用,但与 Settings Page 内其他 LaunchedEffect
    // 一致性,这里用 LaunchedEffect(Unit)。
    var pendingRefresh by remember { mutableStateOf(false) }
    if (pendingRefresh) {
        LaunchedEffect(Unit) {
            viewModel.onEvent(SyncStatusEvent.Refresh)
            pendingRefresh = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "同步状态",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        val currentState = uiState
        when (currentState) {
            is SyncStatusUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is SyncStatusUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                            onClick = { viewModel.onEvent(SyncStatusEvent.Refresh) },
                        )
                    }
                }
            }

            is SyncStatusUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 8.dp + floatingBarBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "status_card") { SyncStatusCard(currentState.snapshot) }
                    item(key = "action_card") {
                        SyncStatusActionCard(
                            snapshot = currentState.snapshot,
                            onRetryAll = { viewModel.onEvent(SyncStatusEvent.RetryAll) },
                            onPurge = { viewModel.onEvent(SyncStatusEvent.PurgeFinished) },
                        )
                    }
                    item(key = "battery_card") {
                        SyncStatusBatteryCard(
                            batteryOptimized = currentState.batteryOptimized,
                            onRequestBatteryOptimization = {
                                requestIgnoreBatteryOptimizations(
                                    context = context,
                                    onLaunched = { pendingRefresh = true },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 状态卡:6 种状态 3×2 网格 + 顶部全局徽章(同步正常 / 有 N 项需要关注)。
 */
@Composable
private fun SyncStatusCard(snapshot: SyncStatusSnapshot) {
    val cs = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column {
            // 头部全局徽章
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (snapshot.hasAttention) Icons.Default.BatteryAlert else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (snapshot.hasAttention) cs.error else cs.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (snapshot.hasAttention) "有项需要关注" else "同步正常",
                    style = MiuixTheme.textStyles.subtitle,
                    color = if (snapshot.hasAttention) cs.error else cs.primary,
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = "总计 ${snapshot.totalCount} 条 · 等待 ${snapshot.pendingCount} · 发送中 ${snapshot.inFlightCount}",
                style = MiuixTheme.textStyles.footnote1,
                color = cs.onSurfaceVariantSummary,
            )
            Spacer(Modifier.size(16.dp))
            // 3×2 网格
            StatusGridRow(
                items = listOf(
                    StatusItem("等待中", snapshot.pendingCount, StatusTone.NEUTRAL),
                    StatusItem("发送中", snapshot.inFlightCount, StatusTone.NEUTRAL),
                    StatusItem("已成功", snapshot.doneCount, StatusTone.POSITIVE),
                ),
            )
            Spacer(Modifier.size(8.dp))
            StatusGridRow(
                items = listOf(
                    StatusItem(
                        "失败",
                        snapshot.failedCount,
                        if (snapshot.failedCount > 0) StatusTone.WARN else StatusTone.NEUTRAL,
                    ),
                    StatusItem(
                        "冲突",
                        snapshot.conflictCount,
                        if (snapshot.conflictCount > 0) StatusTone.WARN else StatusTone.NEUTRAL,
                    ),
                    StatusItem(
                        "永久失败",
                        snapshot.failedPermanentCount,
                        if (snapshot.failedPermanentCount > 0) StatusTone.NEGATIVE else StatusTone.NEUTRAL,
                    ),
                ),
            )
        }
    }
}

/**
 * 操作卡:立即重试(kick Worker)+ 清理历史(purgeDone)。
 */
@Composable
private fun SyncStatusActionCard(
    snapshot: SyncStatusSnapshot,
    onRetryAll: () -> Unit,
    onPurge: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        BasicComponent(
            title = "立即同步",
            summary = "触发一次服务端增量同步(${snapshot.pendingCount + snapshot.failedCount} 条历史记录仅展示)",
            startAction = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            onClick = onRetryAll,
        )
        BasicComponent(
            title = "清理历史",
            summary = "删除 ${SyncStatusRepository.DEFAULT_PURGE_DAYS} 天前已成功的同步记录(当前 ${snapshot.doneCount} 条)",
            startAction = {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            onClick = onPurge,
        )
    }
}

/**
 * 电池优化卡:显示当前白名单状态 + 跳系统设置。
 *
 * [修复防御]:
 * - API<23 永远返 true(没 doze 概念),UI 隐藏"申请加入"行
 * - 跳转 Intent 失败(某些 OEM 锁系统设置入口)被 runCatching 兜底 + warn
 */
@Composable
private fun SyncStatusBatteryCard(
    batteryOptimized: Boolean,
    onRequestBatteryOptimization: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        BasicComponent(
            title = "电池优化",
            summary = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                "当前系统版本无需配置"
            } else if (batteryOptimized) {
                "已加入白名单,后台可被调度"
            } else {
                "未加入白名单,部分设备可能停用后台同步"
            },
            startAction = {
                Icon(
                    imageVector = if (batteryOptimized) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = if (batteryOptimized) cs.primary else cs.error,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !batteryOptimized) {
                    Log.d(TAG, "电池优化: 点击跳转系统设置")
                    onRequestBatteryOptimization()
                } else {
                    Log.d(TAG, "电池优化: 无需跳转(API<23 或已加入白名单)")
                }
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !batteryOptimized) {
            BasicComponent(
                title = "为什么需要电池优化白名单?",
                summary = "Android 6.0+ 默认开启省电模式,未加入白名单的 App 后台可能被杀,导致同步延迟",
                startAction = {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        }
    }
}

// ============ 内部 helper:3 列状态徽章网格 ============

private enum class StatusTone { POSITIVE, NEUTRAL, WARN, NEGATIVE }

private data class StatusItem(
    val label: String,
    val count: Int,
    val tone: StatusTone,
)

@Composable
private fun StatusGridRow(items: List<StatusItem>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            Box(modifier = Modifier.weight(1f)) {
                StatusBadge(item = item)
            }
        }
    }
}

@Composable
private fun StatusBadge(item: StatusItem) {
    val cs = MiuixTheme.colorScheme
    val (bg, fg) = when (item.tone) {
        StatusTone.POSITIVE -> cs.primary to Color.White
        StatusTone.NEUTRAL -> cs.surfaceVariant to cs.onSurfaceVariantSummary
        StatusTone.WARN -> cs.error.copy(alpha = 0.15f) to cs.error
        StatusTone.NEGATIVE -> cs.error to Color.White
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = item.count.toString(),
            style = MiuixTheme.textStyles.title3,
            color = fg,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = item.label,
            style = MiuixTheme.textStyles.footnote2,
            color = fg,
        )
    }
}

/**
 * 跳系统电池优化白名单设置。
 *
 * [修复防御]: Intent 失败(部分 OEM 锁系统设置入口)被 catch + warn,
 * 不会让 UI 崩。`onLaunched` 回调让调用方标记"从设置返回后需要 Refresh"。
 */
private fun requestIgnoreBatteryOptimizations(
    context: android.content.Context,
    onLaunched: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        Log.w(TAG, "requestIgnoreBatteryOptimizations: API<23 跳不过去,跳过")
        return
    }
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Log.d(TAG, "requestIgnoreBatteryOptimizations: 已发起 Intent,等用户从系统返回")
        onLaunched()
    } catch (e: Exception) {
        // [修复防御]: 部分 OEM(华为 EMUI / OPPO ColorOS)可能禁用该 Intent,这里只 warn 不崩
        Log.w(TAG, "requestIgnoreBatteryOptimizations: 跳转失败(OEM 可能锁了入口)", e)
    }
}
