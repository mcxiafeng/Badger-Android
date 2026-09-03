package top.mcxiafeng.badger.pages.settings

import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.BuildConfig
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "LogViewerPage"

@Composable
internal fun LogViewerPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    var logText by remember { mutableStateOf("") }
    var isPackaging by remember { mutableStateOf(false) }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        Log.d(TAG, "LogViewerPage loaded")
        val text = withContext(Dispatchers.IO) { collectLogcat() }
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
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                        withContext(Dispatchers.IO) { verticalScrollState.scrollTo(0) }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "顶部"
                    )
                }
                IconButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { verticalScrollState.scrollTo(verticalScrollState.maxValue) }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "底部"
                    )
                }
                IconButton(onClick = {
                    Methods.copyToClipboard(context, "软件日志", logText)
                    Toast.makeText(context, "已复制日志", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制"
                    )
                }
                IconButton(onClick = {
                    if (!isPackaging) {
                        isPackaging = true
                        scope.launch {
                            try {
                                val zipFile = withContext(Dispatchers.IO) {
                                    packageLogs(context)
                                }
                                if (zipFile != null) {
                                    shareOrSaveZip(context, zipFile)
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "打包失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "package logs failed", e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "打包失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                isPackaging = false
                            }
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = "打包日志"
                    )
                }
            }
        }
    }
}

private fun collectLogcat(): String {
    return try {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-v", "time", "-t", "2000")
        )
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.appendLine(line)
        }
        reader.close()
        process.waitFor()
        sb.toString()
    } catch (e: Exception) {
        Log.e(TAG, "collectLogcat failed", e)
        "读取日志失败: ${e.message}"
    }
}

private fun getDeviceInfo(): String {
    return buildString {
        appendLine("=== 设备信息 ===")
        appendLine("品牌: ${Build.BRAND}")
        appendLine("型号: ${Build.MODEL}")
        appendLine("设备: ${Build.DEVICE}")
        appendLine("Android 版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("安全补丁: ${Build.VERSION.SECURITY_PATCH}")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        appendLine("=== 应用信息 ===")
        appendLine("版本: ${BuildConfig.VERSION_NAME}")
        appendLine("构建日期: ${BuildConfig.BUILD_DATE}")
        appendLine("构建类型: ${BuildConfig.BUILD_TYPE}")
        appendLine()
    }
}

private fun packageLogs(context: android.content.Context): File? {
    val cacheDir = File(context.cacheDir, "shared").apply { mkdirs() }
    val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
    val zipFile = File(cacheDir, "Badger-log-$timeStamp.zip")

    ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
        zos.putNextEntry(ZipEntry("device_info.txt"))
        zos.write(getDeviceInfo().toByteArray())
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("logcat.txt"))
        zos.write(collectLogcat().toByteArray())
        zos.closeEntry()

        try {
            val tombstoneDir = File("/data/tombstones")
            if (tombstoneDir.exists() && tombstoneDir.isDirectory) {
                tombstoneDir.listFiles()?.take(3)?.forEach { file ->
                    if (file.isFile && file.canRead()) {
                        zos.putNextEntry(ZipEntry("tombstones/${file.name}"))
                        zos.write(file.readBytes())
                        zos.closeEntry()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取 tombstone 文件失败", e)
        }

        try {
            val anrFile = File("/data/anr/traces.txt")
            if (anrFile.exists() && anrFile.canRead()) {
                zos.putNextEntry(ZipEntry("anr/traces.txt"))
                zos.write(anrFile.readBytes())
                zos.closeEntry()
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取 ANR traces 文件失败", e)
        }
    }

    Log.d(TAG, "packageLogs: zip created at ${zipFile.absolutePath}")
    return zipFile
}

private fun shareOrSaveZip(context: android.content.Context, zipFile: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        zipFile
    )

    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享日志文件"))
    } catch (e: Exception) {
        Log.e(TAG, "shareOrSaveZip failed", e)
        Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}