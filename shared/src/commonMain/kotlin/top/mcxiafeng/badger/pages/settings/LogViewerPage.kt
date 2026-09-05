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
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.platform.AppInfo
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.shared.util.nowMs
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Package
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.CacheFiles
import top.mcxiafeng.badger.platform.LogCollector
import top.mcxiafeng.badger.platform.PlatformInfo
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.shared.util.BadgerDispatchers



/** zip 文件名时间戳（epoch 派生 yyyyMMdd-HHmmss，跨端无 SimpleDateFormat）。 */
private fun formatZipTimestamp(): String {
    val total = (nowMs() / 1000).toInt()
    val days = total / 86400
    val daySeconds = total % 86400
    // civil-from-days 算法（Howard Hinnant）：epoch → y/m/d
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y2 = if (m <= 2) y + 1 else y
    val h = daySeconds / 3600
    val mi = (daySeconds % 3600) / 60
    val sec = daySeconds % 60
    fun p2(v: Int) = v.toString().padStart(2, '0')
    return "$y2-${p2(m)}-${p2(d)}-${p2(h)}${p2(mi)}${p2(sec)}"
}

private const val TAG = "LogViewerPage"

@Composable
internal fun LogViewerPage(onBack: () -> Unit) {
    val appInfo = remember { KoinComponentBy.get<AppInfo>() }
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    var logText by remember { mutableStateOf("") }
    var isPackaging by remember { mutableStateOf(false) }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        BadgerLog.d(TAG, "LogViewerPage loaded")
        val text = withContext(BadgerDispatchers.io) { LogCollector.collectRecentLogs() }
        logText = text
    }

    BackHandler(enabled = isPackaging) {
        // 打包中不允许返回
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "软件日志",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Lucide.ArrowLeft, contentDescription = "返回")
                    }
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .padding(bottom = floatingBarBottomPadding)
                .fillMaxSize()
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
                        color = MiuixTheme.colorScheme.onBackground
                    ),
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    scope.launch {
                        withContext(BadgerDispatchers.io) { verticalScrollState.scrollTo(0) }
                    }
                }) {
                    Icon(
                        imageVector = Lucide.ArrowUp,
                        contentDescription = "顶部"
                    )
                }
                IconButton(onClick = {
                    scope.launch {
                        withContext(BadgerDispatchers.io) { verticalScrollState.scrollTo(verticalScrollState.maxValue) }
                    }
                }) {
                    Icon(
                        imageVector = Lucide.ArrowDown,
                        contentDescription = "底部"
                    )
                }
                IconButton(onClick = {
                    Methods.copyToClipboard("软件日志", logText)
                    showToast("已复制日志")
                }) {
                    Icon(
                        imageVector = Lucide.Copy,
                        contentDescription = "复制"
                    )
                }
                IconButton(onClick = {
                    if (!isPackaging) {
                        isPackaging = true
                        scope.launch {
                            try {
                                val dirPair = withContext(BadgerDispatchers.io) {
                                    packageLogs(appInfo)
                                }
                                if (dirPair != null) {
                                    shareLogText(dirPair)
                                } else {
                                    withContext(Dispatchers.Main) {
                                        showToast("打包失败")
                                    }
                                }
                            } catch (e: Exception) {
                                BadgerLog.e(TAG, "package logs failed", e)
                                withContext(Dispatchers.Main) {
                                    showToast("打包失败: ${e.message}")
                                }
                            } finally {
                                isPackaging = false
                            }
                        }
                    }
                }) {
                    Icon(
                        imageVector = Lucide.Package,
                        contentDescription = "打包日志"
                    )
                }
            }
        }
    }
}


private fun getDeviceInfo(appInfo: AppInfo): String {
    return buildString {
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
}

private fun packageLogs(appInfo: AppInfo): Pair<String, String>? {
    // [KMP K13c] iOS 无 java.util.zip——改为两份文本路径（device_info / logcat），
    // 分享文本由 SystemShare.shareText 承担；zip 打包回归 Android 专属（K17 评估 iOS 打包方案）。
    val dir = LogCollector.cacheDirPath()
    val info = CacheFiles.writeTextToCache("shared", "device_info.txt", getDeviceInfo(appInfo))
    val logs = CacheFiles.writeTextToCache("shared", "logcat.txt", LogCollector.collectRecentLogs())
    if (info == null || logs == null) return null
    return Pair(dir, "shared")
}


private fun shareLogText(dirPair: Pair<String, String>) {
    val text = LogCollector.collectRecentLogs()
    try {
        SystemShare.shareText("应用日志", text.ifBlank { "(空日志)" })
    } catch (e: Exception) {
        BadgerLog.e(TAG, "shareLogText failed", e)
        showToast("分享失败: ${e.message}")
    }
}
