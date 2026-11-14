package top.mcxiafeng.badger.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "Tester"

private val BarHeight = 64.dp
private val BarSideMargin = 16.dp
private val BarBottomMargin = 20.dp
/** 底栏柔影：低 elevation + 低透明度，模拟环境光散射而非硬投影 */
private val BarElevation = 2.dp
private const val BAR_SHADOW_AMBIENT_ALPHA = 0.10f
private const val BAR_SHADOW_SPOT_ALPHA = 0.18f
private val IconSize = 26.dp
private val LabelFontSize = 12.sp
private val IndicatorHeight = 56.dp
private val IndicatorPadding = 4.dp

/** 指示器选中色 tint 透明度 */
private const val INDICATOR_TINT_ALPHA = 0.12f

/** 按压时指示器的放大倍数（56dp → 约 78dp，凸出条高营造按压手感） */
private const val INDICATOR_PRESS_SCALE = 1.39f

/** 速度形变上限（水平拉伸 / 垂直压缩的最大幅度） */
private const val INDICATOR_STRETCH_MAX = 0.3f

/** 指示器水平拉伸速度增益（速度单位：tab 宽/秒） */
private const val INDICATOR_STRETCH_GAIN_X = 0.1f

/** 指示器垂直压缩速度增益（速度单位：tab 宽/秒） */
private const val INDICATOR_STRETCH_GAIN_Y = 0.045f

/** 弹性蓄力的基础幅度（tab 索引单位） */
private const val ELASTIC_AMP_BASE = 0.08f

/** 弹性蓄力随切换距离的增幅（每多跨一个 tab） */
private const val ELASTIC_AMP_PER_TAB = 0.04f

/** 弹性蓄力幅度上限 */
private const val ELASTIC_AMP_MAX = 0.22f

/** 速度帧差分的最小帧间隔（毫秒），防止极短帧导致速度爆炸 */
private const val VELOCITY_MIN_FRAME_MS = 1f

private const val MILLIS_PER_SECOND = 1000f

/** 按压缩放弹簧：轻微回弹（与原 DampedDragAnimation 的 scale spec 一致） */
private val INDICATOR_PRESS_SPRING = spring<Float>(dampingRatio = 0.6f, stiffness = 250f)

/** 拖拽跟手弹簧：临界阻尼高刚度，指示器平滑贴指（与原 valueAnimationSpec 一致） */
private val INDICATOR_DRAG_FOLLOW_SPRING =
    spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)

/** 弹性蓄力弹簧：高刚度临界阻尼，蓄力段快而不跳 */
private val ELASTIC_KICK_SPRING =
    spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)

/** 弹性回摆弹簧：中等回弹低刚度，追上落点后过冲回摆（果冻感的核心） */
private val ELASTIC_SETTLE_SPRING = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = 0.001f,
)

/** 拖拽释放后 dragOffset 的归位弹簧：无回弹，与 Pager 默认 settle 手感一致 */
private val INDICATOR_SETTLE_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** 二级页面 LazyColumn 需要预留的悬浮导航栏底部避让高度 */
val LocalFloatingBarBottomPadding = staticCompositionLocalOf { 0.dp }

// --- Public API ---

/**
 * 悬浮胶囊导航栏（纯色底 + 动态指示器）。
 *
 * 指示器位置 = pager 实时位置（selectedIndex + pageOffset，连续跟随滑动/翻页）
 * + dragOffset（拖拽指示器的跟手偏移）+ elasticOffset（切换时的弹性形变层）。
 * 切换时先反向蓄力再带回弹地追上落点，落点冲击经速度形变转为垂直压扁，
 * 形成 squash & stretch 的果冻手感；点击 tab / 滑动内容 / 拖拽指示器共用同一位置公式。
 */
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
        containerColor = color,
        modifier = modifier,
    )
}

@Composable
private fun FloatingNavBarImpl(
    selectedIndex: Int,
    pageOffset: Float,
    onSelected: (Int) -> Unit,
    tabs: List<String>,
    icons: List<ImageVector>,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = tabs.size

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    val onSelectedUpdated by rememberUpdatedState(onSelected)

    // --- 指示器动画状态 ---
    // pagerPosition 用 SideEffect 镜像成快照状态，供逐帧速度循环在 draw 阶段读取，
    // 避免每帧重组整个导航栏。
    val pagerPositionState = remember { mutableFloatStateOf(0f) }
    SideEffect { pagerPositionState.floatValue = selectedIndex + pageOffset }

    // dragOffset：拖拽指示器时的跟手偏移（tab 索引单位），释放后 spring 归零
    val dragOffset = remember { Animatable(0f) }
    // elasticOffset：切换时的弹性形变层（tab 索引单位）。切换开始先反向蓄力，再用带回弹的
    // spring 归零——归零过程过冲越过落点再回摆，配合速度形变产生挤压拉伸的果冻手感
    val elasticOffset = remember { Animatable(0f) }
    val pressAnimation = remember(animationScope) { IndicatorPressAnimation(animationScope) }

    // 切换触发：selectedIndex 变化（点按、滑动越过中点、fling）即启动一次蓄力-回摆
    var previousSelectedIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        val travel = selectedIndex - previousSelectedIndex
        previousSelectedIndex = selectedIndex
        if (travel != 0) {
            val direction = if (travel > 0) 1f else -1f
            val amp = (ELASTIC_AMP_BASE + ELASTIC_AMP_PER_TAB * abs(travel))
                .coerceAtMost(ELASTIC_AMP_MAX)
            // 蓄力方向与移动方向相反：pill 先向后拉伸，再回摆追上并过冲
            val anticipation = -direction * amp
            Log.d(TAG, "FloatingNavBar: elastic kick, travel=$travel, amp=$amp")
            elasticOffset.animateTo(anticipation, ELASTIC_KICK_SPRING)
            elasticOffset.animateTo(0f, ELASTIC_SETTLE_SPRING)
        }
    }

    // 指示器速度（tab 宽/秒）：逐帧差分位置，驱动挤压拉伸形变；静止时自然衰减为 0
    val indicatorVelocity = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastPosition = pagerPositionState.floatValue + dragOffset.value + elasticOffset.value
        var lastNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nanos ->
                val position = pagerPositionState.floatValue + dragOffset.value + elasticOffset.value
                val deltaMs = (nanos - lastNanos) / 1_000_000f
                val dtSec = deltaMs.coerceAtLeast(VELOCITY_MIN_FRAME_MS) / MILLIS_PER_SECOND
                indicatorVelocity.floatValue = (position - lastPosition) / dtSec
                lastPosition = position
                lastNanos = nanos
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BarSideMargin)
            .padding(bottom = BarBottomMargin),
        contentAlignment = Alignment.CenterStart,
    ) {
        // ==================== Layer 1: 胶囊底栏 ====================
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    val contentWidthPx =
                        coords.size.width - with(density) { (IndicatorPadding * 2).toPx() }
                    tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                }
                .shadow(
                    elevation = BarElevation,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = BAR_SHADOW_AMBIENT_ALPHA),
                    spotColor = Color.Black.copy(alpha = BAR_SHADOW_SPOT_ALPHA),
                )
                .background(containerColor, CircleShape)
                .height(BarHeight)
                .padding(horizontal = IndicatorPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, label ->
                NavBarItem(
                    title = label,
                    icon = icons[index],
                    selected = selectedIndex == index,
                    onClick = {
                        Log.d(TAG, "FloatingNavBar: tab click, index=$index")
                        // 打断可能仍在衰减的 dragOffset，页面切换交给 pager 动画驱动指示器
                        animationScope.launch { dragOffset.snapTo(0f) }
                        onSelectedUpdated(index)
                    },
                )
            }
        }

        // ==================== Layer 2: 动态指示器 ====================
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }

            Box(
                modifier = Modifier
                    .padding(horizontal = IndicatorPadding)
                    .graphicsLayer {
                        val pillPosition =
                            pagerPositionState.floatValue + dragOffset.value + elasticOffset.value
                        val px = if (isLtr) pillPosition * tabWidthPx else -pillPosition * tabWidthPx
                        translationX = px
                        // 速度形变：运动方向水平拉伸、垂直压缩（挤压拉伸手感）
                        val v = indicatorVelocity.floatValue
                        val stretchX = (v * INDICATOR_STRETCH_GAIN_X)
                            .coerceIn(-INDICATOR_STRETCH_MAX, INDICATOR_STRETCH_MAX)
                        val stretchY = (v * INDICATOR_STRETCH_GAIN_Y)
                            .coerceIn(-INDICATOR_STRETCH_MAX, INDICATOR_STRETCH_MAX)
                        scaleX = pressAnimation.value / (1f - stretchX)
                        scaleY = pressAnimation.value * (1f - stretchY)
                    }
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = INDICATOR_TINT_ALPHA), CircleShape)
                    .pointerInput(tabsCount, isLtr) {
                        // Initial pass 捕获：拖拽消费事件；纯点击不消费，穿透给下方 NavBarItem
                        awaitEachGesture {
                            val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
                            val initialChange = initialEvent.changes.firstOrNull()?.takeIf { it.pressed }
                                ?: return@awaitEachGesture
                            val pointerId = initialChange.id.value
                            val downPos = initialChange.position
                            var previousPos = downPos
                            var dragStarted = false

                            pressAnimation.press()

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change =
                                    event.changes.firstOrNull { it.id.value == pointerId } ?: break

                                if (!change.pressed) {
                                    if (dragStarted) {
                                        change.consume()
                                        val target = (pagerPositionState.floatValue + dragOffset.value)
                                            .roundToInt().coerceIn(0, tabsCount - 1)
                                        Log.d(TAG, "FloatingNavBar: indicator drag release, target=$target")
                                        onSelectedUpdated(target)
                                        animationScope.launch {
                                            dragOffset.animateTo(0f, INDICATOR_SETTLE_SPRING)
                                        }
                                    } else {
                                        Log.d(TAG, "FloatingNavBar: indicator tap passthrough")
                                    }
                                    pressAnimation.release()
                                    break
                                }

                                val dx = change.position.x - previousPos.x
                                val totalDx = change.position.x - downPos.x
                                previousPos = change.position

                                if (!dragStarted && abs(totalDx) > viewConfiguration.touchSlop) {
                                    dragStarted = true
                                    change.consume()
                                    continue
                                }

                                if (dragStarted && tabWidthPx > 0f) {
                                    change.consume()
                                    val direction = if (isLtr) 1f else -1f
                                    // clamp：pill 总位置不得越出首尾 tab
                                    val lower = -pagerPositionState.floatValue
                                    val upper = (tabsCount - 1).toFloat() - pagerPositionState.floatValue
                                    val target = (dragOffset.value + dx / tabWidthPx * direction)
                                        .coerceIn(lower, upper)
                                    // snapTo/animateTo 是挂起函数，不能在受限的指针事件作用域内直接调用，
                                    // 统一经 animationScope 派发（与原 DampedDragAnimation 的 updateValue 模式一致）
                                    animationScope.launch {
                                        dragOffset.animateTo(target, INDICATOR_DRAG_FOLLOW_SPRING)
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

/** 指示器按压缩放：按压放大、释放恢复，job 互斥避免 spring 叠加 */
private class IndicatorPressAnimation(private val scope: CoroutineScope) {
    private val scale = Animatable(1f)
    private var job: Job? = null

    val value: Float get() = scale.value

    fun press() = animateTo(INDICATOR_PRESS_SCALE)

    fun release() = animateTo(1f)

    private fun animateTo(target: Float) {
        job?.cancel()
        job = scope.launch { scale.animateTo(target, INDICATOR_PRESS_SPRING) }
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
