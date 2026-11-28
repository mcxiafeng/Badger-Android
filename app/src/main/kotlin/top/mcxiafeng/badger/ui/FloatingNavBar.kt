package top.mcxiafeng.badger.ui

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerNavIndicator
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.designsystem.BadgerTouchFeedback
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect
import top.mcxiafeng.badger.ui.glass.BAR_GLASS_DISPERSION
import top.mcxiafeng.badger.ui.glass.BAR_GLASS_REFRACTION
import top.mcxiafeng.badger.ui.glass.GlassGlowOutset
import top.mcxiafeng.badger.ui.glass.GlassOpticalParameters
import top.mcxiafeng.badger.ui.glass.GlassStyle
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.glassSurface
import top.mcxiafeng.badger.ui.glass.recordGlassBackdrop
import top.mcxiafeng.badger.ui.glass.rememberGlassBackdrop
import top.mcxiafeng.badger.ui.navigation.MainTab
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val TAG = "FloatingNavBarTester"
private const val OUTLINE_ALPHA = 0.14f
private val BarMinHeight = 72.dp
private val TabIconSize = 22.dp

/** Pointer light on the pane: a quiet sheen under the selection capsule, never a full sweep. */
private val BarGlintRadius = 44.dp
private const val BAR_GLINT_ALPHA = 0.22f
private const val GLINT_MIN_ENVELOPE = 0.01f

/** Keep the visible capsule edges aligned with the todo rows' outer padding. */
private val BarEndTrim = 0.dp

/**
 * Pointer light (liquid-glass signature): while the glass state is live, a soft radial sheen
 * rides under the selection capsule and follows it — the capsule itself tracks the buffered
 * pointer, so the light lands where the finger is. It rides the existing 100ms optical
 * envelope, stays clipped inside the bar capsule and never sweeps the whole pane; this is a
 * moving directional light, not the round touch halo the 2026-11-27 motion redo removed.
 */
@Composable
private fun Modifier.barGlint(indicator: FloatingTabIndicator): Modifier {
    val direction = LocalLayoutDirection.current
    if (!LocalGlassStyle.current.materialEnabled) return this
    return drawWithCache {
        val bar = Path()
        val radiusPx = BarGlintRadius.toPx()
        val glint = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = BAR_GLINT_ALPHA), Color.Transparent),
            center = Offset.Zero, radius = radiusPx,
        )
        onDrawBehind {
            val envelope = indicator.opticalProgress.value.coerceIn(0f, 1f)
            if (envelope <= GLINT_MIN_ENVELOPE) return@onDrawBehind
            bar.rewind()
            bar.addG2RoundRect(barBounds(size, BarEndTrim.toPx()))
            val capsule = indicator.bounds(size, direction)
            val focus = Offset((capsule.left + capsule.right) / 2f, (capsule.top + capsule.bottom) / 2f)
            clipPath(bar) {
                translate(focus.x, focus.y) {
                    drawCircle(brush = glint, radius = radiusPx, center = Offset.Zero, alpha = envelope)
                }
            }
        }
    }
}

/** 主页面内容和浮动操作按钮需要预留的高度，系统栏由 Scaffold 避让。 */
val LocalFloatingBarBottomPadding = staticCompositionLocalOf { 0.dp }

/**
 * 页内沉浸模式（联系人多选）对主导航栏的隐藏请求：MainTabs 宿主读取后隐藏悬浮/经典导航栏、
 * 清零 [LocalFloatingBarBottomPadding] 预留并放开底部导航栏 inset；
 * 页面侧在 LaunchedEffect 中写入，且只在对应 Tab 自身可见时生效。
 */
val LocalMainNavBarHidden = staticCompositionLocalOf { mutableStateOf(false) }

internal val FloatingNavBarMinBottomPadding = BarMinHeight + BadgerSpacing.lg + BadgerSpacing.md

@Composable
internal fun FloatingNavBar(
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null,
    indicatorState: FloatingTabIndicator? = null,
) {
    val scope = rememberCoroutineScope()
    val direction = LocalLayoutDirection.current
    val currentPage = pagerState?.currentPage ?: MainTab.MY_CARD.ordinal
    val style = LocalGlassStyle.current
    val animated = style.materialEnabled
    val indicator = indicatorState ?: remember(pagerState, animated) { FloatingTabIndicator(currentPage, scope, effectsEnabled = animated) }
    val backdrop = rememberGlassBackdrop(Color.Transparent)
    LaunchedEffect(animated) {
        Log.d(TAG, "Floating bar barPressFeedback=$animated; positionTracking=true; outline=false; solidAccentAlpha=${BadgerNavIndicator.SOLID_FILL_ALPHA}; sideMarginDp=${BadgerSpacing.xl.value}")
    }
    SideEffect { indicator.settleTo(currentPage) }
    LaunchedEffect(backdrop) {
        Log.d(TAG, "Bar shadow=0; indicator source=barAndIcons; indicatorIdle=black19%; activeFill=0; pressedScale=${BadgerNavIndicator.PRESSED_SCALE}; pointerBufferMs=${BadgerNavIndicator.POINTER_BUFFER_MS}; idleScale=91%; accentClip=indicatorBounds; deformation=none; endpointCenters=idleTabCenters; durationMs=${BadgerMotion.DURATION_INDICATOR}; barOptics=soft(refraction=${BAR_GLASS_REFRACTION},dispersion=${BAR_GLASS_DISPERSION})")
    }
    LaunchedEffect(Unit) {
        Log.d(TAG, "Bar glint; radiusDp=${BarGlintRadius.value}; alpha=$BAR_GLINT_ALPHA; envelope=opticalProgress100ms; follows=selectionCapsule; glassOnly=true")
    }
    val gestures = if (pagerState == null) Modifier else Modifier
        .observeTabPress(pagerState, indicator, direction)
        .selectTabByDrag(pagerState, indicator, direction)
    val feedback = Modifier.observeBarFollow(indicator).graphicsLayer {
        scaleX = if (animated) indicator.barFeedback.scale.value else 1f
        scaleY = scaleX
        translationX = indicator.barFollow.translation.value
    }

    BoxWithConstraints(modifier.padding(start = BadgerSpacing.xl, end = BadgerSpacing.xl)
        .indicatorClearance().fillMaxWidth().then(feedback)) {
        val inset = with(LocalDensity.current) {
            navTrackInsetPx(constraints.maxWidth, ceil(BadgerSize.minimumTouchTarget.toPx()).toInt(),
                BadgerSpacing.xs.roundToPx()).toDp()
        }
        LaunchedEffect(inset) { Log.d(TAG, "Centered tab cluster; trackInsetDp=${inset.value}; barEndTrimDp=${BarEndTrim.value}; iconLabelGapDp=${BadgerNavIndicator.iconLabelGap.value}") }
        // The source includes the bar and its icons; the selection is a separate, later overlay.
        val recording = if (backdrop == null) Modifier else Modifier.recordGlassBackdrop(backdrop)
        FloatingNavTabs(pagerState, indicator, Modifier.then(recording).navBarSurface(BarEndTrim), gestures, scope, inset)
        Box(Modifier.matchParentSize().barGlint(indicator))
        Box(Modifier.matchParentSize().padding(horizontal = inset, vertical = BadgerSpacing.xs).slidingTabIndicator(indicator, backdrop))
    }
}

internal fun navTrackInsetPx(width: Int, minimumTarget: Int, desiredInset: Int): Int =
    minOf(desiredInset, ((width - minimumTarget * MainTab.entries.size) / 2).coerceAtLeast(0))

private fun Modifier.indicatorClearance(): Modifier = layout { measurable, constraints ->
    val minimumTop = BadgerSpacing.md.roundToPx()
    val minimumBottom = BadgerSpacing.lg.roundToPx()
    val content = measurable.measure(constraints.offset(vertical = -minimumTop - minimumBottom))
    val trackHeight = (content.height - BadgerSpacing.xs.roundToPx() * 2).coerceAtLeast(0)
    val largestHeight = trackHeight * BadgerNavIndicator.IDLE_SCALE *
        BadgerNavIndicator.PRESSED_SCALE * BadgerTouchFeedback.PRESSED_SCALE
    val overflow = ceil(((largestHeight - content.height) / 2f).coerceAtLeast(0f) + GlassGlowOutset.toPx()).toInt()
    val top = maxOf(minimumTop, overflow)
    val bottom = maxOf(minimumBottom, overflow)
    layout(content.width, constraints.constrainHeight(content.height + top + bottom)) { content.placeRelative(0, top) }
}

@Composable
private fun FloatingNavTabs(
    pagerState: PagerState?, indicator: FloatingTabIndicator, surface: Modifier,
    gestures: Modifier, scope: CoroutineScope, horizontalInset: Dp,
) {
    val currentPage = pagerState?.currentPage ?: MainTab.MY_CARD.ordinal
    val animated = LocalGlassStyle.current.materialEnabled
    // The color target rounds the sliding indicator's position, so the accent arrives together
    // with the capsule instead of waiting for the pager to settle.
    val colorPage by remember(indicator) {
        derivedStateOf { indicator.position.roundToInt().coerceIn(MainTab.entries.indices) }
    }
    LaunchedEffect(colorPage) {
        Log.d(TAG, "Tab accent retarget; page=$colorPage; colorMs=${BadgerMotion.DURATION_FAST}; iconScale=${BadgerNavIndicator.SELECTED_ICON_SCALE}")
    }
    val touch = if (animated) Modifier.observeBarTouch(indicator) else Modifier
    Row(Modifier.fillMaxWidth().heightIn(min = BarMinHeight).height(IntrinsicSize.Min)
        .then(surface).then(touch)
        .padding(horizontal = horizontalInset, vertical = BadgerSpacing.xs)
        .selectableGroup().then(gestures)) {
        MainTab.entries.forEach { tab ->
            val onClick: (() -> Unit)? = pagerState?.let { pager ->
                { selectTab(pager, indicator, tab.ordinal, scope, animated) }
            }
            FloatingNavTab(tab, tab.ordinal == currentPage, colorPage, onClick)
        }
    }
}

private fun selectTab(pager: PagerState, indicator: FloatingTabIndicator, page: Int, scope: CoroutineScope, animated: Boolean) {
    Log.d(TAG, "点按切换主标签 ${MainTab.entries[page].name}; material=$animated; positionTracking=true")
    if (page == pager.currentPage && !pager.isScrollInProgress) return
    val sequence = indicator.beginTap(page)
    scope.launch {
        try {
            pager.animateScrollToPage(page, animationSpec = tween(BadgerMotion.DURATION_INDICATOR, easing = FastOutSlowInEasing))
        } finally {
            indicator.finishTap(sequence, pager.currentPage)
        }
    }
}

internal fun navBarTint(style: GlassStyle): Color = style.neutralChromeTint

/** Bar capsule bounds, trimmed horizontally so both ends meet the idle selection edges. */
private fun barBounds(size: Size, trimPx: Float): RoundRect {
    val width = (size.width - trimPx * 2f).coerceAtLeast(0f)
    return RoundRect(trimPx, 0f, trimPx + width, size.height, CornerRadius(minOf(width, size.height) / 2f))
}

@Composable
private fun Modifier.navBarSurface(horizontalTrim: Dp): Modifier {
    val style = LocalGlassStyle.current
    val colors = MiuixTheme.colorScheme
    val tint = navBarTint(style)
    LaunchedEffect(style.dark, tint) {
        Log.d(TAG, "Bar surface tint; dark=${style.dark}; alpha=${tint.alpha}; pressScale=${BadgerTouchFeedback.NAV_PRESSED_SCALE}")
    }
    return if (style.materialEnabled) {
        // Long capsules take the soft lens: less mid-band bending and far less dispersion than a
        // compact pill, or the wide bar reads as rainbow striping over patterned content.
        // 液态导航栏开关关闭时走不透明卡片色底栏（见下方 else），保证开关真正生效。
        glassSurface(BarMinHeight, tint, colors.surface,
            bounds = { barBounds(size, horizontalTrim.toPx()) },
            opticalParameters = { GlassOpticalParameters.Bar })
    } else {
        drawWithCache {
            val path = Path().apply { addG2RoundRect(barBounds(size, horizontalTrim.toPx())) }
            val stroke = Stroke(Dp.Hairline.toPx())
            val fill = colors.surfaceContainer
            val outline = colors.onSurface.copy(alpha = OUTLINE_ALPHA)
            onDrawBehind {
                drawPath(path, fill)
                drawPath(path, outline, style = stroke)
            }
        }
    }
}

/** The complete capsule observes touches without consuming child clicks or drag gestures. */
private fun Modifier.observeBarTouch(indicator: FloatingTabIndicator): Modifier =
    pointerInput(indicator) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            indicator.pressSurface()
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                }
            } finally {
                indicator.releaseSurface()
            }
        }
    }

@Composable
private fun RowScope.FloatingNavTab(
    tab: MainTab,
    selected: Boolean,
    colorPage: Int,
    onClick: (() -> Unit)?,
) {
    val colors = MiuixTheme.colorScheme
    val accentuated = colorPage == tab.ordinal
    val tint by animateColorAsState(
        if (accentuated) colors.primary else colors.onSurfaceVariantSummary,
        tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing),
        label = "navTabTint",
    )
    val iconScale by animateFloatAsState(
        if (accentuated) BadgerNavIndicator.SELECTED_ICON_SCALE else 1f,
        tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing),
        label = "navTabIconScale",
    )
    val selection = if (onClick == null) Modifier else {
        val interactions = remember { MutableInteractionSource() }
        val focused by interactions.collectIsFocusedAsState()
        Modifier.selectable(
            selected = selected,
            interactionSource = interactions,
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        ).then(if (focused) Modifier.border(Dp.Hairline, colors.primary, BadgerShapes.Capsule) else Modifier)
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(selection)
            .padding(BadgerSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BadgerNavIndicator.iconLabelGap, Alignment.CenterVertically),
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint,
            modifier = Modifier.size(TabIconSize).graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            })
        Text(tab.label, style = MiuixTheme.textStyles.footnote1, textAlign = TextAlign.Center, color = tint)
    }
}

/** Observe before the graphics transform so the follow never feeds back into pointer coordinates. */
private fun Modifier.observeBarFollow(indicator: FloatingTabIndicator): Modifier = pointerInput(indicator) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        indicator.barFollow.start(down.position.x)
        var following = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) following = true
                if (following) indicator.barFollow.move(change.position.x, BadgerTouchFeedback.dragFollowLimit.toPx())
            }
        } finally {
            indicator.barFollow.release()
        }
    }
}

private fun Modifier.observeTabPress(
    pager: PagerState, indicator: FloatingTabIndicator, direction: LayoutDirection,
): Modifier = pointerInput(pager, indicator, direction) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val position = pointerTabPosition(down.position.x, size.width.toFloat(), direction)
        val page = position.roundToInt().coerceIn(MainTab.entries.indices)
        indicator.press(page, pager.currentPage, position)
        var released = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    released = true
                    if (indicator.dragPage != null) indicator.trackPointer(pointerTabPosition(change.position.x, size.width.toFloat(), direction))
                    break
                }
                indicator.trackPointer(pointerTabPosition(change.position.x, size.width.toFloat(), direction))
            }
        } finally {
            indicator.releasePress(pager.currentPage, deferToClick = released)
        }
    }
}

private fun Modifier.selectTabByDrag(
    pagerState: PagerState,
    indicator: FloatingTabIndicator,
    layoutDirection: LayoutDirection,
): Modifier = pointerInput(pagerState, indicator, layoutDirection) {
    try {
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, _ ->
                indicator.dragTo(pointerTabPosition(change.position.x, size.width.toFloat(), layoutDirection))
            },
            onDragEnd = {
                val page = indicator.dragPage ?: return@detectHorizontalDragGestures
                Log.d(TAG, "拖动切换主标签 ${MainTab.entries[page].name}")
                pagerState.requestScrollToPage(page)
                indicator.finishDrag(page)
            },
            onDragCancel = { indicator.finishDrag(pagerState.currentPage) },
        )
    } finally {
        indicator.finishDrag(pagerState.currentPage)
    }
}
