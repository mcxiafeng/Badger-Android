package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ocr.LaunchAction
import top.mcxiafeng.badger.platform.executeLaunchAction
import top.mcxiafeng.badger.platform.PlatformClipboard
import top.mcxiafeng.badger.platform.showToast
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton

private const val TAG = "LaunchActionHandler"

/**
 * [KMP K13b] LaunchAction 处理按钮（common 化：原 app 侧 LaunchActionHandler.kt 的 UI 半边；
 * Intent 执行链下沉到 [executeLaunchAction] 的平台 actual）。
 *
 * 根据 LaunchAction 类型显示对应的按钮（跳转/扫码添加/复制并打开）。
 */
@Composable
fun RowScope.LaunchActionButtons(
    launchAction: LaunchAction,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    when (launchAction) {
        is LaunchAction.OpenUrls -> {
            TextButton(
                text = "跳转",
                onClick = { scope.launchAndReport { executeLaunchAction(launchAction); onDismiss() } },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
        is LaunchAction.WechatQrScan -> {
            TextButton(
                text = "扫码添加",
                onClick = {
                    scope.launch {
                        val saved = executeLaunchAction(launchAction)
                        onDismiss()
                        showToast(
                            when {
                                saved -> "二维码已保存，请在微信中扫描相册图片"
                                else -> "保存二维码失败"
                            }
                        )
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
                    // 复制即时生效（原实现为同步路径）；打开失败给出反馈
                    PlatformClipboard.copy(launchAction.copyText)
                    scope.launch {
                        val ok = executeLaunchAction(launchAction)
                        onDismiss()
                        showToast(if (ok) "已复制：${launchAction.copyText}" else "无法打开应用")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
        is LaunchAction.None -> {}
    }
}

private fun CoroutineScope.launchAndReport(block: suspend () -> Unit) = launch {
    runCatching { block() }
}
