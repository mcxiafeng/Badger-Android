package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.AppearanceAccent
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "AppearanceSettingsTester"
private val COLOR_SWATCH_SIZE = 22.dp

/** 纯 UI：外观状态与写入回调由宿主 ViewModel 提供；深浅模式由「我的」页顶栏按钮切换。 */
@Composable
internal fun AppearanceSettings(
    state: AppearanceUiState,
    onAccentChange: (AppearanceAccent) -> Unit,
) {
    var showAccentDialog by rememberSaveable { mutableStateOf(false) }
    Card(insideMargin = PaddingValues()) {
        ArrowPreference(title = "强调色", startAction = { AppearanceIcon(Icons.Filled.ColorLens) }, onClick = {
            Log.d(TAG, "打开强调色选项")
            showAccentDialog = true
        })
        state.errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(BadgerSpacing.md),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
    if (showAccentDialog) {
        AppearanceAccentDialog(state.appearance.accent, onAccentChange) {
            Log.d(TAG, "关闭强调色选项")
            showAccentDialog = false
        }
    }
}

@Composable
private fun AppearanceAccentDialog(
    current: AppearanceAccent,
    onAccentChange: (AppearanceAccent) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        Log.d(TAG, "外观选项：强调色")
    }
    WindowDialog(show = true, title = "强调色", onDismissRequest = onDismiss) {
        AppearanceAccent.entries.forEach { accent ->
            AppearanceOption(accent.label, current == accent, accent) {
                Log.d(TAG, "选择强调色：$accent")
                onDismiss()
                onAccentChange(accent)
            }
        }
    }
}

@Composable
private fun AppearanceIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.padding(end = BadgerSpacing.md),
        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}

@Composable
private fun AppearanceOption(
    title: String,
    selected: Boolean,
    accent: AppearanceAccent? = null,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        modifier = Modifier.semantics { this.selected = selected },
        role = Role.RadioButton,
        startAction = { if (accent != null) AppearanceSwatch(accent) },
        endActions = {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "已选择", tint = MiuixTheme.colorScheme.primary)
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun AppearanceSwatch(accent: AppearanceAccent) {
    val dark = when (MiuixTheme.colorSchemeMode) {
        ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
        ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
        else -> isSystemInDarkTheme()
    }
    val color = remember(accent, dark) { Color(if (dark) accent.darkArgb else accent.lightArgb) }
    Box(Modifier.padding(end = BadgerSpacing.md).size(COLOR_SWATCH_SIZE).background(color, CircleShape))
}
