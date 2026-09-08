package top.mcxiafeng.badger.pages.settings.devices

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.network.UserDevice
import top.mcxiafeng.badger.pages.settings.components.BadgerSwipeRow
import top.mcxiafeng.badger.pages.settings.components.NotLoggedInState
import top.mcxiafeng.badger.pages.settings.components.SETTINGS_SNACKBAR_DURATION_MS
import top.mcxiafeng.badger.pages.settings.components.SettingsSubPageScaffold
import top.mcxiafeng.badger.ui.components.BadgerConfirmDialog
import top.mcxiafeng.badger.ui.components.BadgerEmptyState
import top.mcxiafeng.badger.ui.components.BadgerInputDialog
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MonitorSmartphone
import androidx.compose.foundation.background

private const val TAG = "DeviceListPage"

/**
 * 已登录设备列表页（重写）。
 *
 * - 当前设备高亮 + 不可注销（UI 禁用滑动）
 * - 左滑注销其它设备（[BadgerSwipeRow] + [BadgerConfirmDialog]）
 * - 点击设备行弹重命名（[BadgerInputDialog]）
 * - 下拉刷新
 */
@Composable
internal fun DeviceListPage(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
) {
    val viewModel: DeviceViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            BadgerLog.d(TAG, "DeviceListPage: first load")
            viewModel.refresh()
        }
    }

    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Custom(SETTINGS_SNACKBAR_DURATION_MS),
        )
        viewModel.clearError()
    }

    SettingsSubPageScaffold(
        title = SettingsPage.Devices.title,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                !uiState.isLoggedIn -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        NotLoggedInState(
                            onLogin = onNavigateToLogin,
                            title = "还没有设备",
                            subtitle = "登录账号后同步显示已登录设备",
                        )
                    }
                }
                else -> {
                    val pullState = rememberPullToRefreshState()
                    PullToRefresh(
                        isRefreshing = uiState.loading,
                        onRefresh = {
                            BadgerLog.d(TAG, "DeviceListPage: pull-to-refresh")
                            viewModel.refresh()
                        },
                        pullToRefreshState = pullState,
                        contentPadding = PaddingValues(top = 8.dp),
                    ) {
                        if (uiState.devices.isEmpty() && !uiState.loading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                BadgerEmptyState(
                                    icon = Lucide.MonitorSmartphone,
                                    title = "还没有设备",
                                    subtitle = "登录后会显示已登录设备，也可下拉刷新",
                                    actionLabel = "刷新",
                                    onAction = { viewModel.refresh() },
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(uiState.devices, key = { it.uuid }) { device ->
                                    val isCurrentDevice = device.deviceId == viewModel.currentDeviceId
                                    BadgerSwipeRow(
                                        enabled = !isCurrentDevice,
                                        deleteText = "注销",
                                        onDelete = {
                                            BadgerLog.d(TAG, "Delete device uuid=${device.uuid.take(8)}")
                                            showDeleteConfirm = device.uuid to (device.deviceName.ifBlank { "未知设备" })
                                        },
                                        content = {
                                            DeviceRow(
                                                device = device,
                                                isCurrentDevice = isCurrentDevice,
                                                onClick = {
                                                    BadgerLog.d(TAG, "Rename device uuid=${device.uuid.take(8)}")
                                                    showRenameDialog = device.uuid to device.deviceName
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== 重命名对话框 =====
    showRenameDialog?.let { (uuid, currentName) ->
        var name by remember(currentName) { mutableStateOf(currentName) }
        BadgerInputDialog(
            show = true,
            title = "重命名设备",
            value = name,
            onValueChange = { name = it },
            label = "设备名称",
            confirmText = "保存",
            onConfirm = { newName ->
                val trimmed = newName.trim()
                if (trimmed.isNotBlank()) viewModel.renameDevice(uuid, trimmed)
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null },
        )
    }

    // ===== 注销确认 =====
    showDeleteConfirm?.let { (uuid, deviceName) ->
        BadgerConfirmDialog(
            show = true,
            title = "注销设备",
            message = "确定要注销「$deviceName」吗？该设备将被踢下线。",
            confirmText = "注销",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteDevice(uuid)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null },
        )
    }
}

/** 设备行：图标 + 名称 + 在线状态 + IP/登录时间。 */
@Composable
private fun DeviceRow(
    device: UserDevice,
    isCurrentDevice: Boolean,
    onClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val infoParts = buildList {
        device.ip?.takeIf { it.isNotBlank() }?.let { add(it) }
        formatDeviceLoginTime(device.loginTime)?.let { add(it) }
        if (device.online) add("在线")
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = device.deviceName.ifBlank { "未知设备" },
            titleColor = if (isCurrentDevice) {
                BasicComponentDefaults.titleColor(color = cs.primary)
            } else {
                BasicComponentDefaults.titleColor()
            },
            summary = infoParts.joinToString(" · ").ifBlank { "—" },
            startAction = {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Icon(
                        imageVector = Lucide.MonitorSmartphone,
                        contentDescription = null,
                        tint = if (isCurrentDevice) cs.primary else cs.onSurfaceVariantSummary,
                        modifier = Modifier.size(28.dp),
                    )
                    if (device.online) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(cs.primary, CircleShape),
                        )
                    }
                }
            },
            endActions = {
                if (isCurrentDevice) {
                    Text(
                        text = "当前设备",
                        style = MiuixTheme.textStyles.footnote2,
                        color = cs.primary,
                    )
                }
            },
            onClick = onClick,
        )
    }
}

/** ISO 字符串或 epoch millis → `yyyy-MM-dd HH:mm`；解析失败返回 null。 */
private fun formatDeviceLoginTime(raw: String?): String? = Methods.formatDateTime(raw)
