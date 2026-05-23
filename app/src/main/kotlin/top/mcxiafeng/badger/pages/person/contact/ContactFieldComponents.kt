package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape
import top.yukonga.miuix.kmp.utils.MiuixIndication
import androidx.compose.ui.graphics.Color
import kotlin.collections.iterator

@Composable
internal fun ContactFieldSection(
    title: String,
    fields: List<ContactFieldDisplay>,
    onClick: (ContactFieldDisplay) -> Unit,
    onLongPress: (ContactFieldDisplay) -> Unit,
) {
    SmallTitle(text = title)
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        // 按 fieldKey 分组，同组多值时编号显示（QQ1、QQ2、手机1、手机2...）
        val grouped = fields.groupBy { it.fieldKey ?: it.valueId }
        for ((_, group) in grouped) {
            if (group.size == 1) {
                val first = group.first()
                LongPressArrowPreference(
                    title = first.fieldName,
                    summary = first.value,
                    onClick = { onClick(first) },
                    onLongClick = { onLongPress(first) },
                )
            } else {
                group.forEachIndexed { index, field ->
                    val numberedName = "${field.fieldName}${index + 1}"
                    LongPressArrowPreference(
                        title = numberedName,
                        summary = field.value,
                        onClick = { onClick(field) },
                        onLongClick = { onLongPress(field) },
                    )
                }
            }
        }
    }
}

/**
 * 支持长按的 ArrowPreference（带 Miuix 点击反馈效果）
 */
@Composable
internal fun LongPressArrowPreference(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: ImageVector? = null,
    showArrow: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = MiuixIndication(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(BasicComponentDefaults.InsideMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (showArrow) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * FloatingToolbar 中的带文字操作按钮
 */
@Composable
fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MiuixTheme.colorScheme.onBackground,
) {
    Column(
        modifier = Modifier
            .clip(miuixShape(12.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = MiuixIndication(),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = tint,
        )
    }
}
