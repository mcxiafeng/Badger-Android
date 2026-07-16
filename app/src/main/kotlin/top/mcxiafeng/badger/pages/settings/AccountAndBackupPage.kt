package top.mcxiafeng.badger.pages.settings

import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.data.CloudSyncConfig
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout
import java.text.SimpleDateFormat

private const val TAG = "AccountAndBackup"

/**
 * 账号与备份合并页:合并了原 AccountSettingsPage 的账号信息与
 * 原 CloudSyncSettingsPage 的云端备份功能,通过两个 Hilt VM 协作。
 */
@Composable
internal fun AccountAndBackupPage(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    val accountViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountState by accountViewModel.state.collectAsState()
    val cloudSyncViewModel: CloudSyncSettingsViewModel = hiltViewModel()

    // 账号相关本地状态
    var showEditServerUrl by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }

    // 备份相关本地状态
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var backupResult by remember { mutableStateOf<String?>(null) }
    var restoreResult by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showNotConfiguredDialog by remember { mutableStateOf(false) }
    val notConfiguredDialogVisible = remember { mutableStateOf(false) }
    val restoreConfirmDialogVisible = remember { mutableStateOf(false) }
    var syncEnabled by remember { mutableStateOf(CloudSyncConfig.isSyncEnabled(context)) }

    // [修复防御]: 把旧版本 CloudSyncConfig.server_url 的值一次性迁到 AuthPrefs,
    // 避免「用户登录后在客户端改过备份服务器、但 AuthPrefs 还是默认 10.0.2.2」
    // 的悄默丢配置场景。完成后立刻清掉旧字段,下次启动只看到 AuthPrefs。
    LaunchedEffect(Unit) {
        val legacy = CloudSyncConfig.readLegacyServerUrl(context)
        if (legacy.isNotBlank()) {
            val currentAuth = AuthPrefs.readServerUrl(context)
            val isDefault = currentAuth.isBlank() ||
                currentAuth == "http://10.0.2.2:8080"
            if (isDefault) {
                Log.d(TAG, "Migrate legacy cloud-sync server url → AuthPrefs: $legacy")
                AuthPrefs.writeServerUrl(context, legacy.trim().trimEnd('/'))
            }
            CloudSyncConfig.clearLegacyServerUrl(context)
        }
    }

    val lastSyncTime = CloudSyncConfig.getLastSyncTime(context)
    val lastSyncText = if (lastSyncTime > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", LocalLocale.current.platformLocale).format(lastSyncTime)
    } else "从未"

    BackHandler(enabled = showNotConfiguredDialog || showRestoreConfirm) {
        // [修复防御]: 让 DialogLayout 播放退场动画,LaunchedEffect 会在动画结束后重置外层 flag
        notConfiguredDialogVisible.value = false
        restoreConfirmDialogVisible.value = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "账号与备份",
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
    ) { innerPadding ->
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
            // ===== 卡片 1:当前账号信息 =====
            item(key = "account_info") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    if (accountState.isLoggedIn) {
                        BasicComponent(
                            title = "用户名",
                            summary = accountState.username ?: "—",
                        )
                        BasicComponent(
                            title = "角色",
                            summary = accountState.role ?: "普通用户",
                        )
                    } else {
                        BasicComponent(
                            title = "状态",
                            summary = "未登录",
                        )
                    }
                }
            }

            // ===== 卡片 2:服务器地址(任何时候都暴露) =====
            item(key = "server_config") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    BasicComponent(
                        title = "服务器地址",
                        summary = accountState.serverUrl,
                    )
                    ArrowPreference(
                        title = "修改服务器地址",
                        summary = "登录与备份共用,需重启应用",
                        onClick = {
                            Log.d(TAG, "Open edit server url dialog")
                            showEditServerUrl = true
                        },
                    )
                }
            }

            // ===== 卡片 3:操作(登录 / 登出) =====
            item(key = "account_actions") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    if (accountState.isLoggedIn) {
                        ArrowPreference(
                            title = if (accountState.isLoggingOut) "正在退出..." else "退出登录",
                            summary = "清除本地凭证",
                            enabled = !accountState.isLoggingOut,
                            onClick = {
                                Log.d(TAG, "Open logout confirm")
                                showLogoutConfirm = true
                            },
                        )
                    } else {
                        ArrowPreference(
                            title = "去登录",
                            summary = "登录 Badger-Server 账号",
                            onClick = {
                                Log.d(TAG, "Navigate to login from AccountAndBackupPage")
                                onNavigateToLogin()
                            },
                        )
                    }
                }
            }

            // ===== 卡片 4:云端备份操作 =====
            item(key = "backup_actions") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        title = "开启云端备份",
                        summary = "自动备份和恢复数据",
                        checked = syncEnabled,
                        onCheckedChange = { newValue ->
                            if (newValue && !CloudSyncConfig.isConfigured(context)) {
                                Toast.makeText(context, "请先配置服务器地址并登录", Toast.LENGTH_SHORT).show()
                            } else {
                                syncEnabled = newValue
                                CloudSyncConfig.saveSyncEnabled(context, newValue)
                            }
                        },
                    )
                    ArrowPreference(
                        title = "测试连接",
                        summary = if (isTesting) "连接中..." else testResult ?: "点击测试 Badger-Server 连接",
                        onClick = {
                            if (!CloudSyncConfig.isConfigured(context)) {
                                showNotConfiguredDialog = true
                                return@ArrowPreference
                            }
                            scope.launch {
                                isTesting = true; testResult = null
                                val result = cloudSyncViewModel.testConnection(context)
                                isTesting = false
                                testResult = if (result.isSuccess) "连接成功" else "连接失败: ${result.exceptionOrNull()?.message}"
                            }
                        },
                    )
                    ArrowPreference(
                        title = "立即备份",
                        summary = if (isBackingUp) "备份中..." else backupResult ?: "上传数据到 Badger-Server",
                        onClick = {
                            if (!CloudSyncConfig.isConfigured(context)) {
                                showNotConfiguredDialog = true
                                return@ArrowPreference
                            }
                            scope.launch {
                                isBackingUp = true; backupResult = null
                                val result = cloudSyncViewModel.backup(context)
                                isBackingUp = false
                                backupResult = if (result.isSuccess) "备份成功" else "备份失败: ${result.exceptionOrNull()?.message}"
                            }
                        },
                    )
                    ArrowPreference(
                        title = "恢复数据",
                        summary = if (isRestoring) "恢复中..." else restoreResult ?: "从 Badger-Server 下载数据恢复",
                        onClick = {
                            if (!CloudSyncConfig.isConfigured(context)) {
                                showNotConfiguredDialog = true
                                return@ArrowPreference
                            }
                            Log.d(TAG, "Restore confirm dialog opened")
                            showRestoreConfirm = true
                        },
                    )
                    BasicComponent(title = "上次同步", summary = lastSyncText)
                }
            }

            // ===== 说明 =====
            item(key = "help_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "Badger-Server 同时承担账号鉴权和云端备份,登录与备份共用同一个服务器地址。修改服务器地址后需重启应用,网络客户端才会切到新地址。鉴权使用 JWT,无需在客户端保存密码。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 1.5.em,
                    )
                }
            }
        }
    }

    // ===== 弹窗:修改服务器地址 =====
    if (showEditServerUrl) {
        EditServerUrlDialog(
            currentUrl = accountState.serverUrl,
            onConfirm = { newUrl ->
                Log.d(TAG, "EditServerUrlDialog confirm: $newUrl")
                accountViewModel.updateServerUrl(newUrl)
                showEditServerUrl = false
                Toast.makeText(context, "已保存，请重启应用", Toast.LENGTH_LONG).show()
            },
            onDismiss = {
                Log.d(TAG, "EditServerUrlDialog dismissed")
                showEditServerUrl = false
            },
        )
    }

    // ===== 弹窗:登出确认 =====
    if (showLogoutConfirm) {
        LogoutConfirmDialog(
            isLoggingOut = accountState.isLoggingOut,
            onConfirm = {
                Log.d(TAG, "LogoutConfirmDialog confirm")
                showLogoutConfirm = false
                accountViewModel.logout()
                // App 守卫会自动跳 Route.Login
            },
            onDismiss = {
                Log.d(TAG, "LogoutConfirmDialog cancelled")
                showLogoutConfirm = false
            },
        )
    }

    // ===== 弹窗:未配置提示 =====
    if (showNotConfiguredDialog) {
        LaunchedEffect(Unit) { notConfiguredDialogVisible.value = true }
        LaunchedEffect(notConfiguredDialogVisible.value) {
            if (!notConfiguredDialogVisible.value) showNotConfiguredDialog = false
        }
        DialogLayout(visible = notConfiguredDialogVisible, enableWindowDim = true, renderInRootScaffold = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), insideMargin = PaddingValues(24.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("未配置云同步", style = MiuixTheme.textStyles.subtitle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "请先在「服务器地址」中确认配置正确，并登录账号。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            text = "知道了",
                            onClick = {
                                Log.d(TAG, "Not configured dialog dismissed")
                                showNotConfiguredDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }
    }

    // ===== 弹窗:恢复确认 =====
    if (showRestoreConfirm) {
        LaunchedEffect(Unit) { restoreConfirmDialogVisible.value = true }
        LaunchedEffect(restoreConfirmDialogVisible.value) {
            if (!restoreConfirmDialogVisible.value) showRestoreConfirm = false
        }
        DialogLayout(visible = restoreConfirmDialogVisible, enableWindowDim = true, renderInRootScaffold = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), insideMargin = PaddingValues(24.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("确认恢复", style = MiuixTheme.textStyles.subtitle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "将从 Badger-Server 下载最新备份并恢复数据。恢复后建议重启应用以确保所有设置生效。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(
                                text = "取消",
                                onClick = {
                                    Log.d(TAG, "Restore dialog cancelled")
                                    showRestoreConfirm = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = "恢复",
                                onClick = {
                                    Log.d(TAG, "Restore confirmed, starting restore")
                                    showRestoreConfirm = false
                                    scope.launch {
                                        isRestoring = true
                                        val result = cloudSyncViewModel.restore(context)
                                        isRestoring = false
                                        result.onSuccess { importResult ->
                                            Log.d(TAG, "Restore success: ${importResult.importedContacts} contacts, ${importResult.importedCollections} collections")
                                            restoreResult = "恢复成功:${importResult.importedContacts} 个联系人,${importResult.importedCollections} 个名片夹"
                                        }.onFailure {
                                            Log.d(TAG, "Restore failed: ${it.message}")
                                            restoreResult = "恢复失败: ${it.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }
        }
    }
}
