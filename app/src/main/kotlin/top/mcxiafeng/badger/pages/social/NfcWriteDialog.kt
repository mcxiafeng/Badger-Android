package top.mcxiafeng.badger.pages.social

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import top.yukonga.miuix.kmp.basic.Card
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.PopupWindow
import top.mcxiafeng.badger.ui.glass.RetainedPopup
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "NfcWriteDialogTester"
private val NfcHorizontalMargin = 32.dp
private val NfcPromptLineHeight = 1.5.em

@Composable
internal fun NfcWriteDialog(
    state: NfcWriteState, message: String?, shortUrl: String?, nfcSupported: Boolean,
    isShortLinkConfigured: Boolean = true, onDismiss: () -> Unit, onRetry: () -> Unit,
    onOpenNfcSettings: () -> Unit, onOpenShortLinkSettings: () -> Unit,
) {
    val blockBack = state == NfcWriteState.PREPARING || state == NfcWriteState.READY
    SideEffect { Log.d(TAG, "NFC write panel; state=$state; blockBack=$blockBack; sharedModalStyle=true") }
    BackHandler(enabled = blockBack) { Log.d(TAG, "Back intercepted during state=$state") }
    val dismiss = { Log.d(TAG, "NFC write panel close requested; state=$state"); onDismiss() }
    NfcModalFrame(blockBack, dismiss) {
        NfcDialogContent(state, message, shortUrl, isShortLinkConfigured, dismiss, onRetry, onOpenShortLinkSettings)
    }
}

@Composable
private fun NfcModalFrame(blockBack: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    RetainedPopup(show = true) { motion ->
        PopupWindow(motion, onDismissRequest = if (blockBack) null else onDismiss) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = NfcHorizontalMargin)
                    .graphicsLayer { alpha = motion.progress.value.coerceIn(0f, 1f) },
                    cornerRadius = BadgerRadius.surface,
                    insideMargin = PaddingValues(BadgerSpacing.xl), content = content)
            }
        }
    }
}

@Composable
private fun NfcDialogContent(
    state: NfcWriteState, message: String?, shortUrl: String?, configured: Boolean,
    onDismiss: () -> Unit, onRetry: () -> Unit, onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(BadgerSpacing.sm))
        Text("NFC 标签写入", style = MiuixTheme.textStyles.title3)
        Spacer(Modifier.height(BadgerSpacing.lg))
        if (state == NfcWriteState.ERROR) {
            NfcErrorBody(message, configured, onDismiss, onRetry, onSettings)
        } else {
            NfcStateBody(state, shortUrl)
            Spacer(Modifier.height(BadgerSpacing.lg))
            TextButton(text = "关闭", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(BadgerSpacing.xs))
        }
    }
}

@Composable
private fun ColumnScope.NfcStateBody(state: NfcWriteState, shortUrl: String?) {
    val colors = MiuixTheme.colorScheme
    when (state) {
        NfcWriteState.PREPARING -> {
            CircularProgressIndicator(Modifier.size(BadgerSize.minimumTouchTarget))
            Spacer(Modifier.height(BadgerSpacing.md))
            Text("正在准备链接...", style = MiuixTheme.textStyles.body2, color = colors.onSurfaceVariantSummary)
        }
        NfcWriteState.READY -> {
            NfcLinkIcon()
            Spacer(Modifier.height(BadgerSpacing.md))
            Text("请将空白 NFC 标签\n贴到手机 NFC 感应区", style = MiuixTheme.textStyles.title4,
                textAlign = TextAlign.Center, lineHeight = NfcPromptLineHeight)
            if (!shortUrl.isNullOrBlank()) {
                Spacer(Modifier.height(BadgerSpacing.sm))
                Text(shortUrl, style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceVariantSummary)
            }
        }
        NfcWriteState.SUCCESS -> {
            NfcLinkIcon()
            Spacer(Modifier.height(BadgerSpacing.sm))
            Text("写入成功", style = MiuixTheme.textStyles.title4, color = colors.primary)
        }
        NfcWriteState.ERROR, NfcWriteState.IDLE -> Unit
    }
}

@Composable
private fun NfcLinkIcon() {
    Icon(Icons.Default.Link, contentDescription = null, tint = MiuixTheme.colorScheme.primary,
        modifier = Modifier.size(BadgerSize.minimumTouchTarget))
}

@Composable
private fun ColumnScope.NfcErrorBody(
    message: String?, configured: Boolean, onDismiss: () -> Unit, onRetry: () -> Unit, onSettings: () -> Unit,
) {
    Text("写入失败", style = MiuixTheme.textStyles.title4, color = MiuixTheme.colorScheme.error)
    Spacer(Modifier.height(BadgerSpacing.sm))
    Text(message ?: "未知错误", style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceSecondary, textAlign = TextAlign.Center)
    Spacer(Modifier.height(BadgerSpacing.lg))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        TextButton(text = "关闭", onClick = onDismiss, modifier = Modifier.weight(1f))
        TextButton(text = if (configured) "重试" else "去设置", modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(), onClick = {
                if (configured) { Log.d(TAG, "NFC retry requested"); onRetry() }
                else { Log.d(TAG, "Short link settings requested"); onSettings() }
            })
    }
    Spacer(Modifier.height(BadgerSpacing.xs))
}
