package top.mcxiafeng.badger.pages.person.contact

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.ocr.LaunchAction
import top.mcxiafeng.badger.ocr.buildLaunchAction
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.yukonga.miuix.kmp.window.WindowDialog
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

@Composable
internal fun PlatformDetailDialog(
    show: Boolean,
    platformName: String,
    entry: PlatformEntry,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launchAction = remember(platformName, entry) {
        buildLaunchAction(platformName, entry.value ?: "", entry.jumpLink)
    }

    if (show) WindowDialog(
        show = true,
        title = platformName,
        summary = "长按可复制",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (!entry.displayName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailInfoRow(label = "昵称", value = entry.displayName, context = context)
            }
            if (!entry.value.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailInfoRow(label = "ID", value = entry.value, context = context)
            }
            Spacer(modifier = Modifier.height(8.dp))
            DetailInfoRow(label = "主页链接", value = entry.jumpLink, context = context)
            if (!entry.originalLink.isNullOrBlank() && entry.originalLink != entry.jumpLink) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailInfoRow(label = "原始链接", value = entry.originalLink, context = context)
            }

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

                                    // 短链先解析，拿到最终 URL 再跳转
                                    var resolvedUri = dataUri
                                    if (dataUri != null && isShortLink(dataUri)) {
                                        Log.d("Tester", "检测到短链，解析中: $dataUri")
                                        resolvedUri = resolveRedirect(dataUri) ?: dataUri
                                        Log.d("Tester", "短链解析结果: $resolvedUri")
                                    }

                                    // 用解析后的 URL 重新构建 Intent 列表
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
                                            Log.d("Tester", "尝试跳转[$idx]: data=${intent.data}, pkg=${intent.`package`}")
                                            context.startActivity(intent)
                                            Log.d("Tester", "跳转[$idx]成功")
                                            onDismiss()
                                            return@launch
                                        } catch (e: Exception) {
                                            Log.w("Tester", "跳转[$idx]失败: ${e.message}")
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                                    }
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
                                            // 打开微信扫一扫
                                            val intent = Intent().apply {
                                                setPackage("com.tencent.mm")
                                                action = "com.tencent.mm.action.BIZSHORTCUT"
                                                putExtra("LauncherUI.From.Scaner.Shortcut", true)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            onDismiss()
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "二维码已保存，请在微信中扫描相册图片", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (_: Exception) {
                                            // 回退：直接打开微信主界面
                                            try {
                                                val fallback = Intent(Intent.ACTION_MAIN).apply {
                                                    addCategory(Intent.CATEGORY_LAUNCHER)
                                                    setPackage("com.tencent.mm")
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(fallback)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "二维码已保存，请打开微信扫一扫从相册选取", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (_: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "二维码已保存，请手动打开微信扫一扫", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "保存二维码失败", Toast.LENGTH_SHORT).show()
                                        }
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

/**
 * 生成二维码并保存到相册
 */
internal suspend fun saveQrToGallery(context: Context, content: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, 512, 512, hints
            )
            for (x in 0 until 512) {
                for (y in 0 until 512) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            Methods.saveBitmapToGallery(context, bitmap, "wechat_qr_${System.currentTimeMillis()}.png")
        } catch (e: Exception) {
            false
        }
    }
}

/** 判断是否为短链 */
private fun isShortLink(url: String): Boolean {
    val shortDomains = setOf("xhslink.com", "b23.tv", "t.cn", "dwz.cn", "suo.im")
    val host = try { URI(url).host?.lowercase() ?: return false } catch (_: Exception) { return false }
    return host in shortDomains
}

/** 跟随重定向获取最终 URL，超时 3s */
private suspend fun resolveRedirect(url: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            var currentUrl = url.replace("http://", "https://")
            for (i in 0..5) {
                val conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                Log.d("Tester", "重定向[$i]: code=$code, location=$location")
                if (code in 301..303 && location != null) {
                    currentUrl = if (location.startsWith("http")) location
                    else URL(currentUrl).toURI().resolve(location).toString()
                } else {
                    break
                }
            }
            if (currentUrl != url.replace("http://", "https://")) currentUrl else null
        } catch (e: Exception) {
            Log.w("Tester", "短链解析异常: ${e.message}")
            null
        }
    }
}

/**
 * 详情弹窗中的信息行（带 Miuix 点击反馈效果）
 * 长按复制 value
 */
@Composable
internal fun DetailInfoRow(
    label: String,
    value: String,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = MiuixIndication(),
                onClick = {},
                onLongClick = {
                    Methods.copyToClipboard(context, label, value)
                    Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
                },
            )
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 3
        )
    }
}
