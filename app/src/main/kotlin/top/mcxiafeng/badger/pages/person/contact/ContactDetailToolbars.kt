package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.LaunchAction
import top.mcxiafeng.badger.ocr.buildLaunchAction
import top.mcxiafeng.badger.ui.components.LaunchActionButtons
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 长按字段后，在详情页内提供操作，不再重复打开只读详情弹窗。 */
@Composable
internal fun ContactDetailFloatingToolbars(
    showFieldToolbar: Boolean,
    selectedField: ContactFieldDisplay?,
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
    onDismissActions: () -> Unit = {},
) {
    if (showFieldToolbar && selectedField != null) {
        ContactDetailActionToolbar(
            fieldKey = selectedField.fieldKey, value = selectedField.value,
            onCopy = onFieldCopy, onEdit = onFieldEdit,
            onSync = onFieldSync, onDelete = onFieldDelete,
            onDismiss = onDismissActions,
        )
    }
    if (showPlatformToolbar && selectedPlatform != null) {
        val (fieldKey, entry) = selectedPlatform
        ContactDetailActionToolbar(
            fieldKey = fieldKey, value = entry.value.orEmpty(), jumpLink = entry.jumpLink,
            allowSync = entry.jumpLink.isNotBlank(),
            onCopy = onPlatformCopy, onEdit = onPlatformEdit,
            onSync = onPlatformSync, onDelete = onPlatformDelete,
            onDismiss = onDismissActions,
        )
    }
}

@Composable
private fun ContactDetailActionToolbar(
    fieldKey: String?,
    value: String,
    jumpLink: String = "",
    allowSync: Boolean = true,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val launchAction = remember(fieldKey, value, jumpLink) {
        fieldKey?.let { buildLaunchAction(it, value, jumpLink) } ?: LaunchAction.None
    }
    val canSync = remember(fieldKey, allowSync) {
        val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
        allowSync && contactType?.let { PlatformAdapterRegistry.getAdapter(it)?.canSync } == true
    }
    FloatingToolbar(cornerRadius = 16.dp) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                ToolbarAction(Icons.Default.ContentCopy, "复制", onCopy)
                ToolbarAction(Icons.Default.Edit, "编辑", onEdit)
                if (canSync) ToolbarAction(Icons.Default.Person, "同步信息", onSync)
                ToolbarAction(Icons.Default.Delete, "删除", onDelete, MiuixTheme.colorScheme.error)
            }
            if (launchAction != LaunchAction.None) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    LaunchActionButtons(launchAction = launchAction, onDismiss = onDismiss)
                }
            }
        }
    }
}
