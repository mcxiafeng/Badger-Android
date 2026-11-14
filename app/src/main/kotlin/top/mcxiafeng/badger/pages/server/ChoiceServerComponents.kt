package top.mcxiafeng.badger.pages.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val SPINNER_SIZE = 16.dp
internal val SPINNER_STROKE = 2.dp

private val HERO_DISC_SIZE = 64.dp
private val HERO_ICON_SIZE = 32.dp
private const val HERO_BG_ALPHA = 0.12f

/** 新地址默认走 https（开关可关）。 */
internal const val DEFAULT_HTTPS = true

internal val SNACKBAR_BOTTOM_CLEARANCE = 64.dp

/** 地址卡片上方：图标 + 标题 + 一行说明。 */
@Composable
internal fun ServerHero() {
    val colorScheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(HERO_DISC_SIZE)
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = HERO_BG_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Dns,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(HERO_ICON_SIZE),
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        Text(
            text = "连接服务器",
            style = MiuixTheme.textStyles.headline1,
            color = colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        Text(
            text = "登录后云端同步名片",
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
internal fun ServerUrlInput(
    value: String,
    onValueChange: (String) -> Unit,
    reason: String?,
    onImeAction: (() -> Unit)? = null,
) {
    Column {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = "地址",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = if (onImeAction != null) {
                KeyboardActions(onDone = { onImeAction() })
            } else {
                KeyboardActions.Default
            },
        )
        if (reason != null) {
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            Text(
                text = reason,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun HttpsSwitchRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "HTTPS",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun HistoryRow(entry: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val colorScheme = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = BadgerRadius.card) {
        BasicComponent(
            title = entry,
            onClick = onClick,
            endActions = {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "删除",
                        tint = colorScheme.onSurfaceVariantSummary,
                    )
                }
            },
        )
    }
}

@Composable
internal fun LoginEntryButton(resolving: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !resolving,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (resolving) {
                CircularProgressIndicator(size = SPINNER_SIZE, strokeWidth = SPINNER_STROKE)
                Spacer(modifier = Modifier.width(BadgerSpacing.sm))
            }
            Text(if (resolving) "正在尝试连接中" else "下一步")
        }
    }
}
