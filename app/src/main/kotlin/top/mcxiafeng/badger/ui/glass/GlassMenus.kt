package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.G2_CORNER_MIDPOINT
import top.yukonga.miuix.kmp.basic.FloatingToolbar as MiuixFloatingToolbar
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayListPopup as MiuixOverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.getRoundedCorner
import top.yukonga.miuix.kmp.window.WindowDialog as MiuixWindowDialog

private val DialogHeadingGap = 12.dp
private val NativeDialogMinimumRadius = 32.dp
private val MenuShadowElevation = 12.dp
private const val NATIVE_DIALOG_CORNER_EXTENSION = 1.1f

@Composable
internal fun OverlayListPopup(
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
    val style = LocalGlassStyle.current
    val backdrop = LocalMenuBackdrop.current ?: LocalGlassBackdrop.current ?: LocalPageBackdrop.current?.value
    val colors = MiuixTheme.colorScheme
    val underlay = LocalGlassUnderlay.current.let { if (it == Color.Unspecified) colors.surface else it }
    val tint = colors.primary.copy(alpha = BUTTON_TINT_ALPHA)
    val dim = enableWindowDim && !style.enabled
    MiuixOverlayListPopup(show, popupModifier, popupPositionProvider, alignment, dim,
        onDismissRequest, onDismissFinished, maxHeight, minWidth, renderInRootScaffold) {
        MiuixTheme(colors = colors.copy(surfaceContainer = Color.Transparent)) {
            CompositionLocalProvider(LocalGlassStyle provides style) {
                LaunchedEffect(tint, style) {
                    Log.d(GLASS_LOG_TAG, "List menu; glass=${style.enabled}; fillAlpha=${tint.alpha}; radiusDp=${GlassMenuRadius.value}; cornerProfile=G2; opaqueHost=false; opaqueItems=false")
                }
                val shape = remember { BadgerShapes.rounded(GlassMenuRadius) }
                val background = if (style.enabled) Modifier.glassSurface(GlassMenuRadius, tint, underlay, backdrop)
                    else Modifier.background(colors.surfaceContainer, shape)
                Box(background.clip(shape), propagateMinConstraints = true) { content() }
            }
        }
    }
}

/** Keep the native dialog's immutable 32dp+ clipping outside the visible application surface. */
internal fun nativeDialogClipGuard(systemRadius: Dp): Dp =
    maxOf(NativeDialogMinimumRadius, systemRadius) * NATIVE_DIALOG_CORNER_EXTENSION * G2_CORNER_MIDPOINT +
        BadgerSize.dialogClipSafetyPadding

private fun Modifier.dismissClipGuard(guard: Dp, onDismiss: (() -> Unit)?): Modifier {
    if (onDismiss == null) return this
    return pointerInput(guard, onDismiss) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val inset = guard.toPx()
            val outside = down.position.x < inset || down.position.x > size.width - inset ||
                down.position.y < inset || down.position.y > size.height - inset
            if (outside) {
                down.consume()
                var released = false
                var moved = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    event.changes.forEach { it.consume() }
                    if (!change.pressed) { released = true; break }
                }
                if (released && !moved) {
                    Log.d(GLASS_LOG_TAG, "Dismissed dialog from transparent clip guard")
                    onDismiss()
                }
            }
        }
    }
}

@Composable
internal fun WindowDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color = DialogDefaults.titleColor(),
    summary: String? = null,
    summaryColor: Color = DialogDefaults.summaryColor(),
    backgroundColor: Color = DialogDefaults.backgroundColor(),
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = DialogDefaults.outsideMargin,
    insideMargin: DpSize = DialogDefaults.insideMargin,
    defaultWindowInsetsPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    val style = LocalGlassStyle.current
    val colors = MiuixTheme.colorScheme
    val backdrop = LocalMenuBackdrop.current ?: LocalGlassBackdrop.current ?: LocalPageBackdrop.current?.value
    val guard = nativeDialogClipGuard(getRoundedCorner())
    val outside = DpSize((outsideMargin.width - guard).coerceAtLeast(0.dp), (outsideMargin.height - guard).coerceAtLeast(0.dp))
    MiuixWindowDialog(show = show, modifier = modifier.dismissClipGuard(guard, onDismissRequest), backgroundColor = Color.Transparent,
        enableWindowDim = enableWindowDim && !style.enabled, onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished, outsideMargin = outside, insideMargin = DpSize(guard, guard),
        defaultWindowInsetsPadding = defaultWindowInsetsPadding) {
        MiuixTheme(colors = colors) {
            val surfaceShape = remember { BadgerShapes.rounded(BadgerRadius.menu) }
            val shadow = Modifier.shadow(MenuShadowElevation, surfaceShape, clip = false)
            val surface = if (style.enabled) shadow.then(
                Modifier.glassSurface(BadgerRadius.menu, style.menuTint, backgroundColor, backdrop))
                else shadow.background(backgroundColor, surfaceShape)
            LaunchedEffect(guard, style) {
                Log.d(GLASS_LOG_TAG, "Continuous dialog; radiusDp=${BadgerRadius.menu.value}; nativeClipGuardDp=${guard.value}; shadowDp=${MenuShadowElevation.value}; glass=${style.enabled}")
            }
            Column(surface.clip(surfaceShape).padding(horizontal = insideMargin.width, vertical = insideMargin.height)) {
                if (title != null) DialogHeading(title, titleColor, isTitle = true)
                if (summary != null) DialogHeading(summary, summaryColor, isTitle = false)
                CompositionLocalProvider(LocalGlassUnderlay provides backgroundColor, LocalGlassBackdrop provides null,
                    LocalGlassStyle provides style, content = content)
            }
        }
    }
}

@Composable
private fun DialogHeading(text: String, color: Color, isTitle: Boolean) {
    val styles = MiuixTheme.textStyles
    Text(text, Modifier.fillMaxWidth().padding(bottom = DialogHeadingGap), color = color,
        style = if (isTitle) styles.title4 else styles.body1,
        fontWeight = if (isTitle) FontWeight.Medium else null, textAlign = TextAlign.Center)
}

@Composable
internal fun FloatingToolbar(
    modifier: Modifier = Modifier,
    color: Color = FloatingToolbarDefaults.defaultColor(),
    cornerRadius: Dp = BadgerRadius.surface,
    outSidePadding: PaddingValues = FloatingToolbarDefaults.OutSidePadding,
    shadowElevation: Dp = 4.dp,
    showDivider: Boolean = false,
    content: @Composable () -> Unit,
) {
    val style = LocalGlassStyle.current
    val shape = remember(cornerRadius) { BadgerShapes.rounded(cornerRadius) }
    val background = if (style.enabled) Modifier.glassSurface(cornerRadius, style.chromeTint, color)
        else Modifier.background(color, shape)
    val divider = if (showDivider) Modifier.border(BadgerSize.floatingToolbarDividerWidth,
        MiuixTheme.colorScheme.dividerLine, shape).padding(BadgerSize.floatingToolbarDividerWidth) else Modifier
    MiuixFloatingToolbar(modifier, Color.Transparent, 0.dp, outSidePadding, 0.dp, false) {
        Box(Modifier.shadow(shadowElevation, shape, clip = false).then(background).clip(shape).then(divider)) {
            CompositionLocalProvider(LocalGlassBackdrop provides null, LocalGlassUnderlay provides color,
                LocalGlassStyle provides style, content = content)
        }
    }
}

@Composable
internal fun Modifier.glassPanel(radius: Dp, background: Color): Modifier {
    val style = LocalGlassStyle.current
    if (!style.enabled) return this
    val backdrop = LocalMenuBackdrop.current ?: LocalGlassBackdrop.current ?: LocalPageBackdrop.current?.value
    return glassSurface(radius, style.menuTint, background, backdrop)
}
