package top.mcxiafeng.badger.pages.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import top.mcxiafeng.badger.data.sync.LocalImportItem
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AccountSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MiuixTheme.textStyles.subtitle,
        modifier = modifier.semantics { heading() })
}

@Composable
internal fun AccountOperationError(message: String, isImport: Boolean, busy: Boolean,
    retry: () -> Unit, onLogin: () -> Unit) {
    val displayMessage = remember(message) { accountDisplayMessage(message) }
    val requiresLogin = remember(message) { needsAccountLogin(message) }
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(BadgerSpacing.cardPadding)) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
            Text(displayMessage, style = MiuixTheme.textStyles.body2,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Button(onClick = if (requiresLogin) onLogin else retry, enabled = !busy,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth().heightIn(min = BadgerSize.minimumTouchTarget)) {
                Text(when { requiresLogin -> "重新登录"; isImport -> "重新预览"; else -> "重试" })
            }
        }
    }
}

@Composable
internal fun ImportScopeInfo(name: String) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(BadgerSpacing.cardPadding)) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
            AccountSectionTitle("导入目标")
            Text(name, style = MiuixTheme.textStyles.body1)
        }
    }
}

@Composable
internal fun LocalImportRow(item: LocalImportItem, enabled: Boolean, onSelect: (String, String) -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(BadgerSpacing.cardPadding)) {
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
            AccountSectionTitle(item.title)
            Text(item.summary, style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            item.options.forEach { option ->
                AccountSelectionRow(option.label, item.selection == option.key, enabled) { onSelect(item.key, option.key) }
            }
        }
    }
}

@Composable
internal fun AccountSelectionRow(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = BadgerSize.minimumTouchTarget)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .padding(vertical = BadgerSpacing.xs), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        RadioButton(selected = selected, onClick = null, enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = MiuixTheme.colorScheme.primary,
                unselectedColor = MiuixTheme.colorScheme.onBackgroundVariant))
        Text(label, style = MiuixTheme.textStyles.body1, modifier = Modifier.weight(1f))
    }
}
