package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.glass.FloatingToolbar
import top.mcxiafeng.badger.ui.glass.OverlayListPopup
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 长按字段后，在详情页内提供操作，不再重复打开只读详情弹窗。
 *  平台信息的长按操作已改为行内 ListPopup（见 [PlatformInfoRow]），不再使用悬浮工具栏。 */
@Composable
internal fun ContactDetailFloatingToolbars(
    showFieldToolbar: Boolean,
    selectedField: ContactFieldDisplay?,
    onFieldCopy: () -> Unit,
    onFieldEdit: () -> Unit,
    onFieldSync: () -> Unit,
    onFieldDelete: () -> Unit,
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
    FloatingToolbar(cornerRadius = BadgerRadius.surface) {
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

/**
 * 社交平台信息行：长按弹出 ListPopup（复制 / 编辑 / 同步信息 / 删除），
 * 替代原先长按后浮在页面底部的悬浮工具栏，操作菜单锚定在本行右下，
 * 不再占用页面底部整排按钮。
 *
 * 单击仍走 [onClick]（直接跳转平台），长按才进入操作菜单；
 * 菜单只保留复制/编辑/同步/删除，跳转由单击完成，避免与单击语义重复。
 */
@Composable
internal fun PlatformInfoRow(
    fieldKey: String,
    entry: PlatformEntry,
    displayName: String,
    summary: String,
    selectedPlatformKey: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissRequest: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit,
) {
    val allowSync = entry.jumpLink.isNotBlank()
    val canSync = remember(fieldKey, allowSync) {
        val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
        allowSync && contactType?.let { PlatformAdapterRegistry.getAdapter(it)?.canSync } == true
    }
    val labels = remember(canSync) {
        buildList {
            add("复制")
            add("编辑")
            if (canSync) add("同步信息")
            add("删除")
        }
    }
    Box {
        LongPressArrowPreference(
            title = displayName,
            summary = summary,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        OverlayListPopup(
            show = selectedPlatformKey == fieldKey,
            alignment = PopupPositionProvider.Align.TopEnd,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            onDismissRequest = onDismissRequest,
        ) {
            ListPopupColumn {
                labels.forEachIndexed { index, text ->
                    DropdownImpl(
                        text = text,
                        optionSize = labels.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = { selectedIdx ->
                            when (labels[selectedIdx]) {
                                "复制" -> onCopy()
                                "编辑" -> onEdit()
                                "同步信息" -> onSync()
                                "删除" -> onDelete()
                            }
                        }
                    )
                }
            }
        }
    }
}
