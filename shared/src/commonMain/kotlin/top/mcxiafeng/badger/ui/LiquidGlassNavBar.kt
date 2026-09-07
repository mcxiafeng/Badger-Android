package top.mcxiafeng.badger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.blur.animation.DampedDragAnimation
import top.mcxiafeng.badger.ui.blur.RefractionParams
import top.mcxiafeng.badger.ui.blur.badgerSurface
import top.mcxiafeng.badger.ui.blur.badgerLiquidIndicator
import top.mcxiafeng.badger.ui.blur.rememberBadgerEdgeHighlight
import top.mcxiafeng.badger.ui.blur.rememberCombinedBackdrop
import top.mcxiafeng.badger.ui.designsystem.BadgerGlass
import top.mcxiafeng.badger.ui.designsystem.BadgerMaterials
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "LiquidGlassNavBar"
private val BarHeight = 64.dp
private val BarSideMargin = 16.dp
private val BarBottomMargin = 20.dp
private val IconSize = 26.dp
private val LabelFontSize = 12.sp
private val IndicatorHeight = 56.dp
private val IndicatorPadding = 4.dp
private const val INDICATOR_REFRACTION_SCALE = 0.6f
private const val INDICATOR_CHROMATIC_ABERRATION = 0.2f
private const val PRESS_HIGHLIGHT_BOOST = 0.3f
private const val PRESS_TINT_BOOST = 0.04f
private const val EDGE_REFRACTION_BOOST = 0.5f
private const val VELOCITY_STRETCH_DIVISOR = 10f

val LocalFloatingBarBottomPadding = staticCompositionLocalOf { 0.dp }

private fun Color.luminance(): Float {
    val r = red * 0.2126f
    val g = green * 0.7152f
    val b = blue * 0.0722f
    return r + g + b
}

@Composable
fun FloatingNavBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    tabs: List<String>,
    icons: List<ImageVector>,
    modifier: Modifier = Modifier,
    containerColor: Color = MiuixTheme.colorScheme.surface,
    effectMode: EffectMode = EffectMode.BG_BLUR,
    backdrop: LayerBackdrop? = null,
    blurActive: Boolean = true,
    advancedRefraction: Boolean = false,
    badges: List<String?> = emptyList(),
) {
    require(tabs.size == icons.size) { "tabs and icons must have the same size" }

    FloatingNavBarImpl(
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        tabs = tabs,
        icons = icons,
        modifier = modifier,
        containerColor = containerColor,
        effectMode = effectMode,
        backdrop = backdrop,
        blurActive = blurActive,
        advancedRefraction = advancedRefraction,
        badges = badges,
    )
}

@Composable
private fun FloatingNavBarImpl(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    tabs: List<String>,
    icons: List<ImageVector>,
    containerColor: Color,
    effectMode: EffectMode,
    backdrop: LayerBackdrop?,
    blurActive: Boolean,
    advancedRefraction: Boolean,
    badges: List<String?>,
    modifier: Modifier = Modifier,
) {
    val tabsCount = tabs.size
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val circleShape = CircleShape

    val isDark = containerColor.luminance() < 0.5f
    val accentColor = MiuixTheme.colorScheme.primary
    val glassActive = effectMode != EffectMode.NONE && blurActive && backdrop != null
    val refractionActive = effectMode == EffectMode.LIQUID_GLASS && advancedRefraction && blurActive && backdrop != null

    val hideLabels by NavBarConfig.hideLabelsFlow.collectAsState(initial = false)
    val labelVisible = !hideLabels

    // [FIX] rememberUpdatedState 防止 remember 块内的回调闭包捕获 stale selectedIndex/onSelected
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnSelected by rememberUpdatedState(onSelected)


    // [FIX] 使用 mutableFloatStateOf 减少重组
    var tabWidthPx by remember { mutableFloatStateOf(0f) }

    // [FIX] 统一状态源：以 dampedDrag.value 为真理源，selectedIndex 仅作为外部重置信号
    val dampedDrag = remember(animationScope, tabsCount, density) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.01f,
            initialScale = 1f,
            pressedScale = 0.92f,
            canDrag = { true },
            onDragStarted = { position, size ->
                if (tabWidthPx > 0f) {
                    val padPx = with(density) { IndicatorPadding.toPx() }
                    val rawIndex = (position.x - padPx) / tabWidthPx
                    val clampedIndex = rawIndex.coerceIn(0f, (tabsCount - 1).toFloat())
                    updateValue(clampedIndex)
                }
            },
            onDragStopped = {
                // Settle 到最近的整数索引
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                animateToValue(targetIndex.toFloat())
            },
            onSettled = { settledIndex ->
                BadgerLog.d(TAG, "onSettled: settledIndex=$settledIndex, currentSelected=$currentSelectedIndex")
                if (settledIndex != currentSelectedIndex) {
                    currentOnSelected(settledIndex)
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    // [FIX] RTL 修正：拖拽量在物理空间始终是 LTR，仅在渲染时镜像
                    updateValue(
                        (value + dragAmount.x / tabWidthPx)
                            .coerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                }
            },
        )
    }

    // [FIX] 外部 selectedIndex 变化时，强制同步动画值（不触发 onSelected 回调）
    LaunchedEffect(selectedIndex) {
        val currentTarget = dampedDrag.targetValue.roundToInt()
        if (currentTarget != selectedIndex) {
            if (effectMode == EffectMode.NONE) {
                dampedDrag.snapToValue(selectedIndex.toFloat())
            } else {
                dampedDrag.animateToValue(selectedIndex.toFloat())
            }
        }
    }

    // [FIX] 移除 stableIndex snapshotFlow——它用手指位置 roundToInt 触发导航，
    // 但 roundToInt(0.657)=1 ≠ 用户点击的 tab 0，导致误导航。
    // 导航统一由 onTap → animateToValue → onSettled 处理（唯一出口）。

    // 折射联动
    val edgeBoost by remember(dampedDrag, tabsCount) {
        derivedStateOf {
            if (tabsCount < 2) 1f
            else {
                val distToEdge = minOf(dampedDrag.value, (tabsCount - 1) - dampedDrag.value)
                val maxDist = ((tabsCount - 1) / 2f).coerceAtLeast(1f)
                1f + EDGE_REFRACTION_BOOST * (1f - (distToEdge / maxDist).coerceIn(0f, 1f))
            }
        }
    }

    val shellHighlight = rememberBadgerEdgeHighlight(isDark = isDark, followTilt = refractionActive)
    val dropletHighlightBase = rememberBadgerEdgeHighlight(
        isDark = isDark, followTilt = false, extraDegrees = 90f,
    )

    val tabsBackdrop = if (refractionActive) rememberLayerBackdrop() else null
    val dropletBackdrop = if (refractionActive && backdrop != null && tabsBackdrop != null) {
        rememberCombinedBackdrop(backdrop, tabsBackdrop)
    } else null

    val movingAlpha by remember(dampedDrag) {
        derivedStateOf {
            val pp = dampedDrag.pressProgress
            val range = (dampedDrag.valueRange.endInclusive - dampedDrag.valueRange.start).coerceAtLeast(1f)
            val distNorm = (abs(dampedDrag.value - dampedDrag.targetValue) / range).coerceIn(0f, 1f)
            maxOf(pp, distNorm).coerceIn(0f, 1f)
        }
    }
    val selectedIdleAlpha = 0.85f + 0.15f * movingAlpha

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BarSideMargin)
            .padding(bottom = BarBottomMargin),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Layer 0: Tab Content Sampling
        if (tabsBackdrop != null) {
            Row(
                Modifier
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .height(BarHeight)
                    .padding(horizontal = IndicatorPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, label ->
                    NavBarItem(
                        title = label, icon = icons[index],
                        selected = false, contentColor = accentColor,
                        showLabel = labelVisible, onClick = {},
                    )
                }
            }
        }

        // Layer 1: Shell + Tabs
        val shellMaterial = if (refractionActive) BadgerGlass.glassRegular.base else BadgerMaterials.chrome

        // [FIX] 将拖拽手势提升到 Shell 层，Indicator 变为纯视觉，彻底解决点击穿透问题
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    val contentWidthPx = coords.size.width.toFloat() -
                            with(density) { (IndicatorPadding * 2).toPx() }
                    tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                }
                .height(BarHeight)
                .then(
                    if (glassActive) Modifier.badgerSurface(
                        material = shellMaterial, shape = circleShape, backdrop = backdrop,
                        containerColor = containerColor, tint = shellMaterial.tintFor(isDark),
                        enabled = true,
                        refraction = if (refractionActive) RefractionParams(
                            heightPx = with(density) { BadgerGlass.glassRegular.refractionHeight.toPx() },
                            amountPx = with(density) { BadgerGlass.glassRegular.refractionAmount.toPx() },
                        ) else null,
                        highlight = if (refractionActive) shellHighlight else null,
                    ) else Modifier.background(containerColor, circleShape),
                )
                // [FIX] 全局拖拽手势：拦截水平拖拽，释放点击给子项
                .pointerInput(tabsCount, tabWidthPx, dampedDrag) {
                    awaitEachGesture {
                        val down = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull { it.pressed } ?: return@awaitEachGesture

                        dampedDrag.press()
                        // [FIX] 触摸即跳到手指位置——不等 slop，不增量跟手。
                        // 指示器立刻在手指下方，拖拽只是在此基础上继续跟手。
                        if (tabWidthPx > 0f) {
                            val padPx = with(density) { IndicatorPadding.toPx() }
                            val rawIndex = (down.position.x - padPx) / tabWidthPx
                            dampedDrag.updateValue(
                                rawIndex.coerceIn(0f, (tabsCount - 1).toFloat())
                            )
                        }
                        var dragStarted = false
                        var prevX = down.position.x

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                if (dragStarted) {
                                    change.consume()
                                    // [FIX] 拖拽释放后 settle 到最近整数索引。
                                    // 原来只调 release() 不调 animateToValue，导致：
                                    // 1) valueAnimation 从未在拖拽中更新 → release 等 valueAnimation 收敛到 stale targetValue → 立即"收敛"
                                    // 2) onSettled 用 stale targetValue → 不触发 onSelected → 不跳转
                                    val nearest = dampedDrag.value.roundToInt().coerceIn(0, tabsCount - 1)
                                    BadgerLog.d(TAG, "drag release: settle to nearest=$nearest, dragValue=${dampedDrag.value}")
                                    dampedDrag.animateToValue(nearest.toFloat())
                                } else {
                                    dampedDrag.release()
                                }
                                break
                            }

                            val dx = change.position.x - prevX
                            prevX = change.position.x

                            if (!dragStarted && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                                dragStarted = true
                            }

                            if (dragStarted && tabWidthPx > 0f) {
                                change.consume()
                                dampedDrag.updateValue(
                                    (dampedDrag.value + dx / tabWidthPx)
                                        .coerceIn(0f, (tabsCount - 1).toFloat())
                                )
                            }
                        }
                    }
                }
                .padding(horizontal = IndicatorPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, label ->
                NavBarItem(
                    title = label,
                    icon = icons[index],
                    selected = dampedDrag.value.roundToInt().coerceIn(0, tabsCount - 1) == index,
                    showLabel = labelVisible,
                    onClick = {
                        // 点击直接驱动动画，由 snapshotFlow 统一回调
                        dampedDrag.animateToValue(index.toFloat())
                    },
                    badge = badges.getOrNull(index),
                    selectedIdleAlpha = selectedIdleAlpha,
                )
            }
        }

        // Layer 2: Animated Indicator (Pure Visual)
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val dropletShape = remember { RoundedCornerShape(percent = 50) }
            val pressProgress = dampedDrag.pressProgress

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        // [FIX] 首帧保护 & 运动透明度
                        alpha = if (refractionActive) movingAlpha else 1f

                        // [FIX] RTL 仅在渲染层处理
                        val indicatorPadPx = with(density) { IndicatorPadding.toPx() }
                        val logicalX = dampedDrag.value * tabWidthPx + indicatorPadPx
                        translationX = if (isLtr) logicalX else -(logicalX)

                        val v = dampedDrag.velocity / VELOCITY_STRETCH_DIVISOR
                        scaleX = dampedDrag.scaleX / (1f - (v * 0.75f).coerceIn(-0.2f, 0.2f))
                        scaleY = dampedDrag.scaleY * (1f - (v * 0.25f).coerceIn(-0.2f, 0.2f))
                        transformOrigin = TransformOrigin.Center
                    }
                    .then(
                        if (dropletBackdrop != null) {
                            Modifier.badgerLiquidIndicator(
                                backdrop = dropletBackdrop,
                                shape = dropletShape,
                                surfaceTint = accentColor.copy(
                                    alpha = PRESS_TINT_BOOST + PRESS_TINT_BOOST * pressProgress,
                                ),
                                refraction = RefractionParams(
                                    heightPx = with(density) {
                                        BadgerGlass.glassRegular.refractionHeight.toPx() *
                                                INDICATOR_REFRACTION_SCALE * pressProgress * edgeBoost
                                    },
                                    amountPx = with(density) {
                                        BadgerGlass.glassRegular.refractionAmount.toPx() *
                                                INDICATOR_REFRACTION_SCALE * pressProgress * edgeBoost
                                    },
                                    chromaticAberration = INDICATOR_CHROMATIC_ABERRATION,
                                    depthEffect = true,
                                ),
                                highlight = if (pressProgress > 0.01f) {
                                    dropletHighlightBase.copy(
                                        alpha = (1f - PRESS_HIGHLIGHT_BOOST +
                                                PRESS_HIGHLIGHT_BOOST * pressProgress).coerceAtMost(1f),
                                    )
                                } else null,
                            )
                        } else {
                            Modifier.background(
                                accentColor.copy(
                                    alpha = PRESS_TINT_BOOST + PRESS_TINT_BOOST * pressProgress,
                                ),
                                dropletShape,
                            )
                        },
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
    showLabel: Boolean = true,
    contentColor: Color? = null,
    selectedIdleAlpha: Float = 1f,
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val primary = MiuixTheme.colorScheme.primary
    val onSurface = MiuixTheme.colorScheme.onSurface

    val tint = contentColor ?: when {
        isPressed && selected -> primary.copy(alpha = 0.7f)
        isPressed && !selected -> onSurface.copy(alpha = 0.2f)
        selected -> primary.copy(alpha = selectedIdleAlpha)
        else -> onSurface.copy(alpha = 0.4f)
    }
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal

    Column(
        modifier = Modifier
            .height(BarHeight)
            .weight(1f)
            // [FIX] 使用 detectTapGestures 自动处理 press/release 状态
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
            BadgedBox(badge = { Badge { Text(text = badge) } }) { iconContent() }
        } else {
            iconContent()
        }

        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
    }
}