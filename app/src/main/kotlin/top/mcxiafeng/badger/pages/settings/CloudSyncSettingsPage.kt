package top.mcxiafeng.badger.pages.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.rememberContactRepository
import top.mcxiafeng.badger.network.CloudSyncManager
import top.mcxiafeng.badger.network.WebDavConfig
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

private const val TAG = "CloudSyncSettings"

@Composable
internal fun CloudSyncSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val repository = rememberContactRepository()

    var serverUrl by remember { mutableStateOf(WebDavConfig.getServerUrl(context)) }
    var username by remember { mutableStateOf(WebDavConfig.getUsername(context)) }
    var password by remember { mutableStateOf(WebDavConfig.getPassword(context)) }
    var passwordVisible by remember { mutableStateOf(false) }
    var remotePath by remember { mutableStateOf(WebDavConfig.getRemotePath(context)) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var backupResult by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showNotConfiguredDialog by remember { mutableStateOf(false) }

    val lastSyncTime = WebDavConfig.getLastSyncTime(context)
    val lastSyncText = if (lastSyncTime > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(lastSyncTime)
    } else "从未"

    Scaffold(
        topBar = { TopAppBar(title = "云端备份", scrollBehavior = topAppBarScrollBehavior, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }) },
    ) { innerPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            item(key = "webdav_config") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(text = "备份服务器", fontSize = 15.sp); Spacer(Modifier.height(4.dp))
                        TextField(value = serverUrl, onValueChange = { serverUrl = it; WebDavConfig.saveServerUrl(context, it) }, label = "https://你的NAS地址:端口/dav/", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(text = "用户名", fontSize = 15.sp); Spacer(Modifier.height(4.dp))
                        TextField(value = username, onValueChange = { username = it; WebDavConfig.saveUsername(context, it) }, label = "用户名", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(text = "密码", fontSize = 15.sp); Spacer(Modifier.height(4.dp))
                        TextField(
                            value = password,
                            onValueChange = { password = it; WebDavConfig.savePassword(context, it) },
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
                        Text(text = "备份文件夹", fontSize = 15.sp); Spacer(Modifier.height(4.dp))
                        TextField(value = remotePath, onValueChange = { remotePath = it; WebDavConfig.saveRemotePath(context, it) }, label = "/badger-backup/", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            item(key = "webdav_actions") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(title = "测试连接", summary = if (isTesting) "连接中..." else testResult ?: "点击测试备份服务器连接", onClick = {
                        if (!WebDavConfig.isConfigured(context)) { showNotConfiguredDialog = true; return@ArrowPreference }
                        scope.launch {
                            isTesting = true; testResult = null
                            val result = CloudSyncManager.testConnection(context)
                            isTesting = false
                            testResult = result.getOrNull()?.let { "连接成功" } ?: "连接失败: ${result.exceptionOrNull()?.message}"
                        }
                    })
                    ArrowPreference(title = "立即备份", summary = if (isBackingUp) "备份中..." else backupResult ?: "上传数据到备份服务器", onClick = {
                        if (!WebDavConfig.isConfigured(context)) { showNotConfiguredDialog = true; return@ArrowPreference }
                        scope.launch {
                            isBackingUp = true; backupResult = null
                            val result = CloudSyncManager.backup(context, repository)
                            isBackingUp = false
                            backupResult = result.getOrNull()?.let { "备份成功" } ?: "备份失败: ${result.exceptionOrNull()?.message}"
                        }
                    })
                    ArrowPreference(title = "恢复数据", summary = if (isRestoring) "恢复中..." else "从备份服务器下载数据恢复", onClick = {
                        if (!WebDavConfig.isConfigured(context)) { showNotConfiguredDialog = true; return@ArrowPreference }
                        showRestoreConfirm = true
                    })
                    ArrowPreference(title = "上次同步", summary = lastSyncText, onClick = {})
                }
            }
            item(key = "cloud_help") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Text(
                        text = "填入你自己的 NAS 或云盘的 WebDAV 地址即可备份",
                        fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, lineHeight = 18.sp
                    )
                }
            }
        }
    }

    // 未配置提示弹窗
    if (showNotConfiguredDialog) {
        val dialogVisible = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { dialogVisible.value = true }
        DialogLayout(visible = dialogVisible, enableWindowDim = true, renderInRootScaffold = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), insideMargin = PaddingValues(24.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("未配置云同步", style = MiuixTheme.textStyles.subtitle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("请先填写备份服务器地址和用户名，才能使用云同步功能。", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(text = "知道了", onClick = { showNotConfiguredDialog = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
                    }
                }
            }
        }
    }

    // 恢复确认弹窗
    if (showRestoreConfirm) {
        val dialogVisible = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { dialogVisible.value = true }
        DialogLayout(visible = dialogVisible, enableWindowDim = true, renderInRootScaffold = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), insideMargin = PaddingValues(24.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("确认恢复", style = MiuixTheme.textStyles.subtitle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("将从备份服务器下载最新备份并恢复数据。恢复后建议重启应用以确保所有设置生效。", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(text = "取消", onClick = { showRestoreConfirm = false }, modifier = Modifier.weight(1f))
                            TextButton(text = "恢复", onClick = {
                                showRestoreConfirm = false
                                scope.launch {
                                    isRestoring = true
                                    val restoreResult = CloudSyncManager.restore(context, repository)
                                    isRestoring = false
                                    restoreResult.onSuccess { importResult ->
                                        backupResult = "恢复成功：${importResult.importedContacts} 个联系人，${importResult.importedCollections} 个名片夹"
                                    }.onFailure {
                                        backupResult = "恢复失败: ${it.message}"
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
