package top.mcxiafeng.badger.ui

import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.MainTab
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private const val TAG = "FloatingNavBarTester"
private const val OUTLINE_ALPHA = 0.14f
private const val INDICATOR_ALPHA = 0.15f
private val BarMaxWidth = 400.dp
private val BarMinHeight = 64.dp
private val BarElevation = 3.dp
private val TabIconSize = 22.dp

/** 主页面内容和浮动操作按钮需要预留的高度，系统栏由 Scaffold 避让。 */
val LocalFloatingBarBottomPadding = staticCompositionLocalOf { 0.dp }

internal val FloatingNavBarMinBottomPadding = BarMinHeight + BadgerSpacing.lg

@Composable
internal fun FloatingNavBar(
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    val currentPage = pagerState?.currentPage ?: MainTab.MY_CARD.ordinal
    val indicator = remember(pagerState) { FloatingTabIndicator(currentPage, scope) }
    LaunchedEffect(indicator, currentPage) { indicator.settleTo(currentPage) }
    val dragModifier = if (pagerState == null) Modifier else {
        Modifier.selectTabByDrag(pagerState, indicator, layoutDirection)
    }
    val highlightedPage = indicator.dragPage ?: currentPage

    Row(
        modifier = modifier
            .padding(start = BadgerSpacing.xl, end = BadgerSpacing.xl, bottom = BadgerSpacing.lg)
            .widthIn(max = BarMaxWidth)
            .fillMaxWidth()
            .heightIn(min = BarMinHeight)
            .height(IntrinsicSize.Min)
            .shadow(BarElevation, CircleShape, clip = true)
            .background(colors.surfaceContainer)
            .border(Dp.Hairline, colors.onSurface.copy(alpha = OUTLINE_ALPHA), CircleShape)
            .padding(BadgerSpacing.xs)
            .selectableGroup()
            .then(dragModifier)
            .slidingTabIndicator(indicator, colors.primary.copy(alpha = INDICATOR_ALPHA)),
    ) {
        MainTab.entries.forEach { tab ->
            val onClick: (() -> Unit)? = pagerState?.let { pager ->
                {
                    Log.d(TAG, "点按切换主标签 ${tab.name}")
                    scope.launch { pager.animateScrollToPage(tab.ordinal) }
                }
            }
            FloatingNavTab(tab, tab.ordinal == currentPage, tab.ordinal == highlightedPage, onClick)
        }
    }
}

private fun Modifier.slidingTabIndicator(indicator: FloatingTabIndicator, color: Color): Modifier = drawBehind {
    val tabWidth = size.width / MainTab.entries.size
    val position = indicator.position
    val slot = if (layoutDirection == LayoutDirection.Ltr) position else MainTab.entries.lastIndex - position
    drawRoundRect(
        color = color,
        topLeft = Offset(tabWidth * slot, 0f),
        size = Size(tabWidth, size.height),
        cornerRadius = CornerRadius(minOf(tabWidth, size.height) / 2f),
    )
}

@Composable
private fun RowScope.FloatingNavTab(
    tab: MainTab,
    selected: Boolean,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
) {
    val colors = MiuixTheme.colorScheme
    val tint = if (highlighted) colors.primary else colors.onSurfaceVariantSummary
    val selection = if (onClick == null) Modifier else {
        val interactions = remember { MutableInteractionSource() }
        val focused by interactions.collectIsFocusedAsState()
        Modifier.selectable(
            selected = selected,
            interactionSource = interactions,
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        ).then(if (focused) Modifier.border(Dp.Hairline, colors.primary, CircleShape) else Modifier)
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(selection)
            .padding(BadgerSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs, Alignment.CenterVertically),
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(TabIconSize))
        Text(tab.label, style = MiuixTheme.textStyles.footnote1, textAlign = TextAlign.Center, color = tint)
    }
}

private fun Modifier.selectTabByDrag(
    pagerState: PagerState,
    indicator: FloatingTabIndicator,
    layoutDirection: LayoutDirection,
): Modifier = pointerInput(pagerState, indicator, layoutDirection) {
    val direction = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
    try {
        detectHorizontalDragGestures(
            onHorizontalDrag = { _, delta ->
                val tabWidth = size.width.toFloat() / MainTab.entries.size
                if (tabWidth > 0f) indicator.dragBy(direction * delta / tabWidth)
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

internal class FloatingTabIndicator(initialPage: Int, private val scope: CoroutineScope) {
    var position by mutableFloatStateOf(initialPage.toFloat())
        private set
    var dragPage by mutableStateOf<Int?>(null)
        private set

    private var settleJob: Job? = null
    private var settleTarget: Int? = initialPage

    fun dragBy(delta: Float) {
        if (dragPage == null) {
            settleJob?.cancel()
            settleTarget = null
        }
        position = (position + delta).coerceIn(0f, MainTab.entries.lastIndex.toFloat())
        dragPage = position.roundToInt()
    }

    fun finishDrag(page: Int) {
        if (dragPage == null) return
        dragPage = null
        settleTo(page)
    }

    fun settleTo(page: Int) {
        if (dragPage != null || settleTarget == page) return
        settleTarget = page
        settleJob?.cancel()
        val target = page.toFloat()
        if (position == target) return
        settleJob = scope.launch {
            animate(position, target, animationSpec = tween(BadgerMotion.DURATION_FAST)) { value, _ ->
                position = value
            }
        }
    }
}

private val ClassicBarHeight = 64.dp
private val ClassicIconSize = 26.dp
private val ClassicIconGap = 2.dp
private val ClassicLabelSize = 12.sp
private const val CLASSIC_PRESSED_ALPHA = 0.6f

@Composable
fun RowScope.NavBarItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val isPressed by interactions.collectIsPressedAsState()
    val colors = MiuixTheme.colorScheme
    val baseTint = if (selected) colors.primary else colors.onSurfaceVariantSummary
    val tint = if (isPressed) baseTint.copy(alpha = CLASSIC_PRESSED_ALPHA) else baseTint

    Column(
        modifier = Modifier
            .height(ClassicBarHeight)
            .weight(1f)
            .selectable(selected = selected, interactionSource = interactions, indication = null,
                role = Role.Tab, onClick = {
                    Log.d(TAG, "点按经典主标签 $title")
                    onClick()
                })
            .padding(horizontal = BadgerSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(ClassicIconSize),
            colorFilter = ColorFilter.tint(tint),
        )
        Spacer(modifier = Modifier.height(ClassicIconGap))
        Text(
            text = title,
            color = tint,
            textAlign = TextAlign.Center,
            fontSize = ClassicLabelSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
