package top.mcxiafeng.badger.pages.settings.devices

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.BadgerEmptyState
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.MonitorSmartphone
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "DeviceListPage"

/**
 * [B4] 已登录设备列表页。
 *
 * - 当前设备高亮 + 不可注销（403 会报错，UI 层直接禁用按钮）
 * - 左滑注销其它设备（确认弹窗 → DELETE API）
 * - 点击设备行弹重命名对话框
 * - 下拉刷新
 */
@Composable
internal fun DeviceListPage(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
) {
    val viewModel: DeviceViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // uuid → currentName
    var showDeleteConfirm by remember { mutableStateOf<Pair<String, String>?>(null) } // uuid → deviceName

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
            duration = SnackbarDuration.Custom(1800),
        )
        viewModel.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "已登录设备",
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
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                !uiState.isLoggedIn -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = floatingBarBottomPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        BadgerEmptyState(
                            icon = Lucide.MonitorSmartphone,
                            title = "还没有设备",
                            subtitle = "登录账号后同步显示已登录设备。",
                            actionLabel = "去登录",
                            onAction = onNavigateToLogin,
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = floatingBarBottomPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                BadgerEmptyState(
                                    icon = Lucide.MonitorSmartphone,
                                    title = "还没有设备",
                                    subtitle = "登录后会显示已登录设备，也可下拉刷新。",
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
                                    bottom = 8.dp + floatingBarBottomPadding,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(uiState.devices, key = { it.uuid }) { device ->
                                    val isCurrentDevice = device.deviceId == viewModel.currentDeviceId
                                    DeviceSwipeRow(
                                        device = device,
                                        isCurrentDevice = isCurrentDevice,
                                        onRename = {
                                            BadgerLog.d(TAG, "Rename device uuid=${device.uuid.take(8)}")
                                            showRenameDialog = device.uuid to device.deviceName
                                        },
                                        onDelete = {
                                            BadgerLog.d(TAG, "Delete device uuid=${device.uuid.take(8)}")
                                            showDeleteConfirm = device.uuid to (device.deviceName.ifBlank { "未知设备" })
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
        RenameDeviceDialog(
            currentName = currentName,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newName ->
                viewModel.renameDevice(uuid, newName)
                showRenameDialog = null
            },
        )
    }

    // ===== 注销确认对话框 =====
    showDeleteConfirm?.let { (uuid, deviceName) ->
        LogoutDeviceConfirmDialog(
            deviceName = deviceName,
            onDismiss = { showDeleteConfirm = null },
            onConfirm = {
                viewModel.deleteDevice(uuid)
                showDeleteConfirm = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSwipeRow(
    device: UserDevice,
    isCurrentDevice: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    key(device.uuid) {
        if (isCurrentDevice) {
            // 当前设备：不可左滑，点击重命名
            DeviceRow(
                device = device,
                isCurrentDevice = true,
                onClick = onRename,
            )
        } else {
            // 其它设备：左滑注销，点击重命名
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        onDelete()
                    }
                    false
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 2.dp)
                            .background(
                                color = MiuixTheme.colorScheme.error,
                                shape = top.mcxiafeng.badger.utils.miuixShape(12.dp),
                            )
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = "注销",
                            color = MiuixTheme.colorScheme.onError,
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
            ) {
                DeviceRow(
                    device = device,
                    isCurrentDevice = false,
                    onClick = onRename,
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: UserDevice,
    isCurrentDevice: Boolean,
    onClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 设备图标 + 在线状态指示
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
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = device.deviceName.ifBlank { "未知设备" },
                        style = MiuixTheme.textStyles.body1,
                        color = cs.onSurface,
                        fontWeight = if (isCurrentDevice) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isCurrentDevice) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "当前设备",
                            style = MiuixTheme.textStyles.footnote2,
                            color = cs.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val infoParts = buildList {
                        device.ip?.takeIf { it.isNotBlank() }?.let { add(it) }
                        formatDeviceLoginTime(device.loginTime)?.let { add(it) }
                    }
                    Text(
                        text = infoParts.joinToString(" · ").ifBlank { "—" },
                        style = MiuixTheme.textStyles.footnote1,
                        color = cs.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (device.online) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "在线",
                            style = MiuixTheme.textStyles.footnote2,
                            color = cs.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameDeviceDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    WindowDialog(
        show = true,
        title = "重命名设备",
        summary = "",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "设备名称",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            DialogButtonRow(
                positiveText = "保存",
                onNegative = onDismiss,
                onPositive = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) onConfirm(trimmed)
                },
            )
        }
    }
}

@Composable
private fun LogoutDeviceConfirmDialog(
    deviceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = "注销设备",
        summary = "确定要注销「$deviceName」吗？该设备将被踢下线。",
        onDismissRequest = onDismiss,
    ) {
        DialogButtonRow(
            positiveText = "注销",
            onNegative = onDismiss,
            onPositive = onConfirm,
        )
    }
}

/** ISO 字符串或 epoch millis → `yyyy-MM-dd HH:mm`；解析失败返回 null。 */
private fun formatDeviceLoginTime(raw: String?): String? {
    return Methods.formatDateTime(raw)
}
