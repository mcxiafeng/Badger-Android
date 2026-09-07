package top.mcxiafeng.badger.pages.person.contact.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import kotlin.collections.iterator
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronRight

@Composable
internal fun ContactFieldSection(
    title: String,
    fields: List<PersonFieldDisplay>,
    onClick: (PersonFieldDisplay) -> Unit,
    onLongPress: (PersonFieldDisplay) -> Unit,
) {
    // [修复防御]: 删除 SmallTitle 灰色分组标题,只保留简洁卡片。title 参数保留
    // 是为了对外不破坏调用方签名(可能有外部引用),实际不再渲染。
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
                style = MiuixTheme.textStyles.headline1,
                color = MiuixTheme.colorScheme.onBackground,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (showArrow) {
            Spacer(modifier = Modifier.width(8.dp))
            val layoutDirection = LocalLayoutDirection.current
            Image(
                modifier = Modifier
                    .size(width = 10.dp, height = 16.dp)
                    .graphicsLayer {
                        scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                    }
                    .align(Alignment.CenterVertically),
                imageVector = Lucide.ChevronRight,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions),
            )
        }
    }
}
