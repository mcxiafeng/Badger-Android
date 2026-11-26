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
import androidx.compose.material.icons.filled.Brightness6
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
import top.mcxiafeng.badger.data.AppearanceMode
import top.mcxiafeng.badger.data.AppearanceState
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "AppearanceSettingsTester"
private val COLOR_SWATCH_SIZE = 22.dp
private enum class AppearanceDialog { Mode, Accent }

/** 纯 UI：外观状态与写入回调由宿主 ViewModel 提供。 */
@Composable
internal fun AppearanceSettings(
    state: AppearanceUiState,
    onModeChange: (AppearanceMode) -> Unit,
    onAccentChange: (AppearanceAccent) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<AppearanceDialog?>(null) }
    AppearanceCard(state, onMode = {
        Log.d(TAG, "打开深浅模式选项")
        dialog = AppearanceDialog.Mode
    }, onAccent = {
        Log.d(TAG, "打开强调色选项")
        dialog = AppearanceDialog.Accent
    })
    val activeDialog = dialog
    if (activeDialog != null) {
        AppearanceOptionsDialog(activeDialog, state.appearance, onModeChange, onAccentChange) {
            Log.d(TAG, "关闭外观选项：$activeDialog")
            dialog = null
        }
    }
}

@Composable
private fun AppearanceCard(state: AppearanceUiState, onMode: () -> Unit, onAccent: () -> Unit) {
    Card(insideMargin = PaddingValues()) {
        ArrowPreference(title = "深浅模式", startAction = { AppearanceIcon(Icons.Filled.Brightness6) }, onClick = onMode)
        ArrowPreference(title = "强调色", startAction = { AppearanceIcon(Icons.Filled.ColorLens) }, onClick = onAccent)
        state.errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(BadgerSpacing.md),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AppearanceOptionsDialog(
    dialog: AppearanceDialog,
    state: AppearanceState,
    onModeChange: (AppearanceMode) -> Unit,
    onAccentChange: (AppearanceAccent) -> Unit,
    onDismiss: () -> Unit,
) {
    val glassEnabled = LocalGlassStyle.current.enabled
    LaunchedEffect(dialog, glassEnabled) {
        Log.d(TAG, "外观选项：$dialog，透明玻璃背景=$glassEnabled")
    }
    WindowDialog(
        show = true,
        title = if (dialog == AppearanceDialog.Mode) "深浅模式" else "强调色",
        // Leave the underlay transparent while the shared wrapper samples and tints the page.
        backgroundColor = if (glassEnabled) Color.Transparent else DialogDefaults.backgroundColor(),
        onDismissRequest = onDismiss,
    ) {
        if (dialog == AppearanceDialog.Mode) {
            AppearanceMode.entries.forEach { mode ->
                AppearanceOption(mode.label, state.mode == mode) {
                    Log.d(TAG, "选择深浅模式：$mode")
                    onDismiss()
                    onModeChange(mode)
                }
            }
        } else {
            AppearanceAccent.entries.forEach { accent ->
                AppearanceOption(accent.label, state.accent == accent, accent) {
                    Log.d(TAG, "选择强调色：$accent")
                    onDismiss()
                    onAccentChange(accent)
                }
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
