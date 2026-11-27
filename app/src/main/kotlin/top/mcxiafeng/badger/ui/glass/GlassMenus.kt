package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
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
/** Callers pass the zero-margin context provider; the app always keeps its 8dp gap to the anchor. */
private val FlatMenuPositionProvider = ListPopupDefaults.dropdownPositionProvider(
    verticalMargin = BadgerSpacing.menuAnchorGap,
)
private const val GLASS_MENUS_LOG_TAG = "GlassMenusTester"
private val NativeDialogMinimumRadius = 32.dp
private val MenuShadowElevation = BadgerSize.menuShadowElevation
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
    if (style.flatMode) {
        // Miuix's own panel, scrim and reveal; the host theme lifts the panel onto the elevated container,
        // and the 8dp anchor gap is kept regardless of the zero-margin provider callers pass.
        Log.d(GLASS_MENUS_LOG_TAG, "Flat list menu; native=MiuixOverlayListPopup; show=$show; dim=$enableWindowDim; anchorGapDp=${BadgerSpacing.menuAnchorGap.value}; alignment=$alignment; rootScaffold=$renderInRootScaffold")
        MiuixOverlayListPopup(
            show = show, popupModifier = popupModifier, popupPositionProvider = FlatMenuPositionProvider,
            alignment = alignment, enableWindowDim = enableWindowDim, onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished, maxHeight = maxHeight, minWidth = minWidth,
            renderInRootScaffold = renderInRootScaffold, content = content,
        )
        return
    }
    val backdrop = LocalMenuBackdrop.current ?: LocalGlassBackdrop.current ?: LocalPageBackdrop.current?.value
    val colors = MiuixTheme.colorScheme
    val underlay = LocalGlassUnderlay.current.let { if (it == Color.Unspecified) colors.surface else it }
    val tint = colors.primary.copy(alpha = BUTTON_TINT_ALPHA)
    val dim = enableWindowDim && !style.enabled
    val shadowOutsetPx = with(LocalDensity.current) { BadgerSize.menuShadowOutset.roundToPx() }
    val position = remember(popupPositionProvider, shadowOutsetPx) {
        if (popupPositionProvider === ListPopupDefaults.ContextMenuPositionProvider ||
            popupPositionProvider === ListPopupDefaults.DropdownPositionProvider) MenuPopupPositionProvider(shadowOutsetPx)
        else MenuPopupPositionProvider(shadowOutsetPx, popupPositionProvider)
    }
    AnimatedListPopup(show, popupModifier, position, alignment, dim,
        onDismissRequest, onDismissFinished, maxHeight, minWidth, renderInRootScaffold) {
        MiuixTheme(colors = colors.copy(surfaceContainer = Color.Transparent)) {
            CompositionLocalProvider(LocalGlassStyle provides style) {
                LaunchedEffect(tint, style) {
                    Log.d(GLASS_LOG_TAG, "List menu; glass=${style.enabled}; material=${style.materialEnabled}; fillAlpha=${if (style.materialEnabled) tint.alpha else 1f}; radiusDp=${GlassMenuRadius.value}; cornerProfile=G2; opaqueHost=false; opaqueItems=false; shadowDp=${MenuShadowElevation.value}; shadowGuardDp=${BadgerSize.menuShadowOutset.value}; anchorGapDp=${BadgerSpacing.menuAnchorGap.value}")
                }
                val shape = remember { BadgerShapes.rounded(GlassMenuRadius) }
                val background = if (style.enabled) Modifier.glassSurface(GlassMenuRadius, tint, underlay, backdrop)
                    else Modifier.background(colors.surfaceContainer, shape)
                Box(Modifier.padding(BadgerSize.menuShadowOutset), propagateMinConstraints = true) {
                    Box(Modifier.menuShadow(shape).then(background).clip(shape), propagateMinConstraints = true) { content() }
                }
            }
        }
    }
}

/** Keep the previous visible margin and shadow clearance when removing the native clipping host. */
internal fun nativeDialogClipGuard(systemRadius: Dp): Dp =
    maxOf(NativeDialogMinimumRadius, systemRadius) * NATIVE_DIALOG_CORNER_EXTENSION * G2_CORNER_MIDPOINT +
        BadgerSize.dialogClipSafetyPadding

@Composable
internal fun WindowDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color = DialogDefaults.titleColor(),
    summary: String? = null,
    summaryColor: Color = DialogDefaults.summaryColor(),
    backgroundColor: Color = Color.Unspecified,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = DialogDefaults.outsideMargin,
    insideMargin: DpSize = DialogDefaults.insideMargin,
    defaultWindowInsetsPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    val style = LocalGlassStyle.current
    // The window background matches the page color, so panels lift off the page on the elevated container.
    val panelBackground = if (backgroundColor == Color.Unspecified) MiuixTheme.colorScheme.surfaceContainerHigh
        else backgroundColor
    if (style.flatMode) {
        // Miuix draws the surface, radius, dim and slide; the retained host keeps the app's exit hand-off.
        Log.d(GLASS_MENUS_LOG_TAG, "Flat dialog; native=MiuixWindowDialog; show=$show; title=${title != null}; dim=$enableWindowDim; elevatedBody=true; retainedExit=true")
        RetainedPopup(show = show) { motion ->
            MiuixWindowDialog(
                show = motion.visible, modifier = modifier, title = title, titleColor = titleColor,
                summary = summary, summaryColor = summaryColor, backgroundColor = panelBackground,
                enableWindowDim = enableWindowDim, onDismissRequest = onDismissRequest,
                onDismissFinished = onDismissFinished, outsideMargin = outsideMargin,
                insideMargin = insideMargin, defaultWindowInsetsPadding = defaultWindowInsetsPadding,
                content = content,
            )
        }
        return
    }
    val spec = WindowDialogSpec(title, titleColor, summary, summaryColor, panelBackground,
        outsideMargin, insideMargin, defaultWindowInsetsPadding, enableWindowDim, nativeDialogClipGuard(getRoundedCorner()))
    RetainedPopup(show, onDismissFinished) { motion ->
        PopupWindow(motion, onDismissRequest) {
            WindowDialogPlacement(motion, spec, modifier, onDismissRequest) {
                WindowDialogSurface(spec, content)
            }
        }
    }
}

@Composable
private fun WindowDialogSurface(spec: WindowDialogSpec, content: @Composable () -> Unit) {
    val style = LocalGlassStyle.current
    val backdrop = LocalMenuBackdrop.current ?: LocalGlassBackdrop.current ?: LocalPageBackdrop.current?.value
    val shape = remember { BadgerShapes.rounded(BadgerRadius.menu) }
    val shadow = Modifier.menuShadow(shape)
    val surface = shadow.then(Modifier.modalPopupSurface(BadgerRadius.menu, backdrop))
    LaunchedEffect(spec.guard, style, spec.background, backdrop) {
        Log.d(GLASS_LOG_TAG, "Continuous dialog; radiusDp=${BadgerRadius.menu.value}; nativeClipGuardDp=${spec.guard.value}; shadowDp=${MenuShadowElevation.value}; glass=${style.enabled}; material=${style.materialEnabled}; underlayAlpha=${spec.background.alpha}; backdropAvailable=${backdrop != null}; retainedExit=true; transitionMs=${BadgerMotion.DURATION_TOUCH_FEEDBACK}")
    }
    Column(surface.clip(shape).padding(horizontal = spec.inside.width, vertical = spec.inside.height)) {
        if (spec.title != null) DialogHeading(spec.title, spec.titleColor, isTitle = true)
        if (spec.summary != null) DialogHeading(spec.summary, spec.summaryColor, isTitle = false)
        CompositionLocalProvider(LocalGlassUnderlay provides spec.background, LocalGlassBackdrop provides null,
            LocalGlassStyle provides style, content = content)
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
    // The toolbar is a long multi-action capsule like the grouped toolbar: the soft lens keeps
    // the mid-band quiet and the dispersion low, or the wide pill stripes over patterned content.
    val background = if (style.enabled) Modifier.glassSurface(cornerRadius, style.chromeTint, color,
        opticalParameters = { GlassOpticalParameters.Bar })
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

