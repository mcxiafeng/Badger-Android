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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
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

/** 导航栏高度恒定：[用户裁决 2026-09-06] 蹲起式整栏收缩移除，形变只发生在水珠指示器上 */
private val BarHeight = 64.dp
private val BarSideMargin = 16.dp
private val BarBottomMargin = 20.dp
private val IconSize = 26.dp
private val LabelFontSize = 12.sp
private val IndicatorHeight = 56.dp
private val IndicatorPadding = 4.dp

/** 水滴折射强度相对 glassRegular token 的比例（56dp 小控件用满 24dp 位移过强） */
private const val INDICATOR_REFRACTION_SCALE = 0.6f

/** 水滴色散强度（miuix example 校准值） */
private const val INDICATOR_CHROMATIC_ABERRATION = 0.5f

/** 按压实变：高光强度提升（特效规格 §4「高光强度 +30%」） */
private const val PRESS_HIGHLIGHT_BOOST = 0.3f

/** 按压实变：tint 不透明度提升（特效规格 §4「按压中玻璃变实」） */
private const val PRESS_TINT_BOOST = 0.04f

/** 静息水滴 tint alpha（重做前行为保留） */
private const val INDICATOR_TINT_ALPHA = 0.12f

/** 拖到导航栏边缘时折射增强的倍率上限（特效规格 §4「拖到边缘折射增强」） */
private const val EDGE_REFRACTION_BOOST = 0.5f

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
    // [修复 2026-09-06] 折射严格限定液态玻璃档——「完整液态效果」偏好会跨档持久，
    // 旧判断漏了档位检查导致标准磨砂档误启折射/水珠玻璃
    val refractionActive =
        effectMode == EffectMode.LIQUID_GLASS && advancedRefraction && blurActive && backdrop != null

    LaunchedEffect(effectMode, refractionActive, glassActive) {
        BadgerLog.d(TAG, "mode=$effectMode glass=$glassActive refraction=$refractionActive")
    }

    // --- [用户裁决 2026-09-06] 导航栏高度恒定（蹲起式整栏收缩移除——形变只发生在水珠指示器上）；
    // 「隐藏标签」为常驻开关（默认关）：开启后导航栏纯图标形态，与滚动无关 ---
    val hideLabels by NavBarConfig.hideLabelsFlow.collectAsState(initial = false)
    val labelVisible = !hideLabels

    // --- DampedDragAnimation（特效规格 §4：阻尼参数保留，现值可用） ---
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
            // 按压实变（spec §4）：scale 0.97 —— 玻璃变实
            pressedScale = 0.97f,
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

    // --- 折射联动（特效规格 §4：拖到边缘折射增强） ---
    val edgeBoost by remember(tabsCount) {
        derivedStateOf {
            if (tabsCount < 2) {
                1f
            } else {
                val distToEdge = minOf(dampedDrag.value, (tabsCount - 1) - dampedDrag.value)
                val maxDist = ((tabsCount - 1) / 2f).coerceAtLeast(1f)
                1f + EDGE_REFRACTION_BOOST * (1f - (distToEdge / maxDist).coerceIn(0f, 1f))
            }
        }
    }

    // --- 材质高光（L5 静态 / L6 倾斜光斑仅在完整液态档） ---
    val shellHighlight = rememberBadgerEdgeHighlight(isDark = isDark, followTilt = refractionActive)
    val dropletHighlightBase = rememberBadgerEdgeHighlight(
        isDark = isDark, followTilt = false, extraDegrees = 90f,
    )

    // --- Tab 内容采样源（水滴融合：折射「页面 + Tab 内容」，miuix example 模式） ---
    val tabsBackdrop = if (refractionActive) rememberLayerBackdrop() else null
    val dropletBackdrop = if (refractionActive && backdrop != null && tabsBackdrop != null) {
        rememberCombinedBackdrop(backdrop, tabsBackdrop)
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BarSideMargin)
            .padding(bottom = BarBottomMargin),
        contentAlignment = Alignment.CenterStart,
    ) {
        // ==================== Layer 0: Tab 内容注册（不可见，供水滴折射采样） ====================
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
                        title = label,
                        icon = icons[index],
                        selected = currentIndex == index,
                        // 水珠内的前景内容：与真实 Tab 内容一致（图标+文字齐全且为强调色，
                        // miuix example 同款——透过水珠看到的选中 Tab = 强调色的图标与文字）
                        contentColor = accentColor,
                        showLabel = labelVisible,
                        onClick = {},
                    )
                }
            }
        }

        // ==================== Layer 1: Shell（玻璃/磨砂/纯色导航栏本体） ====================
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { (IndicatorPadding * 2).toPx() }
                    tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                }
                .height(BarHeight)
                .then(
                    if (glassActive) {
                        Modifier.badgerSurface(
                            material = BadgerMaterials.chrome,
                            shape = circleShape,
                            backdrop = backdrop,
                            containerColor = containerColor,
                            tint = BadgerMaterials.chrome.tintFor(isDark),
                            enabled = true,
                            refraction = if (refractionActive) {
                                RefractionParams(
                                    heightPx = with(density) { BadgerGlass.glassRegular.refractionHeight.toPx() },
                                    amountPx = with(density) { BadgerGlass.glassRegular.refractionAmount.toPx() },
                                )
                            } else {
                                null
                            },
                            highlight = shellHighlight,
                        )
                    } else {
                        Modifier.background(containerColor, circleShape)
                    }
                )
                .padding(horizontal = IndicatorPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, label ->
                NavBarItem(
                    title = label,
                    icon = icons[index],
                    selected = currentIndex == index,
                    showLabel = labelVisible,
                    onClick = { currentIndex = index },
                    badge = badges.getOrNull(index),
                )
            }
        }

        // ==================== Layer 2: Animated indicator（水滴） ====================
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val dropletShape = remember { RoundedCornerShape(50) }
            val pressProgress = dampedDrag.pressProgress
            val pressTintAlpha = INDICATOR_TINT_ALPHA + PRESS_TINT_BOOST * pressProgress

            Box(
                modifier = Modifier
                    .padding(horizontal = IndicatorPadding)
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
                    .then(
                        if (dropletBackdrop != null) {
                            // 完整液态档：水滴 = 按压驱动折射的玻璃元素（按压变实 + 边缘折射增强）
                            Modifier.badgerLiquidIndicator(
                                backdrop = dropletBackdrop,
                                shape = dropletShape,
                                surfaceTint = accentColor.copy(alpha = pressTintAlpha),
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
                                        alpha = (1f - PRESS_HIGHLIGHT_BOOST + PRESS_HIGHLIGHT_BOOST * pressProgress)
                                            .coerceAtMost(1f),
                                    )
                                } else {
                                    null
                                },
                            )
                        } else {
                            // 磨砂/无效果/未开高级折射：主题色胶囊（按压变实）
                            Modifier.background(accentColor.copy(alpha = pressTintAlpha), dropletShape)
                        }
                    )
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
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val primary = MiuixTheme.colorScheme.primary
    val onSurfaceVariant = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val tint = contentColor ?: when {
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
        // [用户裁决 2026-09-06] 标签隐藏仅由「滚动时隐藏标签」开关控制（默认关），展开/收起带动画；栏高恒定
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
