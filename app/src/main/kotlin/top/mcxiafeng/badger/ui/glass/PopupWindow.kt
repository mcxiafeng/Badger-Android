package top.mcxiafeng.badger.ui.glass

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Keep anchored menus as popups and modal panels as dialogs; the host owns all transition timing. */
@Composable
internal fun PopupWindow(
    motion: PopupMotionState,
    onDismissRequest: (() -> Unit)?,
    hideImeOnDismiss: Boolean = true,
    dropdown: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dismiss: () -> Unit = { if (motion.visible) onDismissRequest?.invoke() }
    if (dropdown) {
        Popup(popupPositionProvider = FullWindowPopupPosition, onDismissRequest = dismiss,
            properties = PopupProperties(focusable = true, clippingEnabled = false,
                dismissOnClickOutside = false, dismissOnBackPress = motion.visible && onDismissRequest != null)) {
            PopupWindowContent(motion, dismiss, hideImeOnDismiss, content)
        }
    } else {
        Dialog(onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
                dismissOnClickOutside = false, dismissOnBackPress = motion.visible && onDismissRequest != null)) {
            ConfigurePopupDialogWindow()
            PopupWindowContent(motion, dismiss, hideImeOnDismiss, content)
        }
    }
}

private object FullWindowPopupPosition : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset = IntOffset.Zero
}

@Composable
private fun ConfigurePopupDialogWindow() {
    val view = LocalView.current
    val dark = LocalAppDarkTheme.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            window.setWindowAnimations(0)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

@Composable
private fun PopupWindowContent(
    motion: PopupMotionState, dismiss: () -> Unit, hideImeOnDismiss: Boolean, content: @Composable () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(motion.visible, hideImeOnDismiss) { if (!motion.visible && hideImeOnDismiss) keyboard?.hide() }
    val closingGate = if (motion.visible) Modifier else Modifier.blockClosingPopupInput().clearAndSetSemantics { }
    CompositionLocalProvider(LocalDismissState provides dismiss) {
        Box(Modifier.fillMaxSize().then(closingGate)) { content() }
    }
}

/** No second action can run while the last frame is only being retained for its exit. */
private fun Modifier.blockClosingPopupInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

@Composable
internal fun PopupDim(motion: PopupMotionState, enabled: Boolean) {
    if (!enabled) return
    val color = MiuixTheme.colorScheme.windowDimming
    Box(Modifier.fillMaxSize().drawBehind { drawRect(color, alpha = motion.progress.value.coerceIn(0f, 1f)) })
}
