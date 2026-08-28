package top.mcxiafeng.badger.ui

import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.blur.BlurIntensity
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.blur.LiquidLensProfile
import top.mcxiafeng.badger.ui.blur.LiquidGlassTuning
import top.mcxiafeng.badger.ui.blur.drawLiquidSphereSurface
import top.mcxiafeng.badger.ui.blur.toHazeStyle
import top.mcxiafeng.badger.ui.blur.toLiquidGlassTuning
import top.mcxiafeng.badger.ui.blur.animation.DampedDragAnimation
import top.mcxiafeng.badger.ui.blur.animation.InteractiveHighlight
import top.mcxiafeng.badger.ui.blur.animation.rememberLiquidWobble
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "Tester"

private val BarHeight = 64.dp
private val BarSideMargin = 16.dp
private val BarBottomMargin = 20.dp
private val IconSize = 26.dp
private val LabelFontSize = 12.sp
private val IndicatorHeight = 56.dp
private val IndicatorPadding = 4.dp

val LocalFloatingBarBottomPadding = staticCompositionLocalOf { 0.dp }

/** 手动计算 Color.luminance()（避免 Compose 版本兼容问题） */
private fun Color.luminance(): Float {
    val r = red * 0.2126f
    val g = green * 0.7152f
    val b = blue * 0.0722f
    return r + g + b
}

// --- Public API ---

@Composable
fun FloatingNavBar(
    selectedIndex: Int,
    pageOffset: Float,
    onSelected: (Int) -> Unit,
    tabs: List<String>,
    icons: List<ImageVector>,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
    liquidGlassEnabled: Boolean = false,
    hazeState: HazeState? = null,
    backdrop: LayerBackdrop? = null,
    blurIntensity: BlurIntensity = BlurIntensity.THICK,
    effectMode: EffectMode = EffectMode.BG_BLUR,
    isScrolling: Boolean = false,
    badges: List<String?> = emptyList(),
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
        liquidGlassEnabled = liquidGlassEnabled,
        hazeState = hazeState,
        backdrop = backdrop,
        blurIntensity = blurIntensity,
        effectMode = effectMode,
        isScrolling = isScrolling,
        badges = badges,
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
    liquidGlassEnabled: Boolean,
    hazeState: HazeState?,
    backdrop: LayerBackdrop?,
    blurIntensity: BlurIntensity,
    effectMode: EffectMode,
    isScrolling: Boolean,
    badges: List<String?> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val tabsCount = tabs.size
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val circleShape = remember { androidx.compose.foundation.shape.CircleShape }

    val isDark = containerColor.luminance() < 0.5f
    val hazeActive = liquidGlassEnabled && hazeState != null && effectMode == EffectMode.BG_BLUR

    val effectiveHaze = hazeState?.takeIf { hazeActive && it.blurEnabled }

    // --- DampedDragAnimation ---
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    var currentIndex by remember { mutableIntStateOf(selectedIndex) }

    class DampedDragHolder { var instance: DampedDragAnimation? = null }
    val holder = remember { DampedDragHolder() }

    val dampedDrag = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { true },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                } else {
                    animateToValue(targetIndex.toFloat())
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (value + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                }
            },
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) currentIndex = selectedIndex
    }
    val onSelectedUpdated by rememberUpdatedState(onSelected)
    LaunchedEffect(dampedDrag) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dampedDrag.animateToValue(index.toFloat())
            onSelectedUpdated(index)
        }
    }

    // --- InteractiveHighlight ---
    val interactiveHighlight = remember(animationScope, isLtr) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { layerSize, _ ->
                Offset(
                    x = if (isLtr) {
                        (dampedDrag.value + 0.5f) * tabWidthPx
                    } else {
                        layerSize.width - (dampedDrag.value + 0.5f) * tabWidthPx
                    },
                    y = layerSize.height / 2f,
                )
            },
        )
    }

    // --- LiquidWobble ---
    val wobbleShape = rememberLiquidWobble(
        enabled = !isScrolling && liquidGlassEnabled,
        baseCornerRadius = 50.dp,
    )

    // --- Tuning & LensProfile ---
    val tuning = blurIntensity.toLiquidGlassTuning()
    val blurRadiusDp by NavBarConfig.blurRadiusDpFlow.collectAsState(initial = 12f)
    val lensProfile by remember(dampedDrag.pressProgress, isScrolling) {
        derivedStateOf {
            val progress = dampedDrag.pressProgress
            if (progress > 0.01f) {
                LiquidLensProfile(
                    shouldRefract = true,
                    motionFraction = progress,
                    refractionAmount = (58f + progress * 54f),
                    refractionHeight = (84f + progress * 96f),
                    centerHighlightAlpha = 0.12f + progress * 0.16f,
                    edgeCompressionAlpha = (0.06f + progress * 0.16f),
                    aberrationStrength = ((0.008f + progress * 0.024f)).coerceIn(0f, 0.06f),
                )
            } else {
                LiquidLensProfile()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFloating) Modifier.padding(horizontal = BarSideMargin).padding(bottom = BarBottomMargin) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        // ==================== Layer 1: Shell (Row — the bar background) ====================
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { (IndicatorPadding * 2).toPx() }
                    tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                }
                .then(
                    when {
                        effectiveHaze != null -> {
                            Modifier
                                .clip(circleShape)
                                .hazeEffect(
                                    state = effectiveHaze,
                                    style = HazeStyle(
                                        blurRadius = blurRadiusDp.dp,
                                        tint = HazeTint(Color.White.copy(alpha = 0.12f)),
                                        backgroundColor = Color.White.copy(alpha = 0.06f),
                                    ),
                                )
                        }
                        effectMode == EffectMode.NONE -> {
                            Modifier.background(containerColor, circleShape)
                        }
                        liquidGlassEnabled && Build.VERSION.SDK_INT >= 31 -> {
                            Modifier.background(containerColor.copy(alpha = 0.7f), circleShape)
                        }
                        liquidGlassEnabled -> {
                            Modifier.background(containerColor.copy(alpha = 0.85f), circleShape)
                        }
                        else -> {
                            Modifier.background(containerColor, circleShape)
                        }
                    }
                )
                .height(BarHeight)
                .padding(horizontal = IndicatorPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, label ->
                NavBarItem(
                    title = label,
                    icon = icons[index],
                    selected = currentIndex == index,
                    onClick = { currentIndex = index },
                    badge = badges.getOrNull(index),
                )
            }
        }

        // ==================== Layer 2: Animated indicator (water droplet) ====================
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val lensShape = remember { RoundedCornerShape(50) }

            Box(
                modifier = Modifier
                    .padding(horizontal = IndicatorPadding)
                    .then(
                        if (effectMode != EffectMode.LIQUID_GLASS) {
                            Modifier
                                .graphicsLayer {
                                    val px = if (isLtr) dampedDrag.value * tabWidthPx else -dampedDrag.value * tabWidthPx
                                    translationX = px
                                    scaleX = dampedDrag.scaleX
                                    scaleY = dampedDrag.scaleY
                                    val v = dampedDrag.velocity / 10f
                                    scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                                }
                                .background(accentColor.copy(alpha = 0.12f), circleShape)
                        } else if (effectMode == EffectMode.LIQUID_GLASS) {
                            Modifier
                                .graphicsLayer {
                                    val px = if (isLtr) dampedDrag.value * tabWidthPx else -dampedDrag.value * tabWidthPx
                                    translationX = px
                                    scaleX = dampedDrag.scaleX
                                    scaleY = dampedDrag.scaleY
                                    val v = dampedDrag.velocity / 10f
                                    scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                                    transformOrigin = TransformOrigin.Center
                                }
                                .clip(wobbleShape)
                                .drawWithContent {
                                    drawContent()
                                    // 水滴球体底色
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                accentColor.copy(alpha = 0.07f),
                                                accentColor.copy(alpha = 0.02f),
                                                Color.Transparent,
                                            ),
                                            center = Offset(size.width / 2f, size.height * 0.35f),
                                            radius = size.minDimension * 0.55f,
                                        ),
                                    )
                                    drawLiquidSphereSurface(
                                        baseColor = Color.White,
                                        lensProfile = lensProfile,
                                        tuning = tuning,
                                        accentTint = accentColor,
                                    )
                                }
                        } else {
                            Modifier
                                .graphicsLayer {
                                    val px = if (isLtr) dampedDrag.value * tabWidthPx else -dampedDrag.value * tabWidthPx
                                    translationX = px
                                    scaleX = dampedDrag.scaleX
                                    scaleY = dampedDrag.scaleY
                                    val v = dampedDrag.velocity / 10f
                                    scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                                }
                                .clip(circleShape)
                                .drawWithContent {
                                    drawContent()
                                    drawLiquidSphereSurface(
                                        baseColor = Color.White,
                                        lensProfile = lensProfile,
                                        tuning = tuning,
                                        accentTint = accentColor,
                                    )
                                }
                        }
                    )
                    .then(interactiveHighlight.modifier)
                    .pointerInput(tabsCount, tabWidthPx, isLtr) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            var downPos = Offset.Zero
                            var previousPos = Offset.Zero
                            var dragStarted = false
                            var pointerId: Long = -1L

                            val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
                            val initialChange = initialEvent.changes.firstOrNull()?.takeIf { it.pressed }
                                ?: return@awaitEachGesture
                            downPos = initialChange.position
                            previousPos = downPos
                            pointerId = initialChange.id.value

                            // Press immediately on touch → lens magnification starts
                            dampedDrag.press()

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id.value == pointerId } ?: break

                                if (!change.pressed) {
                                    if (dragStarted) {
                                        dampedDrag.release()
                                        val targetIndex = dampedDrag.targetValue.roundToInt()
                                            .coerceIn(0, tabsCount - 1)
                                        if (currentIndex != targetIndex) {
                                            currentIndex = targetIndex
                                        } else {
                                            dampedDrag.animateToValue(targetIndex.toFloat())
                                        }
                                        change.consume()
                                    } else {
                                        dampedDrag.release()
                                    }
                                    break
                                }

                                val dx = change.position.x - previousPos.x
                                val totalDx = change.position.x - downPos.x
                                previousPos = change.position

                                if (!dragStarted && abs(totalDx) > touchSlop) {
                                    dragStarted = true
                                    change.consume()
                                    continue
                                }

                                if (dragStarted) {
                                    change.consume()
                                    if (tabWidthPx > 0f) {
                                        dampedDrag.updateValue(
                                            (dampedDrag.value + dx / tabWidthPx * if (isLtr) 1f else -1f)
                                                .coerceIn(0f, (tabsCount - 1).toFloat()),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .then(
                        if (hazeActive) interactiveHighlight.gestureModifier else Modifier
                    )
                    .height(IndicatorHeight)
                    .width(tabWidthDp),
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
    badge: String? = null,
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
        val iconContent: @Composable () -> Unit = {
            Image(
                modifier = Modifier.size(IconSize),
                imageVector = icon,
                contentDescription = title,
                colorFilter = ColorFilter.tint(tint),
            )
        }
        if (badge != null) {
            BadgedBox(
                badge = {
                    Badge { Text(text = badge) }
                },
            ) {
                iconContent()
            }
        } else {
            iconContent()
        }
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