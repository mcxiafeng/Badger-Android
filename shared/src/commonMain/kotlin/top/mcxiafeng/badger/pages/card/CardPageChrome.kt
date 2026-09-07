package top.mcxiafeng.badger.pages.card

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.EllipsisVertical

@Composable
internal fun CardOverflowMenu(
    showOverflowMenu: Boolean,
    onDismissOverflowMenu: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Box {
        IconButton(onClick = { onDismissOverflowMenu() }) {
            Icon(Lucide.EllipsisVertical, contentDescription = "更多")
        }
        OverlayListPopup(
            show = showOverflowMenu,
            alignment = PopupPositionProvider.Align.TopEnd,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            onDismissRequest = onDismissOverflowMenu,
        ) {
            ListPopupColumn {
                DropdownImpl(
                    text = "导出名片夹",
                    optionSize = 2,
                    isSelected = false,
                    index = 0,
                    onSelectedIndexChange = {
                        onDismissOverflowMenu()
                        onExport()
                    },
                )
                DropdownImpl(
                    text = "导入名片夹",
                    optionSize = 2,
                    isSelected = false,
                    index = 1,
                    onSelectedIndexChange = {
                        onDismissOverflowMenu()
                        onImport()
                    },
                )
            }
        }
    }
}
