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
