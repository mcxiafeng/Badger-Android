package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.network.ServerApi
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "CloudBackupPage"

/**
 * [§16] 云端备份独立页。
 *
 * 顶部 TopAppBar + 3 个 Card:
 * 1. **提示卡** —— envelope 上限 4 MiB (服务端 spec §0.2 / §14)
 * 2. **备份列表** —— 每行 `id / name / size / createdAt` + 删除按钮(二次确认)
 * 3. **操作卡** —— "上传 / 立即下载最新 / 刷新"
 *
 * 删除二次确认用 WindowDialog,按 `feedback_dialog_rules.md` 三路重置规则:
 * `onDismissRequest` / 取消按钮 / 确认按钮 三条都置 `showConfirmDelete = false`。
 *
 * BackHandler: 单返回键关闭页面(由上游 Route.SettingsSubPage 背压,这里不拦截)。
 */
@Composable
internal fun CloudBackupPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: CloudBackupViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 首屏拉一次
    LaunchedEffect(Unit) {
        Log.d(TAG, "CloudBackupPage: first load")
        viewModel.refresh()
    }

    // error → snackbar 显示,然后清掉 transient error,避免重复弹
    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Custom(1800),
        )
        viewModel.clearError()
    }

    // 删除二次确认 dialog 的 flag — 三路重置
    var showConfirmDelete by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteName by remember { mutableStateOf("") }

    BackHandler(enabled = showConfirmDelete) {
        showConfirmDelete = false
        pendingDeleteId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "云端备份",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.loading && uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 8.dp + floatingBarBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "tip_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(16.dp),
                        ) {
                            Text(
                                text = "备份包上限 ${
                                    formatBytes(CloudSyncSettingsViewModel.MAX_BACKUP_BYTES)
                                },超出时会在上传前拒绝。",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                lineHeight = 1.5.em,
                            )
                        }
                    }

                    item(key = "actions_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp),
                        ) {
                            ArrowPreference(
                                title = "刷新列表",
                                summary = "重新拉取服务端 backup 列表",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                },
                                onClick = { viewModel.refresh() },
                            )
                        }
                    }

                    if (uiState.items.isEmpty()) {
                        item(key = "empty_state") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(16.dp),
                            ) {
                                Text(
                                    text = "暂无云端备份",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    } else {
                        item(key = "list_header") {
                            Text(
                                text = "备份列表(${uiState.items.size})",
                                style = MiuixTheme.textStyles.subtitle,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        items(
                            items = uiState.items,
                            key = { it.id },
                        ) { item ->
                            BackupRow(
                                item = item,
                                isDeleting = uiState.deletingId == item.id,
                                onRequestDelete = {
                                    pendingDeleteId = item.id
                                    pendingDeleteName = item.name
                                    showConfirmDelete = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // 删除二次确认 dialog —— 三路重置
    if (showConfirmDelete) {
        WindowDialog(
            show = true,
            title = "删除云端备份?",
            onDismissRequest = {
                showConfirmDelete = false
                pendingDeleteId = null
            },
        ) {
            Text(
                text = "将永久删除服务端备份「$pendingDeleteName」。此操作不可撤销。",
                style = MiuixTheme.textStyles.body2,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        showConfirmDelete = false
                        pendingDeleteId = null
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(20.dp))
                TextButton(
                    text = "删除",
                    onClick = {
                        val id = pendingDeleteId
                        showConfirmDelete = false
                        pendingDeleteId = null
                        if (id != null) viewModel.delete(id)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 单条 backup 行 —— name / size / createdAt + 右侧删除按钮。
 */
@Composable
private fun BackupRow(
    item: ServerApi.BackupSummary,
    isDeleting: Boolean,
    onRequestDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        BasicComponent(
            title = item.name,
            summary = "${formatBytes(item.size)} · ${formatCreatedAt(item.createdAt)}",
            endActions = {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onRequestDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MiuixTheme.colorScheme.error,
                        )
                    }
                }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.2f MB".format(bytes / (1024.0 * 1024.0))
}

/**
 * 服务端 ISO-8601 字符串 → 用户可读 `yyyy-MM-dd HH:mm`。
 * 容错:解析失败 → 原样返回。
 */
private fun formatCreatedAt(raw: String): String {
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val trimmed = raw.substringBefore('.').substringBefore('+').substringBefore('Z')
        val date: Date = parser.parse(trimmed) ?: return raw
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
    }.getOrDefault(raw)
}

@Suppress("unused")
private val uploadIconHint = Icons.Default.CloudUpload