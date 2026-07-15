package top.mcxiafeng.badger.pages.settings

import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

private const val TAG = "Tester"

@Composable
internal fun CloudSyncSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
    val cloudSyncViewModel: CloudSyncSettingsViewModel = hiltViewModel()

    var serverUrl by rememberSaveable { mutableStateOf(CloudSyncConfig.getServerUrl(context)) }
    var username by rememberSaveable { mutableStateOf(CloudSyncConfig.getUsername(context)) }
    var password by rememberSaveable { mutableStateOf(CloudSyncConfig.getPassword(context)) }
    var passwordVisible by remember { mutableStateOf(false) }
    var remotePath by remember { mutableStateOf(CloudSyncConfig.getRemotePath(context)) }
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

    val lastSyncTime = CloudSyncConfig.getLastSyncTime(context)
    val lastSyncText = if (lastSyncTime > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", LocalLocale.current.platformLocale).format(lastSyncTime)
    } else "从未"

    BackHandler(enabled = showNotConfiguredDialog || showRestoreConfirm) {
        // 让 DialogLayout 播放退场动画，LaunchedEffect 会在动画结束后重置外层 flag
        notConfiguredDialogVisible.value = false
        restoreConfirmDialogVisible.value = false
    }

    Scaffold(
        topBar = { TopAppBar(title = "云端备份", scrollBehavior = topAppBarScrollBehavior, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }) },
    ) { innerPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "webdav_config") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(text = "备份服务器", style = MiuixTheme.textStyles.body1); Spacer(Modifier.height(4.dp))
                        TextField(value = serverUrl, onValueChange = { serverUrl = it; CloudSyncConfig.saveServerUrl(context, it) }, label = "https://你的NAS地址:端口/dav/", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(text = "用户名", style = MiuixTheme.textStyles.body1); Spacer(Modifier.height(4.dp))
                        TextField(value = username, onValueChange = { username = it; CloudSyncConfig.saveUsername(context, it) }, label = "用户名", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(text = "密码", style = MiuixTheme.textStyles.body1); Spacer(Modifier.height(4.dp))
                        TextField(
                            value = password,
                            onValueChange = { password = it; CloudSyncConfig.savePassword(context, it) },
                            label = "密码",
                            useLabelAsPlaceholder = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (passwordVisible) "隐藏" else "显示"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(text = "备份文件夹", style = MiuixTheme.textStyles.body1); Spacer(Modifier.height(4.dp))
                        TextField(value = remotePath, onValueChange = { remotePath = it; CloudSyncConfig.saveRemotePath(context, it) }, label = "/badger-backup/", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            item(key = "webdav_actions") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(title = "测试连接", summary = if (isTesting) "连接中..." else testResult ?: "点击测试备份服务器连接", onClick = {
                        if (!CloudSyncConfig.isConfigured(context)) { showNotConfiguredDialog = true; return@ArrowPreference }
                        scope.launch {
                            isTesting = true; testResult = null
                            val result = cloudSyncViewModel.testConnection(context)
                            isTesting = false
                            testResult = result.getOrNull()?.let { "连接成功" } ?: "连接失败: ${result.exceptionOrNull()?.message}"
                        }
                    })
                    ArrowPreference(title = "立即备份", summary = if (isBackingUp) "备份中..." else backupResult ?: "上传数据到备份服务器", onClick = {
                        if (!CloudSyncConfig.isConfigured(context)) { showNotConfiguredDialog = true; return@ArrowPreference }
                        scope.launch {
                            isBackingUp = true; backupResult = null
                            val result = cloudSyncViewModel.backup(context)
                            isBackingUp = false
                            backupResult = result.getOrNull()?.let { "备份成功" } ?: "备份失败: ${result.exceptionOrNull()?.message}"
                        }
                    })
                    ArrowPreference(title = "恢复数据", summary = if (isRestoring) "恢复中..." else restoreResult ?: "从备份服务器下载数据恢复", onClick = {
                        if (!CloudSyncConfig.isConfigured(context)) { showNotConfiguredDialog = true; return@ArrowPreference }
                        Log.d(TAG, "Restore confirm dialog opened")
                        showRestoreConfirm = true
                    })
                    BasicComponent(title = "上次同步", summary = lastSyncText)
                }
            }
            item(key = "cloud_help") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Text(
                        text = "填入你自己的 NAS 或云盘的 WebDAV 地址即可备份",
                        style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, lineHeight = 1.5.em
                    )
                }
            }
        }
    }

    // 未配置提示弹窗
    if (showNotConfiguredDialog) {
        LaunchedEffect(Unit) { notConfiguredDialogVisible.value = true }
        // DialogLayout 点击外部关闭时只更新 dialogVisible，需要同步重置外层 flag
        LaunchedEffect(notConfiguredDialogVisible.value) {
            if (!notConfiguredDialogVisible.value) showNotConfiguredDialog = false
        }
        DialogLayout(visible = notConfiguredDialogVisible, enableWindowDim = true, renderInRootScaffold = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), insideMargin = PaddingValues(24.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("未配置云同步", style = MiuixTheme.textStyles.subtitle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("请先填写备份服务器地址和用户名，才能使用云同步功能。", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(text = "知道了", onClick = { Log.d(TAG, "Not configured dialog dismissed"); showNotConfiguredDialog = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
                    }
                }
            }
        }
    }

    // 恢复确认弹窗
    if (showRestoreConfirm) {
        LaunchedEffect(Unit) { restoreConfirmDialogVisible.value = true }
        // DialogLayout 点击外部关闭时只更新 dialogVisible，需要同步重置外层 flag
        LaunchedEffect(restoreConfirmDialogVisible.value) {
            if (!restoreConfirmDialogVisible.value) showRestoreConfirm = false
        }
        DialogLayout(visible = restoreConfirmDialogVisible, enableWindowDim = true, renderInRootScaffold = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), insideMargin = PaddingValues(24.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("确认恢复", style = MiuixTheme.textStyles.subtitle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("将从备份服务器下载最新备份并恢复数据。恢复后建议重启应用以确保所有设置生效。", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(text = "取消", onClick = { Log.d(TAG, "Restore dialog cancelled"); showRestoreConfirm = false }, modifier = Modifier.weight(1f))
                            TextButton(text = "恢复", onClick = {
                                Log.d(TAG, "Restore confirmed, starting restore")
                                showRestoreConfirm = false
                                scope.launch {
                                    isRestoring = true
                                    val result = cloudSyncViewModel.restore(context)
                                    isRestoring = false
                                    result.onSuccess { importResult ->
                                        Log.d(TAG, "Restore success: ${importResult.importedContacts} contacts, ${importResult.importedCollections} collections")
                                        restoreResult = "恢复成功：${importResult.importedContacts} 个联系人，${importResult.importedCollections} 个名片夹"
                                    }.onFailure {
                                        Log.d(TAG, "Restore failed: ${it.message}")
                                        restoreResult = "恢复失败: ${it.message}"
                                    }
                                }
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
                        }
                    }
                }
            }
        }
    }
}
