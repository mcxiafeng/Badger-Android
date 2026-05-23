package top.mcxiafeng.badger.pages.social

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.viewmodels.NfcWriteState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

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
                    Text(text = "NFC 标签写入", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    when (state) {
                        NfcWriteState.PREPARING -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "正在准备短链接...", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        NfcWriteState.READY -> {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "请将空白 NFC 标签\n贴到手机 NFC 感应区",
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                            if (!shortUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = shortUrl, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        NfcWriteState.SUCCESS -> {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "写入成功", fontSize = 16.sp, color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        NfcWriteState.ERROR -> {
                            Text(text = "写入失败", fontSize = 16.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = message ?: "未知错误", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, textAlign = TextAlign.Center)
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
                                TextButton(
                                    text = if (isShortLinkConfigured) "重试" else "去设置",
                                    onClick = if (isShortLinkConfigured) onRetry else onOpenShortLinkSettings,
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
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}