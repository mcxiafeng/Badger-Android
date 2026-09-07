package top.mcxiafeng.badger.ui.blur.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * 阻尼拖拽动画控制器。
 *
 * 核心设计：拖拽阶段用普通 Float 字段直接赋值（零延迟、无锁），
 * 只在 settle 回弹时才启用弹簧动画。彻底消除 Animatable.snapTo 的
 * MutatorMutex 排队导致的跟手滞后。
 *
 * 手势规则：
 * - down 即触发 press（透镜按压效果），但不消费事件；
 * - 水平位移超过 touchSlop 后才进入拖拽态并 consume，子项 tap 自动取消；
 * - 未超过 slop 直接抬起 → 不消费任何事件 → 子项 onClick 正常触发；
 * - up / cancel / 事件被抢 → 统一走 onDragStopped + release，settle 只有一个入口。
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val canDrag: (Offset) -> Boolean = { true },
    val onDragStarted: DampedDragAnimation.(position: Offset, size: IntSize) -> Unit = { _, _ -> },
    val onDragStopped: DampedDragAnimation.() -> Unit = {},
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    val onSettled: DampedDragAnimation.(settledIndex: Int) -> Unit = {},
) {

    // --- 动画规格 ---

    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    // [FIX] dampingRatio 1.0（临界阻尼）——原来 0.5 欠阻尼导致速度在 0 附近振荡，
    // 快速甩动后释放指示器会"震"。速度不需要弹性回弹，只需平滑衰减到 0。
    private val velocityAnimationSpec = spring(1f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    // [FIX] dampingRatio 1.0（临界阻尼）——原来 0.6/0.7 欠阻尼，scale 回弹过冲振荡
    private val scaleXAnimationSpec = spring(1f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(1f, 250f, 0.001f)

    // --- 动画实例 ---
    // valueAnimation 仅用于 settle 阶段的弹簧回弹，拖拽阶段不使用

    private val valueAnimation = Animatable(
        initialValue.coerceIn(valueRange), visibilityThreshold
    )
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()

    private var pressJob: Job? = null
    private var releaseJob: Job? = null

    private val velocityTracker = VelocityTracker()
    private val startMark = TimeSource.Monotonic.markNow()

    private fun nowMillis(): Long = startMark.elapsedNow().inWholeMilliseconds

    // --- 公开只读状态 ---

    /**
     * 当前指示器位置（tab 索引浮点值）。
     * [FIX] 统一单值源——永远读 valueAnimation.value（State-backed Animatable）。
     * 拖拽跟手用 snapTo（即时赋值，无弹簧延迟），settle 用 animateTo（弹簧回弹）。
     * 消除了 dragValue/valueAnimation 双值源同步问题。
     */
    val value: Float get() = valueAnimation.value

    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    /** 手指是否按住（含拖拽中）。down 即 true，up/cancel 即 false。 */
    var isDragging: Boolean by mutableStateOf(false)
        private set

    /**
     * 稳定选中索引：非按压中、动画已收敛时返回整数索引，否则返回 null。
     * 上层可用 snapshotFlow { stableIndex } 做调试/预览，
     * 业务回调请使用 onSettled 回调（更可靠）。
     */
    val stableIndex: Int? by derivedStateOf {
        if (isDragging) return@derivedStateOf null
        val settled = abs(value - targetValue) < visibilityThreshold && abs(velocity) < 0.05f
        if (settled) {
            value.roundToInt()
                .coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
        } else {
            null
        }
    }

    // --- 统一手势入口 ---

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!canDrag(down.position)) return@awaitEachGesture

            val pointerId = down.id
            val downPos = down.position
            var prevPos = downPos
            var dragStarted = false

            // 触摸即按压（透镜效果），但不消费 down，子项 tap 手势仍可正常建立
            press()
            onDragStarted(downPos, size)

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.fastFirstOrNull { it.id == pointerId } ?: break

                if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                    if (dragStarted) change.consume()
                    break
                }

                if (change.isConsumed) {
                    // 被父级/兄弟节点抢走，视为取消
                    break
                }

                if (!dragStarted) {
                    if (abs(change.position.x - downPos.x) > slop && canDrag(change.position)) {
                        dragStarted = true
                        change.consume()
                    }
                } else if (canDrag(change.position)) {
                    change.consume()
                    onDrag(size, change.position - prevPos)
                }
                prevPos = change.position
            }

            // 唯一 settle 出口：up / cancel / 事件被抢，全部收敛到这里
            onDragStopped()
            release()
        }
    }

    // --- 按压视觉 ---

    fun press() {
        isDragging = true
        releaseJob?.cancel()
        pressJob?.cancel()
        velocityTracker.resetTracking()
        // 锚定当前值为速度采样的起点，避免首帧速度跳变
        velocityTracker.addPosition(nowMillis(), Offset(value, 0f))
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        isDragging = false
        releaseJob?.cancel()
        // [FIX] scale/pressProgress 立即并行衰减——不等 value 收敛。
        // 原来等 value 收敛后才解压，到达目的地时指示器还压着（scale=0.92），
        // 突然弹回 → "顿挫感" + 主题色底色可见（pressProgress=1 时 tint alpha 高）。
        pressJob?.cancel()
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
        // onSettled 单独等 value 收敛后回调
        releaseJob = animationScope.launch {
            withFrameMillis { }
            // [FIX] 只在有动画运行时回调 onSettled——tap（无动画）时 value==targetValue，
            // 跳过 onSettled，让 NavBarItem.onTap → animateToValue 统一处理导航。
            // 否则 onSettled(fingerIndex.roundToInt()) 可能和 onTap(index) 不一致 → 双跳。
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .first { abs(it - valueAnimation.targetValue) < threshold }
                val settledIndex = targetValue.roundToInt()
                    .coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
                onSettled(settledIndex)
            }
        }
    }

    // --- 值驱动 ---

    /**
     * 拖拽跟手：snapTo 即时赋值（无弹簧延迟），同时喂速度采样器。
     * [FIX] 用 valueAnimation.snapTo（State-backed Animatable）——赋值即触发
     * graphicsLayer 重绘，指示器跟手。UNDISPATCHED 确保 snapTo 在当前帧完成。
     * 消除了 dragValue 双值源同步问题。
     */
    fun updateValue(value: Float) {
        val clamped = value.coerceIn(valueRange)
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.snapTo(clamped)
        }
        updateVelocity(clamped)
    }

    /**
     * 程序化跳转（点击 Tab / 外部 selectedIndex 同步 / 拖拽释放 settle）。
     * [FIX] 不调 release()——release 内 releaseJob 与 animateTo 竞态：
     * withFrameMillis 后 animateTo 可能还没更新 targetValue，releaseJob 误判
     * value==targetValue 跳过 onSettled。改为自己管 scale 解压 + 等 value 收敛。
     */
    fun animateToValue(value: Float) {
        val target = value.coerceIn(valueRange)
        val currentVisualValue = this.value
        val currentVelocity = this.velocity
        animationScope.launch {
            mutatorMutex.mutate {
                valueAnimation.snapTo(currentVisualValue)
                velocityAnimation.snapTo(currentVelocity)
            }
            mutatorMutex.mutate {
                isDragging = false
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
            }
            // 并行解压 scale/pressProgress（不等 value 收敛）
            pressJob?.cancel()
            pressJob = animationScope.launch {
                launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
                launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
            }
            // 等 value 收敛后 onSettled——直接等 target（局部变量，无竞态）
            releaseJob?.cancel()
            releaseJob = animationScope.launch {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .first { abs(it - target) < threshold }
                val settledIndex = target.roundToInt()
                    .coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
                onSettled(settledIndex)
            }
        }
    }

    /** 效果模式 = 无 时直切，水滴停摆，所有状态瞬时归位。 */
    fun snapToValue(value: Float) {
        isDragging = false
        releaseJob?.cancel()
        pressJob?.cancel()
        val clamped = value.coerceIn(valueRange)
        animationScope.launch {
            mutatorMutex.mutate {
                valueAnimation.snapTo(clamped)
                velocityAnimation.snapTo(0f)
                pressProgressAnimation.snapTo(0f)
                scaleXAnimation.snapTo(initialScale)
                scaleYAnimation.snapTo(initialScale)
            }
            val settledIndex = clamped.roundToInt()
                .coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
            onSettled(settledIndex)
        }
    }

    private fun updateVelocity(currentValue: Float) {
        velocityTracker.addPosition(nowMillis(), Offset(currentValue, 0f))
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
        val targetVelocity = velocityTracker.calculateVelocity().x / span
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            velocityAnimation.snapTo(targetVelocity)
        }
    }
}