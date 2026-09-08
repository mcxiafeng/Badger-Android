package top.mcxiafeng.badger.pages.settings.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.components.ThinDivider
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置分组小标题（footnote 样式）。
 *
 * 置于 [SettingsGroupCard] 之上，左缩进 lgx(20dp) 与卡片内行文字起点对齐。
 */
@Composable
internal fun SettingsGroupHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = BadgerSpacing.lgx, top = BadgerSpacing.lg, bottom = BadgerSpacing.xs),
    )
}

/**
 * 设置分组卡片：自动在行间插入 [ThinDivider]，调用方只管按顺序给行。
 *
 * 取代原先各页手写 `Card + 逐行 ArrowPreference + 手插分隔`的样板。
 * 卡片 [insideMargin] = 0，行内边距由 [ArrowPreference] / [BasicComponent] 自带。
 *
 * @param rows 该组从上到下的行，按调用顺序渲染，相邻两行之间自动插入 [ThinDivider]
 */
@Composable
internal fun SettingsGroupCard(
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = BadgerRadius.card,
        insideMargin = PaddingValues(0.dp),
    ) {
        rows.forEachIndexed { index, row ->
            row()
            if (index != rows.lastIndex) {
                ThinDivider(modifier = Modifier.padding(start = BadgerSpacing.lg))
            }
        }
    }
}
