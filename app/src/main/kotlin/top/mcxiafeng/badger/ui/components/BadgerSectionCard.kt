package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一分组卡片组件
 *
 * 基于 miuix [Card] 封装，提供一致的分组卡片样式：
 * - 可选标题（显示在卡片外部上方）
 * - 卡片内容区域（通过 [content] lambda 自定义）
 * - 统一的内边距和间距
 *
 * 适用于设置分组、信息分组等场景。
 *
 * @param title 可选标题（显示在卡片外部上方，null 则不显示）
 * @param insideMargin 卡片内部边距（默认 0dp，由内容自行控制）
 * @param content 卡片内容
 * @param modifier Modifier
 */
@Composable
fun BadgerSectionCard(
    title: String? = null,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(
                    start = BadgerSpacing.lg,
                    bottom = BadgerSpacing.xs,
                ),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = insideMargin,
            content = content,
        )
    }
}

/**
 * 带标题的分组卡片（简化版）
 *
 * 当内容是多个列表项时，使用此版本自动添加统一内边距。
 *
 * @param title 分组标题
 * @param modifier Modifier
 * @param content 卡片内容
 */
@Composable
fun BadgerTitledCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BadgerSectionCard(
        title = title,
        modifier = modifier,
        content = content,
    )
}
