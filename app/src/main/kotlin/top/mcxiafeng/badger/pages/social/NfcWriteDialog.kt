package top.mcxiafeng.badger.pages.social

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.pages.social.NfcWriteState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

private const val TAG = "Tester"

/**
 * NFC 标签写入对话框
 *
 * 状态流程：PREPARING → READY → SUCCESS / ERROR
 */
@Composable
internal fun NfcWriteDialog(
    state: NfcWriteState,
    message: String?,
    shortUrl: String?,
    nfcSupported: Boolean,
    isShortLinkConfigured: Boolean = true,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onOpenShortLinkSettings: () -> Unit
) {
    val visible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible.value = true }

    // PREPARING/READY 状态下拦截返回键，防止中断 NFC 写入流程
    BackHandler(enabled = state == NfcWriteState.PREPARING || state == NfcWriteState.READY) {
        Log.d(TAG, "NfcWrite: BackHandler intercepted in state=$state")
    }

    DialogLayout(
        visible = visible,
        enableWindowDim = true,
        enterTransition = fadeIn(tween(300)),
        exitTransition = fadeOut(tween(200)),
        renderInRootScaffold = true,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                insideMargin = PaddingValues(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "NFC 标签写入", style = MiuixTheme.textStyles.title3)
                    Spacer(modifier = Modifier.height(16.dp))

                    when (state) {
                        NfcWriteState.PREPARING -> {
                            Log.d(TAG, "NfcWrite state: PREPARING")
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "正在准备短链接...", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        NfcWriteState.READY -> {
                            Log.d(TAG, "NfcWrite state: READY, shortUrl=$shortUrl")
                            top.yukonga.miuix.kmp.basic.Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "请将空白 NFC 标签\n贴到手机 NFC 感应区",
                                style = MiuixTheme.textStyles.title4,
                                textAlign = TextAlign.Center,
                                lineHeight = 1.5.em
                            )
                            if (!shortUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = shortUrl, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        NfcWriteState.SUCCESS -> {
                            Log.d(TAG, "NfcWrite state: SUCCESS")
                            top.yukonga.miuix.kmp.basic.Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "写入成功", style = MiuixTheme.textStyles.title4, color = MiuixTheme.colorScheme.primary)
                        }
                        NfcWriteState.ERROR -> {
                            Log.d(TAG, "NfcWrite state: ERROR, message=$message")
                            Text(text = "写入失败", style = MiuixTheme.textStyles.title4, color = MiuixTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = message ?: "未知错误", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    text = "关闭",
                                    onClick = { Log.d(TAG, "NfcWrite dismiss from ERROR"); onDismiss() },
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    text = if (isShortLinkConfigured) "重试" else "去设置",
                                    onClick = {
                                        if (isShortLinkConfigured) { Log.d(TAG, "NfcWrite retry"); onRetry() }
                                        else { Log.d(TAG, "NfcWrite open shortLink settings"); onOpenShortLinkSettings() }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            return@Card
                        }
                        NfcWriteState.IDLE -> {}
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        text = "关闭",
                        onClick = { Log.d(TAG, "NfcWrite dismiss"); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}