package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.yukonga.miuix.kmp.basic.InputField as MiuixInputField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Preserve Miuix search focus/IME behavior while replacing its fixed capsule background. */
@Composable
internal fun InputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = MiuixTheme.colorScheme
    LaunchedEffect(Unit) { Log.d("InputFieldTester", "Search input uses shared continuous surface; native background=false") }
    MiuixTheme(colors = colors.copy(surfaceContainerHigh = Color.Transparent)) {
        MiuixInputField(query, onQueryChange, onSearch, expanded, onExpandedChange,
            modifier.background(colors.surfaceContainerHigh, BadgerShapes.Surface).clip(BadgerShapes.Surface),
            label, enabled, textStyle, leadingIcon, trailingIcon, interactionSource)
    }
}
