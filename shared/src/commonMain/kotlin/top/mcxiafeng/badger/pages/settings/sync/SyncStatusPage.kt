package top.mcxiafeng.badger.pages.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.data.repository.SyncStatusSnapshot
import top.mcxiafeng.badger.pages.settings.components.SettingsMessageEffect
import top.mcxiafeng.badger.pages.settings.components.SettingsSubPageScaffold
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.platform.BatteryOptimization
import top.mcxiafeng.badger.platform.PlatformInfo
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.BatteryFull
import com.composables.icons.lucide.BatteryWarning
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.TriangleAlert

private const val TAG = "SyncStatusPage"

/**
 * 同步状态页（重写：共享脚手架 + SettingsMessageEffect + 去 pendingRefresh hack）。
 *
 * 三段 Card：状态卡（同步健康 + 游标版本） / 操作卡（立即同步） / 电池优化卡。
 */
@Composable
internal fun SyncStatusPage(onBack: () -> Unit) {
    val viewModel: SyncStatusViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SettingsMessageEffect(snackbarHostState, viewModel.messages)

    SettingsSubPageScaffold(
        title = SettingsPage.SyncStatus.title,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        when (val currentState = uiState) {
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
                            text = "加载失败：${currentState.message}",
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
                        start = BadgerSpacing.md,
                        end = BadgerSpacing.md,
                        top = BadgerSpacing.sm,
                        bottom = BadgerSpacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
                ) {
                    item(key = "status_card") { SyncStatusCard(currentState.snapshot) }
                    item(key = "action_card") {
                        SyncStatusActionCard(
                            onRetryAll = { viewModel.onEvent(SyncStatusEvent.RetryAll) },
                        )
                    }
                    item(key = "battery_card") {
                        SyncStatusBatteryCard(
                            batteryOptimized = currentState.batteryOptimized,
                            onRequestBatteryOptimization = {
                                // 跳系统电池优化设置；返回后直接刷新一次（不再用 pendingRefresh hack）
                                BadgerLog.d(TAG, "电池优化：跳转系统设置")
                                BatteryOptimization.openRequestSettings()
                                viewModel.onEvent(SyncStatusEvent.Refresh)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 状态卡：同步健康状态 + 游标版本号。 */
@Composable
private fun SyncStatusCard(snapshot: SyncStatusSnapshot) {
    val cs = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (snapshot.hasAttention) Lucide.TriangleAlert else Lucide.CircleCheck,
                    contentDescription = null,
                    tint = if (snapshot.hasAttention) cs.error else cs.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (snapshot.hasAttention) "有 ${snapshot.unsyncedCount} 个联系人未同步" else "同步正常",
                    style = MiuixTheme.textStyles.subtitle,
                    color = if (snapshot.hasAttention) cs.error else cs.primary,
                )
            }
            Spacer(Modifier.size(8.dp))
            SyncStatusDetailRow(
                label = "同步游标版本",
                value = if (snapshot.lastSyncVersion > 0) "v${snapshot.lastSyncVersion}" else "尚未同步",
            )
            Spacer(Modifier.size(4.dp))
            SyncStatusDetailRow(
                label = "未同步联系人",
                value = "${snapshot.unsyncedCount} 个",
            )
        }
    }
}

@Composable
private fun SyncStatusDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 操作卡：立即同步（触发增量同步）。 */
@Composable
private fun SyncStatusActionCard(onRetryAll: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        BasicComponent(
            title = "立即同步",
            summary = "触发一次服务端增量同步",
            startAction = {
                Icon(
                    imageVector = Lucide.RefreshCw,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            onClick = onRetryAll,
        )
    }
}

/** 电池优化卡：显示白名单状态 + 跳系统设置。 */
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
            summary = when {
                PlatformInfo.apiLevel < 23 -> "当前系统版本无需配置"
                batteryOptimized -> "已加入白名单，后台可被调度"
                else -> "未加入白名单，部分设备可能停用后台同步"
            },
            startAction = {
                Icon(
                    imageVector = if (batteryOptimized) Lucide.BatteryFull else Lucide.BatteryWarning,
                    contentDescription = null,
                    tint = if (batteryOptimized) cs.primary else cs.error,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            onClick = {
                if (!batteryOptimized) {
                    BadgerLog.d(TAG, "电池优化：需跳转系统设置")
                    onRequestBatteryOptimization()
                } else {
                    BadgerLog.d(TAG, "电池优化：无需跳转（API<23 或已加入白名单）")
                }
            },
        )
        if (!batteryOptimized) {
            BasicComponent(
                title = "为什么需要电池优化白名单？",
                summary = "Android 6.0+ 默认开启省电模式，未加入白名单的 App 后台可能被杀，导致同步延迟",
                startAction = {
                    Icon(
                        imageVector = Lucide.Sparkles,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        }
    }
}
