package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.rememberListPopupLayoutInfo
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val LIST_POPUP_LOG_TAG = "AnimatedListPopupTester"
private const val MENU_COLLAPSED_SCALE = 0.85f

/** Native-signature adapter; placement and list scrolling still belong to the supplied Miuix APIs. */
@Composable
internal fun AnimatedListPopup(
    show: Boolean,
    popupModifier: Modifier = Modifier,
    popupPositionProvider: PopupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    maxHeight: Dp? = null,
    minWidth: Dp = ListPopupDefaults.MinWidth,
    renderInRootScaffold: Boolean = true,
    content: @Composable () -> Unit,
) {
    var anchorOnScreen by remember { mutableStateOf<IntRect?>(null) }
    // Keep this at the caller: a retained window is a different coordinate space from its anchor.
    Spacer(Modifier.size(0.dp).onGloballyPositioned { coordinates ->
        val parent = coordinates.parentLayoutCoordinates ?: return@onGloballyPositioned
        if (!parent.isAttached) return@onGloballyPositioned
        val origin = parent.localToScreen(Offset.Zero)
        if (!origin.x.isFinite() || !origin.y.isFinite()) return@onGloballyPositioned
        val bounds = IntRect(origin.round(), parent.size)
        if (anchorOnScreen != bounds) {
            anchorOnScreen = bounds
            Log.d(LIST_POPUP_LOG_TAG, "Menu anchor recorded in screen coordinates")
        }
    })
    LaunchedEffect(show, renderInRootScaffold) {
        Log.d(LIST_POPUP_LOG_TAG, "List popup requested; show=$show; rootScaffold=$renderInRootScaffold; nativeWindow=true")
    }
    val anchor = anchorOnScreen ?: return
    RetainedPopup(show, onDismissFinished) { motion ->
        PopupWindow(motion, onDismissRequest, hideImeOnDismiss = false, dropdown = true) {
            // Modifier.composed and the items must see the same transparent host theme.
            val colors = MiuixTheme.colorScheme
            MiuixTheme(colors = colors.copy(surfaceContainer = Color.Transparent)) {
                PopupDim(motion, enableWindowDim)
                AnimatedListPopupLayout(motion, anchor, popupModifier, popupPositionProvider,
                    alignment, onDismissRequest, maxHeight, minWidth, content)
            }
        }
    }
}

private data class ListPopupRootOrigin(val screen: IntOffset, val window: IntOffset)

/** The dialog and the source Activity can have different screen origins (including multi-window). */
internal fun listPopupAnchorInWindow(
    anchorOnScreen: IntRect,
    rootOnScreen: IntOffset,
    rootInWindow: IntOffset,
): IntRect = anchorOnScreen.translate(rootInWindow - rootOnScreen)

@Composable
private fun AnimatedListPopupLayout(
    motion: PopupMotionState,
    anchorOnScreen: IntRect,
    popupModifier: Modifier,
    positionProvider: PopupPositionProvider,
    alignment: PopupPositionProvider.Align,
    onDismissRequest: (() -> Unit)?,
    maxHeight: Dp?,
    minWidth: Dp,
    content: @Composable () -> Unit,
) {
    var rootOrigin by remember { mutableStateOf<ListPopupRootOrigin?>(null) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val origin = rootOrigin
    val anchor = origin?.let { listPopupAnchorInWindow(anchorOnScreen, it.screen, it.window) } ?: IntRect.Zero
    val info = rememberListPopupLayoutInfo(alignment, positionProvider, anchor, menuSize)
    val dismiss = {
        if (motion.visible) {
            Log.d(LIST_POPUP_LOG_TAG, "Menu dismissal requested")
            onDismissRequest?.invoke()
        }
    }
    Box(popupModifier.fillMaxSize().onGloballyPositioned { coordinates ->
        val screen = coordinates.localToScreen(Offset.Zero)
        if (screen.x.isFinite() && screen.y.isFinite()) {
            val next = ListPopupRootOrigin(screen.round(), coordinates.positionInWindow().round())
            if (rootOrigin != next) {
                rootOrigin = next
                Log.d(LIST_POPUP_LOG_TAG, "Popup root origin recorded; anchor converted between windows")
            }
        }
    }.pointerInput(onDismissRequest, motion) { detectTapGestures { dismiss() } }) {
        if (origin != null) {
            Box(Modifier.layout { measurable, constraints ->
                val floor = ListPopupDefaults.MinPopupHeight.roundToPx().coerceAtMost(constraints.maxHeight)
                val available = maxHeight?.roundToPx()
                    ?: (info.windowBounds.height - info.popupMargin.top - info.popupMargin.bottom)
                val placeable = measurable.measure(constraints.copy(
                    minWidth = minWidth.roundToPx().coerceIn(0, constraints.maxWidth),
                    minHeight = floor,
                    maxHeight = available.coerceIn(floor, constraints.maxHeight),
                ))
                val position = positionProvider.calculatePosition(anchor, info.windowBounds, layoutDirection,
                    IntSize(placeable.width, placeable.height), info.popupMargin, alignment) - origin.window
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(position) }
            }, propagateMinConstraints = true) {
                AnimatedListPopupSurface(motion, info.localTransformOrigin, { menuSize = it }, dismiss, content)
            }
        }
    }
}

@Composable
private fun AnimatedListPopupSurface(
    motion: PopupMotionState,
    origin: TransformOrigin,
    onSizeChanged: (IntSize) -> Unit,
    dismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.onSizeChanged {
        onSizeChanged(it)
        Log.d(LIST_POPUP_LOG_TAG, "Menu measured; widthPx=${it.width}; heightPx=${it.height}; unclippedShadow=true")
    }.graphicsLayer {
        val progress = motion.progress.value
        val scale = MENU_COLLAPSED_SCALE + (1f - MENU_COLLAPSED_SCALE) * progress
        scaleX = scale
        scaleY = scale
        alpha = progress.coerceIn(0f, 1f)
        transformOrigin = origin
        clip = false
    }.pointerInput(Unit) { detectTapGestures { /* Interior blank space is not outside. */ } },
        propagateMinConstraints = true) {
        CompositionLocalProvider(LocalDismissState provides dismiss, content = content)
    }
}
