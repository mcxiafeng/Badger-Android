package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.designsystem.BadgerAlpha
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import top.mcxiafeng.badger.ui.glass.LocalGlassBackdrop
import top.mcxiafeng.badger.ui.glass.LocalGlassUnderlay
import top.mcxiafeng.badger.ui.glass.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarColors
import top.yukonga.miuix.kmp.basic.SnackbarData
import top.yukonga.miuix.kmp.basic.SnackbarDefaults
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarVisuals
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Retain Miuix's queue, timeouts, accessibility duration and swipe dismissal. */
@Composable
internal fun SnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
    canSwipeToDismiss: Boolean = true,
    content: @Composable (SnackbarData) -> Unit = { Snackbar(it) },
) = MiuixSnackbarHost(state, modifier, canSwipeToDismiss, content)

@Composable
internal fun Snackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = BadgerRadius.surface,
    colors: SnackbarColors = SnackbarDefaults.snackbarColors(),
    insideMargin: PaddingValues = SnackbarDefaults.InsideMargin,
) {
    val shape = remember(cornerRadius) { BadgerShapes.rounded(cornerRadius) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(data) { Log.d("SnackbarTester", "Continuous snackbar surface; radiusDp=${cornerRadius.value}") }
    Row(modifier.semantics { isTraversalGroup = true; liveRegion = LiveRegionMode.Polite }
        .padding(SnackbarDefaults.OuterPadding)
        .dropShadow(shape, Shadow(radius = BadgerSize.snackbarShadowRadius, color = Color.Black, alpha = BadgerAlpha.SNACKBAR_SHADOW_ALPHA))
        .background(colors.containerColor, shape).clip(shape)
        .pointerInput(Unit) { detectTapGestures { } }
        .defaultMinSize(minHeight = BadgerSize.minimumTouchTarget).padding(insideMargin),
        verticalAlignment = Alignment.CenterVertically) {
        Text(data.visuals.message, Modifier.weight(1f), color = colors.contentColor,
            style = MiuixTheme.textStyles.body2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        CompositionLocalProvider(LocalGlassBackdrop provides null, LocalGlassUnderlay provides colors.containerColor) {
            SnackbarActions(data.visuals, colors,
                onAction = { scope.launch { Log.d("SnackbarTester", "Snackbar action clicked"); data.performAction() } },
                onDismiss = { scope.launch { Log.d("SnackbarTester", "Snackbar dismissed"); data.dismiss() } })
        }
    }
}

@Composable
private fun RowScope.SnackbarActions(visuals: SnackbarVisuals, colors: SnackbarColors, onAction: () -> Unit, onDismiss: () -> Unit) {
    val label = visuals.actionLabel
    if (!label.isNullOrEmpty()) {
        TextButton(label, onAction, Modifier.padding(start = BadgerSpacing.md),
            cornerRadius = SnackbarDefaults.ActionCornerRadius, minWidth = BadgerSize.snackbarActionMinimum,
            minHeight = BadgerSize.snackbarActionMinimum, colors = ButtonDefaults.textButtonColorsPrimary(
                color = colors.actionContainerColor, textColor = colors.actionContentColor),
            insideMargin = SnackbarDefaults.ActionInsideMargin, textStyle = TextStyle(fontSize = BadgerSize.snackbarActionTextSize))
    }
    if (visuals.withDismissAction) {
        Icon(Icons.Default.Close, "关闭提示", tint = colors.dismissActionContentColor,
            modifier = Modifier.padding(start = BadgerSpacing.sm).size(BadgerSize.snackbarDismissSize)
                .clickable(indication = null, interactionSource = null, onClick = onDismiss))
    }
}
