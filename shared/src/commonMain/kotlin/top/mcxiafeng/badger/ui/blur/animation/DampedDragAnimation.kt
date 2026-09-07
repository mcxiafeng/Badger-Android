package top.mcxiafeng.badger.ui.blur.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

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

    // --- 拖拽阶段的即时值（绕过 Animatable 的 MutatorMutex，零延迟赋值） ---
    // [FIX] dragValue 必须是 State-backed（mutableFloatStateOf），否则拖拽中赋值不触发
    // graphicsLayer 重新执行 → 指示器冻结，不跟随手指。plain Float 字段对 Compose
    // snapshot 系统不可见，读写都不触发重组/重绘。

    private val dragValueState = mutableFloatStateOf(initialValue.coerceIn(valueRange))
    private var dragValue: Float
        get() = dragValueState.floatValue
        set(value) { dragValueState.floatValue = value.coerceIn(valueRange) }

    // --- 公开只读状态 ---

    /**
     * 拖拽中返回手指对应的即时位置；非拖拽时返回弹簧动画的当前值。
     * 这是整个类的核心改动：拖拽阶段不再经过 Animatable，直接读 dragValue。
     */
    val value: Float
        get() = if (isDragging) dragValue else valueAnimation.value

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
        // [FIX] 进入拖拽前把 dragValue 同步到动画当前值。
        // value getter 在 isDragging=true 时读 dragValue，false 时读 valueAnimation。
        // 若 animateToValue 只更新了 valueAnimation 而 dragValue 停在旧值，
        // 下次 press 时 isDragging 翻 true，value 瞬间跳到 stale dragValue → 指示器闪跳。
        dragValue = valueAnimation.value
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
        releaseJob = animationScope.launch {
            withFrameMillis { }
            // 等待 valueAnimation 收敛到 targetValue（弹簧回弹完成）
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .first { abs(it - valueAnimation.targetValue) < threshold }
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }

            // 所有视觉动画启动后，计算最终 settle 索引并回调
            val settledIndex = targetValue.roundToInt()
                .coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
            onSettled(settledIndex)
        }
    }

    // --- 值驱动 ---

    /**
     * 拖拽跟手：直接赋值 dragValue（零延迟、无锁），同时手动喂速度采样器。
     * 仅在手势拖拽循环中调用；程序化跳转请用 animateToValue / snapToValue。
     */
    fun updateValue(value: Float) {
        dragValue = value.coerceIn(valueRange)
        updateVelocity()
    }

    /**
     * 程序化跳转（点击 Tab / 外部 selectedIndex 同步）。
     * 带按压脉冲的弹簧动画：先把 Animatable 同步到当前位置，再弹向目标。
     */
    fun animateToValue(value: Float) {
        val target = value.coerceIn(valueRange)
        // [FIX] 在 launch 外捕获——参数 'value' 遮蔽属性 'value'，launch 内 this 是
        // CoroutineScope 不是 DampedDragAnimation。用 value（不是 dragValue）同步：
        // isDragging=false 时读 valueAnimation.value（当前视觉位置），snapTo 是 no-op；
        // isDragging=true 时读 dragValue（手指位置）。旧代码用 dragValue 导致 stale 回跳。
        val currentVisualValue = this.value
        val currentVelocity = this.velocity
        animationScope.launch {
            mutatorMutex.mutate {
                valueAnimation.snapTo(currentVisualValue)
                velocityAnimation.snapTo(currentVelocity)
            }
            // 再启动弹簧动画（也在互斥锁内，等 snapTo 完成后才执行）
            mutatorMutex.mutate {
                press()
                // [FIX] settle 阶段不调 updateVelocity() —— 拖拽已结束，velocityTracker
                // 不再接收新数据，继续 snapTo 只会取消 velocityAnimation.animateTo(0f)
                // 导致速度衰减被反复打断、velocity 值跳变驱动 scaleX/scaleY 形变"震"。
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                release()
            }
        }
    }

    /** 效果模式 = 无 时直切，水滴停摆，所有状态瞬时归位。 */
    fun snapToValue(value: Float) {
        isDragging = false
        releaseJob?.cancel()
        pressJob?.cancel()
        val clamped = value.coerceIn(valueRange)
        dragValue = clamped
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

    private fun updateVelocity() {
        velocityTracker.addPosition(nowMillis(), Offset(value, 0f))
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
        val targetVelocity = velocityTracker.calculateVelocity().x / span
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            velocityAnimation.snapTo(targetVelocity)
        }
    }
}