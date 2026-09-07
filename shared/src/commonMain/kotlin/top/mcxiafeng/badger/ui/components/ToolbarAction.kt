package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.utils.MiuixIndication

/**
 * FloatingToolbar 中的带文字操作按钮（U08 自 `ContactFieldComponents.kt` 下沉）。
 *
 * **使用场景**：联系人详情页、名片夹页、联系人列表页的浮动工具栏——
 * 图标 + 文字竖排，clip 圆角 + MiuixIndication 点击反馈。
 *
 * @param icon 图标
 * @param label 文字标签（同时作为 contentDescription）
 * @param onClick 点击回调
 * @param tint 图标与文字颜色，默认取 onBackground
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
