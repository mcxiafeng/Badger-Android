package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OverlayDropdownPreference(
    items: List<String>, selectedIndex: Int, title: String, enabled: Boolean, onSelectedIndexChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BasicComponent(title = title, enabled = enabled && items.isNotEmpty(), onClick = { expanded = true }, endActions = {
            Text(items.getOrNull(selectedIndex).orEmpty(), style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        })
        OverlayListPopup(show = expanded && enabled, alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { expanded = false }) {
            ListPopupColumn {
                items.forEachIndexed { index, label ->
                    DropdownImpl(text = label, optionSize = items.size, index = index, isSelected = index == selectedIndex,
                        onSelectedIndexChange = {
                            expanded = false
                            Log.d(GLASS_LOG_TAG, "Selected glass dropdown option index=$index")
                            onSelectedIndexChange(index)
                        })
                }
            }
        }
    }
}
