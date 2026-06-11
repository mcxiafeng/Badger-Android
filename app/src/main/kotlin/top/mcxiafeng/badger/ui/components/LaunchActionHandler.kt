package top.mcxiafeng.badger.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ocr.LaunchAction
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.isShortLink
import top.mcxiafeng.badger.utils.resolveRedirect
import top.mcxiafeng.badger.utils.saveQrToGallery
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton

private const val TAG = "LaunchActionHandler"

/**
 * LaunchAction 处理按钮
 *
 * 根据 LaunchAction 类型显示对应的按钮（跳转/扫码添加/复制并打开）
 */
@Composable
fun RowScope.LaunchActionButtons(
    launchAction: LaunchAction,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    when (launchAction) {
        is LaunchAction.Intents -> {
            TextButton(
                text = "跳转",
                onClick = {
                    scope.launch {
                        handleIntents(context, launchAction, onDismiss)
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
                        handleWechatQrScan(context, launchAction, onDismiss)
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
                    handleCopyAndOpen(context, launchAction, onDismiss)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
        is LaunchAction.None -> {}
    }
}

private suspend fun handleIntents(
    context: Context,
    launchAction: LaunchAction.Intents,
    onDismiss: () -> Unit
) {
    val pkg = launchAction.intents.find { it.`package` != null }?.`package`
    val dataUri = launchAction.intents.firstOrNull()?.data?.toString()

    // 短链先解析
    var resolvedUri = dataUri
    if (dataUri != null && isShortLink(dataUri)) {
        Log.d(TAG, "检测到短链，解析中: $dataUri")
        resolvedUri = resolveRedirect(dataUri) ?: dataUri
        Log.d(TAG, "短链解析结果: $resolvedUri")
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
            Log.d(TAG, "尝试跳转[$idx]: data=${intent.data}, pkg=${intent.`package`}")
            context.startActivity(intent)
            Log.d(TAG, "跳转[$idx]成功")
            onDismiss()
            return
        } catch (e: Exception) {
            Log.w(TAG, "跳转[$idx]失败: ${e.message}")
        }
    }
    withContext(Dispatchers.Main) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun handleWechatQrScan(
    context: Context,
    launchAction: LaunchAction.WechatQrScan,
    onDismiss: () -> Unit
) {
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
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "二维码已保存，请在微信中扫描相册图片", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("LaunchActionHandler", "启动微信扫一扫失败，尝试备用方式", e)
            try {
                val fallback = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage("com.tencent.mm")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                onDismiss()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "二维码已保存，请打开微信扫一扫从相册选取", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("LaunchActionHandler", "启动微信失败，提示用户手动打开", e)
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

private fun handleCopyAndOpen(
    context: Context,
    launchAction: LaunchAction.CopyAndOpen,
    onDismiss: () -> Unit
) {
    Methods.copyToClipboard(context, "", launchAction.copyText)
    Toast.makeText(context, "已复制：${launchAction.copyText}", Toast.LENGTH_SHORT).show()
    try {
        context.startActivity(launchAction.intent)
        onDismiss()
    } catch (e: Exception) {
        Log.e(TAG, "启动Activity失败", e)
        Toast.makeText(context, "无法打开应用", Toast.LENGTH_SHORT).show()
    }
}
