package top.mcxiafeng.badger.pages.person.contact

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.ocr.LaunchAction
import top.mcxiafeng.badger.ocr.buildLaunchAction
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

private const val TAG = "FieldDetailDialog"

/**
 * 联系方式详情弹窗
 *
 * 显示字段值，根据 LaunchAction 提供跳转/扫码添加/复制并打开等操作。
 */
@Composable
fun FieldDetailDialog(
    field: ContactFieldDisplay,
    show: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fieldKey = field.fieldKey
    val launchAction = remember(fieldKey, field.value) {
        if (fieldKey.isNullOrBlank()) LaunchAction.None
        else buildLaunchAction(fieldKey, field.value)
    }

    WindowDialog(
        show = show,
        title = field.fieldName,
        summary = "长按可复制",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            DetailInfoRow(
                label = field.fieldName,
                value = field.value,
                context = context
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                when (launchAction) {
                    is LaunchAction.Intents -> {
                        TextButton(
                            text = "跳转",
                            onClick = {
                                scope.launch {
                                    val pkg = launchAction.intents.find { it.`package` != null }?.`package`
                                    val dataUri = launchAction.intents.firstOrNull()?.data?.toString()

                                    // 短链先解析
                                    var resolvedUri = dataUri
                                    if (dataUri != null) {
                                        val host = try { URI(dataUri).host?.lowercase() } catch (_: Exception) { null }
                                        if (host in setOf("xhslink.com", "b23.tv", "t.cn", "dwz.cn", "suo.im")) {
                                            Log.d(TAG, "检测到短链，解析中: $dataUri")
                                            resolvedUri = withContext(Dispatchers.IO) {
                                                try {
                                                    var cur = dataUri.replace("http://", "https://")
                                                    for (i in 0..5) {
                                                        val conn = URL(cur).openConnection() as HttpURLConnection
                                                        conn.instanceFollowRedirects = false
                                                        conn.connectTimeout = 3000
                                                        conn.readTimeout = 3000
                                                        conn.requestMethod = "GET"
                                                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                                                        val code = conn.responseCode
                                                        val loc = conn.getHeaderField("Location")
                                                        conn.disconnect()
                                                        Log.d(TAG, "重定向[$i]: code=$code, location=$loc")
                                                        if (code in 301..303 && loc != null) {
                                                            cur = if (loc.startsWith("http")) loc
                                                            else URL(cur).toURI().resolve(loc).toString()
                                                        } else break
                                                    }
                                                    val original = dataUri.replace("http://", "https://")
                                                    if (cur != original) cur else null
                                                } catch (e: Exception) {
                                                    Log.w(TAG, "短链解析异常: ${e.message}")
                                                    null
                                                }
                                            } ?: dataUri
                                            Log.d(TAG, "短链解析结果: $resolvedUri")
                                        }
                                    }

                                    // 用解析后的 URL 重新构建 Intent
                                    val intents = if (resolvedUri != dataUri && resolvedUri != null) {
                                        listOfNotNull(
                                            if (pkg != null) Intent(Intent.ACTION_VIEW, Uri.parse(resolvedUri)).apply {
                                                setPackage(pkg)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            } else null,
                                            Intent(Intent.ACTION_VIEW, Uri.parse(resolvedUri)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    } else launchAction.intents

                                    for ((idx, intent) in intents.withIndex()) {
                                        try {
                                            Log.d(TAG, "尝试跳转[$idx]: data=${intent.data}, pkg=${intent.`package`}")
                                            context.startActivity(intent)
                                            Log.d(TAG, "跳转[$idx]成功")
                                            onDismiss()
                                            return@launch
                                        } catch (e: Exception) {
                                            Log.w(TAG, "跳转[$idx]失败: ${e.message}")
                                        }
                                    }
                                    Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                    is LaunchAction.WechatQrScan -> {
                        TextButton(
                            text = "扫码添加",
                            onClick = {
                                scope.launch {
                                    val saved = saveQrToGallery(context, launchAction.qrContent)
                                    if (saved) {
                                        try {
                                            val intent = Intent().apply {
                                                setPackage("com.tencent.mm")
                                                action = "com.tencent.mm.action.BIZSHORTCUT"
                                                putExtra("LauncherUI.From.Scaner.Shortcut", true)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            onDismiss()
                                            Toast.makeText(context, "二维码已保存，请在微信中扫描相册图片", Toast.LENGTH_LONG).show()
                                        } catch (_: Exception) {
                                            try {
                                                val fallback = Intent(Intent.ACTION_MAIN).apply {
                                                    addCategory(Intent.CATEGORY_LAUNCHER)
                                                    setPackage("com.tencent.mm")
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(fallback)
                                                onDismiss()
                                                Toast.makeText(context, "二维码已保存，请打开微信扫一扫从相册选取", Toast.LENGTH_LONG).show()
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "二维码已保存，请手动打开微信扫一扫", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "保存二维码失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                    is LaunchAction.CopyAndOpen -> {
                        TextButton(
                            text = "复制并打开",
                            onClick = {
                                Methods.copyToClipboard(context, "", launchAction.copyText)
                                Toast.makeText(context, "已复制：${launchAction.copyText}", Toast.LENGTH_SHORT).show()
                                try {
                                    context.startActivity(launchAction.intent)
                                    onDismiss()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开应用", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                    is LaunchAction.None -> {}
                }
            }
        }
    }
}
