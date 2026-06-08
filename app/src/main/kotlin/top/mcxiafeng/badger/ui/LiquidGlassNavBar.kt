package top.mcxiafeng.badger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.capsule.ContinuousCapsule
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.abs

private val BarHeight = 64.dp
private val BarSideMargin = 16.dp
private val CornerRadius = 50.dp
private val BarBottomMargin = 20.dp
private val IconSize = 26.dp
private val LabelFontSize = 12.sp
private val IndicatorHeight = 56.dp
private val IndicatorPadding = 4.dp

val LocalFloatingBarBottomPadding = staticCompositionLocalOf { 0.dp }

@Composable
fun FloatingNavBar(
    selectedIndex: Int,
    pageOffset: Float,
    onSelected: (Int) -> Unit,
    tabs: List<String>,
    icons: List<ImageVector>,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
) {
    FloatingNavBarImpl(
        selectedIndex = selectedIndex,
        pageOffset = pageOffset,
        onSelected = onSelected,
        tabs = tabs,
        icons = icons,
        modifier = modifier,
        accentColor = MiuixTheme.colorScheme.primary,
        containerColor = color,
        isFloating = true,
    )
}

@Composable
private fun FloatingNavBarImpl(
    selectedIndex: Int,
    pageOffset: Float,
    onSelected: (Int) -> Unit,
    tabs: List<String>,
    icons: List<ImageVector>,
    accentColor: Color,
    containerColor: Color,
    isFloating: Boolean,
    modifier: Modifier = Modifier,
) {
    val tabsCount = tabs.size
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val continuousOffset = selectedIndex + pageOffset
    val settledIndex = if (abs(pageOffset) < 0.1f) selectedIndex else -1

    val indicatorScale = remember { Animatable(1f) }
    LaunchedEffect(selectedIndex) {
        indicatorScale.snapTo(0.9f)
        indicatorScale.animateTo(1f, spring(stiffness = 400f, dampingRatio = 0.5f))
    }

    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFloating) Modifier.padding(horizontal = BarSideMargin).padding(bottom = BarBottomMargin) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        // Layer 1: Visible Row (the bar itself)
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { (IndicatorPadding * 2).toPx() }
                    tabWidthPx = contentWidthPx / tabsCount
                }
                .then(
                    if (isFloating) {
                        Modifier.graphicsLayer(
                            shadowElevation = with(density) { 1.dp.toPx() },
                            shape = miuixShape(CornerRadius),
                            clip = true,
                        )
                    } else {
                        Modifier
                    }
                )
                .background(containerColor)
                .height(BarHeight)
                .padding(horizontal = IndicatorPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, label ->
                NavBarItem(
                    title = label,
                    icon = icons[index],
                    selected = settledIndex == index,
                    onClick = { onSelected(index) },
                )
            }
        }

        // Layer 2: Animated indicator Box (follows swipe)
        if (tabWidthPx > 0f) {
            Box(
                Modifier
                    .padding(horizontal = IndicatorPadding)
                    .graphicsLayer {
                        val offset = continuousOffset * tabWidthPx
                        translationX = if (isLtr) offset else -offset
                        val scale = indicatorScale.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(
                        accentColor.copy(alpha = 0.12f),
                        if (isFloating) ContinuousCapsule else RoundedCornerShape(28.dp)
                    )
                    .height(IndicatorHeight)
                    .width(with(density) { ((totalWidthPx - with(density) { (IndicatorPadding * 2).toPx() }) / tabsCount).toDp() })
            )
        }
    }
}

@Composable
fun RowScope.NavBarItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val primary = MiuixTheme.colorScheme.primary
    val onSurfaceVariant = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val tint = when {
        isPressed && selected -> primary.copy(alpha = 0.6f)
        isPressed && !selected -> onSurfaceVariant.copy(alpha = 0.6f)
        selected -> primary
        else -> onSurfaceVariant
    }
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal

    Column(
        modifier = Modifier
            .height(BarHeight)
            .weight(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { currentOnClick() },
                )
            }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            modifier = Modifier.size(IconSize),
            imageVector = icon,
            contentDescription = title,
            colorFilter = ColorFilter.tint(tint),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            color = tint,
            textAlign = TextAlign.Center,
            fontSize = LabelFontSize,
            fontWeight = fontWeight,
        )
    }
}
