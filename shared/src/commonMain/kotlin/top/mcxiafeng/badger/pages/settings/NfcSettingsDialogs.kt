package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import top.mcxiafeng.badger.network.ShortIoDomain
import top.mcxiafeng.badger.network.ShortIoLink
import top.mcxiafeng.badger.ui.components.BadgerDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color

private const val MAX_DIALOG_LIST_HEIGHT = 300

/** 域名选择弹窗。 */
@Composable
internal fun NfcDomainPickerDialog(
    domains: List<ShortIoDomain>,
    loading: Boolean,
    error: String?,
    selectedHostname: String,
    onPick: (ShortIoDomain) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    BadgerDialog(
        show = true,
        title = "选择域名",
        onDismissRequest = onDismiss,
        showButtons = false,
    ) {
        when {
            loading -> Box(
                Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(modifier = Modifier.size(36.dp)) }

            error != null -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = error,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(text = "重试", onClick = onRetry, colors = ButtonDefaults.textButtonColorsPrimary())
            }

            domains.isEmpty() -> Text(
                "没有可用域名\n请在 short.io 后台添加",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )

            else -> LazyColumn(modifier = Modifier.heightIn(max = MAX_DIALOG_LIST_HEIGHT.dp)) {
                items(domains, key = { it.hostname }) { d ->
                    val isSelected = d.hostname == selectedHostname
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(d) }
                            .background(
                                if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                                miuixShape(8.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = d.hostname,
                            style = if (isSelected) MiuixTheme.textStyles.subtitle else MiuixTheme.textStyles.body2,
                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** 短链接选择弹窗。 */
@Composable
internal fun NfcLinkPickerDialog(
    links: List<ShortIoLink>,
    loading: Boolean,
    error: String?,
    selectedLinkId: String,
    domain: String,
    onPick: (ShortIoLink) -> Unit,
    onRetry: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    BadgerDialog(
        show = true,
        title = "选择短链接",
        onDismissRequest = onDismiss,
        showButtons = false,
    ) {
        when {
            loading -> Box(
                Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(modifier = Modifier.size(36.dp)) }

            error != null -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = error,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(text = "重试", onClick = onRetry, colors = ButtonDefaults.textButtonColorsPrimary())
            }

            links.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "该域名下还没有短链接",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(text = "创建一个", onClick = onCreate, colors = ButtonDefaults.textButtonColorsPrimary())
            }

            else -> LazyColumn(
                modifier = Modifier.heightIn(max = MAX_DIALOG_LIST_HEIGHT.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(links, key = { it.idString }) { link ->
                    val isSelected = link.idString == selectedLinkId
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(link) }
                            .background(
                                if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                miuixShape(8.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = link.shortURL.ifBlank { "$domain/${link.path}" },
                            style = if (isSelected) MiuixTheme.textStyles.subtitle else MiuixTheme.textStyles.body2,
                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (link.originalURL.isNotBlank()) {
                            Text(
                                text = link.originalURL,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 创建短链接弹窗。 */
@Composable
internal fun NfcCreateLinkDialog(
    creating: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var createUrl by remember { mutableStateOf("") }
    BadgerDialog(
        show = true,
        title = "创建短链接",
        onDismissRequest = onDismiss,
        showButtons = false,
    ) {
        Text(
            "目标链接（对方碰 NFC 后打开的地址）",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(BadgerSpacing.sm))
        TextField(
            value = createUrl,
            onValueChange = { createUrl = it },
            label = "https://example.com",
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(BadgerSpacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(BadgerSpacing.xl))
            TextButton(
                text = if (creating) "创建中..." else "创建",
                onClick = { onConfirm(createUrl) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = !creating,
            )
        }
    }
}
