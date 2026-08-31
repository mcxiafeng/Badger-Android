package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.data.PersonFieldDisplay
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * ContactDetail field/platform action toolbar.
 *
 * The coordinator owns state and business callbacks; this file owns the toolbar UI
 * and the rules for which actions are visible for the selected item.
 */
@Composable
internal fun ContactDetailFloatingToolbars(
    showFieldToolbar: Boolean,
    selectedField: PersonFieldDisplay?,
    onFieldCopy: () -> Unit,
    onFieldEdit: () -> Unit,
    onFieldSync: () -> Unit,
    onFieldDelete: () -> Unit,
    showPlatformToolbar: Boolean,
    selectedPlatform: Pair<String, PlatformEntry>?,
    onPlatformCopy: () -> Unit,
    onPlatformEdit: () -> Unit,
    onPlatformSync: () -> Unit,
    onPlatformDelete: () -> Unit,
) {
    if (showFieldToolbar && selectedField != null) {
        FloatingToolbar(cornerRadius = BadgerRadius.lg) {
            Row(
                modifier = Modifier.padding(horizontal = BadgerSpacing.xs, vertical = BadgerSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.xxs),
            ) {
                ToolbarAction(Icons.Default.ContentCopy, "复制", onFieldCopy)
                ToolbarAction(Icons.Default.Edit, "编辑", onFieldEdit)
                selectedField.fieldKey
                    ?.takeIf { it.kindCanSync }
                    ?.let { ToolbarAction(Icons.Default.Person, "同步信息", onFieldSync) }
                ToolbarAction(
                    icon = Icons.Default.Delete,
                    label = "删除",
                    tint = MiuixTheme.colorScheme.error,
                    onClick = onFieldDelete,
                )
            }
        }
    }

    if (showPlatformToolbar && selectedPlatform != null) {
        val (fieldKey, entry) = selectedPlatform
        FloatingToolbar(cornerRadius = BadgerRadius.lg) {
            Row(
                modifier = Modifier.padding(horizontal = BadgerSpacing.xs, vertical = BadgerSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.xxs),
            ) {
                ToolbarAction(Icons.Default.ContentCopy, "复制", onPlatformCopy)
                ToolbarAction(Icons.Default.Edit, "编辑", onPlatformEdit)
                if (entry.jumpLink.isNotBlank() && fieldKey.kindCanSync) {
                    ToolbarAction(Icons.Default.Person, "同步信息", onPlatformSync)
                }
                ToolbarAction(
                    icon = Icons.Default.Delete,
                    label = "删除",
                    tint = MiuixTheme.colorScheme.error,
                    onClick = onPlatformDelete,
                )
            }
        }
    }
}
