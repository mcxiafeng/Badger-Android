package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 自绘水平分割线（0.5dp，使用主题 dividerLine 颜色）。
 *
 * 用自绘不用 Miuix HorizontalDivider 是因为后者在 Card 内经常渲染不出来。
 * 卡片内行与行之间的视觉分隔统一用此组件（SectionCard / SettingsGroupCard 约定）。
 */
@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine)
    )
}
