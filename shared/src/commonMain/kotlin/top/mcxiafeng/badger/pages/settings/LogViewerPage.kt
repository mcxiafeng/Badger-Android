package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.platform.SystemShare
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.pages.settings.components.SettingsSubPageScaffold
import top.mcxiafeng.badger.platform.AppInfo
import top.mcxiafeng.badger.platform.CacheFiles
import top.mcxiafeng.badger.platform.LogCollector
import top.mcxiafeng.badger.platform.PlatformInfo
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import kotlinx.coroutines.CancellationException

private const val TAG = "LogViewerPage"

/**
 * 软件日志页（重写：共享脚手架 + 删死代码）。
 *
 * 删除：`formatZipTimestamp`（21 行 civil-from-days 历法算法，零调用方）；
 * `shareLogText(dirPair)` 的无用 dirPair 参数（仅 text 被用）。
 */
@Composable
internal fun LogViewerPage(onBack: () -> Unit) {
    val appInfo = remember { KoinComponentBy.get<AppInfo>() }
    val scope = rememberCoroutineScope()

    var logText by remember { mutableStateOf("") }
    var isPackaging by remember { mutableStateOf(false) }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        BadgerLog.d(TAG, "LogViewerPage loaded")
        val text = withContext(BadgerDispatchers.io) { LogCollector.collectRecentLogs() }
        logText = text
    }

    // 打包中拦截返回，防误触丢失结果
    BackHandler(enabled = isPackaging) { }

    SettingsSubPageScaffold(title = SettingsPage.AppLog.title, onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .fillMaxSize(),
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                BasicText(
                    text = logText.ifEmpty { "正在加载日志..." },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState)
                        .horizontalScroll(horizontalScrollState)
                        .padding(12.dp),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                    ),
                    softWrap = false,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    scope.launch {
                        withContext(BadgerDispatchers.io) { verticalScrollState.scrollTo(0) }
                    }
                }) {
                    Icon(imageVector = Lucide.ArrowUp, contentDescription = "顶部")
                }
                IconButton(onClick = {
                    scope.launch {
                        withContext(BadgerDispatchers.io) { verticalScrollState.scrollTo(verticalScrollState.maxValue) }
                    }
                }) {
                    Icon(imageVector = Lucide.ArrowDown, contentDescription = "底部")
                }
                IconButton(onClick = {
                    Methods.copyToClipboard("软件日志", logText)
                    showToast("已复制日志")
                }) {
                    Icon(imageVector = Lucide.Copy, contentDescription = "复制")
                }
                IconButton(onClick = {
                    if (!isPackaging) {
                        isPackaging = true
                        scope.launch {
                            try {
                                val ok = withContext(BadgerDispatchers.io) { packageLogs(appInfo) }
                                if (ok) {
                                    shareLogText()
                                } else {
                                    withContext(Dispatchers.Main) { showToast("打包失败") }
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                BadgerLog.e(TAG, "package logs failed", e)
                                withContext(Dispatchers.Main) { showToast("打包失败: ${e.message}") }
                            } finally {
                                isPackaging = false
                            }
                        }
                    }
                }) {
                    Icon(imageVector = Lucide.Package, contentDescription = "打包日志")
                }
            }
        }
    }
}

private fun getDeviceInfo(appInfo: AppInfo): String = buildString {
    appendLine("=== 设备信息 ===")
    appendLine("型号: ${PlatformInfo.deviceModel}")
    appendLine("系统版本: API/OS ${PlatformInfo.apiLevel}")
    appendLine(LogCollector.deviceAbiLine())
    appendLine()
    appendLine("=== 应用信息 ===")
    appendLine("版本: ${appInfo.versionName}")
    appendLine("构建日期: ${appInfo.buildDate}")
    appendLine()
}

/** 把 device_info.txt + logcat.txt 写入 cache/shared；返回是否成功。 */
private suspend fun packageLogs(appInfo: AppInfo): Boolean {
    // iOS 无 java.util.zip，改为两份文本路径，分享由 SystemShare.shareText 承担。
    CacheFiles.writeTextToCache("shared", "device_info.txt", getDeviceInfo(appInfo)) ?: return false
    CacheFiles.writeTextToCache("shared", "logcat.txt", LogCollector.collectRecentLogs()) ?: return false
    return true
}

private fun shareLogText() {
    val text = LogCollector.collectRecentLogs()
    try {
        SystemShare.shareText("应用日志", text.ifBlank { "(空日志)" })
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        BadgerLog.e(TAG, "shareLogText failed", e)
        showToast("分享失败: ${e.message}")
    }
}
