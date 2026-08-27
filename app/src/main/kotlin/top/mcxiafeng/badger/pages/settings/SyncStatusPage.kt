package top.mcxiafeng.badger.pages.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import top.mcxiafeng.badger.data.repository.SyncStatusSnapshot
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
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
 * [Phase 4 Task #21] 同步状态页。
 *
 * 退役队列语义后，三段 Card 简化为：
 * 1. **状态卡**: 同步健康状态（已同步 / 有 N 个未同步联系人）+ 游标版本号。
 * 2. **操作卡**: "立即同步"按钮（触发增量同步）。
 * 3. **电池优化卡**: 显示是否加入白名单;点击跳系统设置。
 */
@Composable
internal fun SyncStatusPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: SyncStatusViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(
                message = msg.text,
                duration = SnackbarDuration.Custom(1800),
            )
        }
    }

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
                            onRetryAll = { viewModel.onEvent(SyncStatusEvent.RetryAll) },
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
 * 状态卡:同步健康状态 + 游标版本号。
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
                    imageVector = if (snapshot.hasAttention) Icons.Default.Warning else Icons.Default.CheckCircle,
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
            // 详情行
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

/**
 * 操作卡:立即同步（触发增量同步）。
 */
@Composable
private fun SyncStatusActionCard(
    onRetryAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        BasicComponent(
            title = "立即同步",
            summary = "触发一次服务端增量同步",
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
    }
}

/**
 * 电池优化卡:显示当前白名单状态 + 跳系统设置。
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

/**
 * 跳系统电池优化白名单设置。
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
        Log.w(TAG, "requestIgnoreBatteryOptimizations: 跳转失败(OEM 可能锁了入口)", e)
    }
}
