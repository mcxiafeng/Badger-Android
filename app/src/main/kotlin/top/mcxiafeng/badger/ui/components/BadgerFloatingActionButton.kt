package top.mcxiafeng.badger.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主题强调色浮动按钮。
 *
 * Miuix 原生 [FloatingActionButton] 的图标默认跟随 `onSurface`，强调色偏深时图标与底色
 * 对比不足导致不可见。此处按容器底色亮度自适应选择图标前景色：深底用浅色图标、浅底用
 * 深色图标，保证任意用户强调色下图标可见度。
 *
 * @param onClick The callback when the [BadgerFloatingActionButton] is clicked.
 * @param modifier The modifier to be applied to the [BadgerFloatingActionButton].
 * @param containerColor The FAB background color; defaults to the theme accent.
 * @param icon The icon to display.
 * @param contentDescription Accessibility description for the icon.
 */
@Composable
fun BadgerFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MiuixTheme.colorScheme.primary,
    icon: ImageVector,
    contentDescription: String? = null,
) {
    val iconTint = remember(containerColor) { contentColorFor(containerColor) }
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        content = {
            Icon(icon, contentDescription, tint = iconTint)
        },
    )
}
