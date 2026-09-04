package top.mcxiafeng.badger.pages.person.contact.dialogs

import android.content.Context
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.ocr.LaunchAction
import top.mcxiafeng.badger.ocr.buildLaunchAction
import top.mcxiafeng.badger.ui.components.LaunchActionButtons
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun PlatformDetailDialog(
    show: Boolean,
    platformName: String,
    entry: PlatformEntry,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

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
            val detailDisplayName = entry.displayName
            if (!detailDisplayName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailInfoRow(label = "昵称", value = detailDisplayName, context = context)
            }
            val detailValue = entry.value
            if (!detailValue.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailInfoRow(label = "ID", value = detailValue, context = context)
            }
            Spacer(modifier = Modifier.height(8.dp))
            DetailInfoRow(label = "主页链接", value = entry.jumpLink, context = context)
            val detailOriginalLink = entry.originalLink
            if (!detailOriginalLink.isNullOrBlank() && detailOriginalLink != entry.jumpLink) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailInfoRow(label = "原始链接", value = detailOriginalLink, context = context)
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
                LaunchActionButtons(
                    launchAction = launchAction,
                    onDismiss = onDismiss
                )
            }
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
