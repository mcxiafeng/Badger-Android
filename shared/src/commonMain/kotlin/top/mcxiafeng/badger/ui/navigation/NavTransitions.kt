package top.mcxiafeng.badger.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.utils.BadgerLog

/** 页面过渡动画：push(前进)、pop(后退)、reset(回主页)、modal(扫码等模态进入)。 */
object NavTransitions {

    private const val TAG = "NavTransitionsTester"

    // [U05] 时长单一来源 = BadgerMotion.DURATION_BASE（原 500ms 弹簧过冲振荡过久收敛至 300ms）
    private const val DURATION_MS = BadgerMotion.DURATION_BASE

    /** U09：push/pop 低弹 spring，无可见振荡 */
    private val pushSpec: FiniteAnimationSpec<IntOffset> = BadgerMotion.pushSpringOffset()

    /** U09：模态类（Scanner 进入）tween + FastOutSlowIn */
    private val modalSpec: FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = DURATION_MS, easing = FastOutSlowInEasing)

    /**
     * U11：效果模式 = 无 时直切。读取 [NavBarConfig.effectModeFlow] 当前值，
     * 模式恢复后下一次转场即恢复动画，无需重启。
     */
    private fun reducedMotion(): Boolean =
        NavBarConfig.effectModeFlow.value == EffectMode.NONE

    private fun motion(label: String, build: () -> ContentTransform): ContentTransform {
        if (reducedMotion()) {
            BadgerLog.d(TAG, "reduced motion: $label -> none()")
            return none()
        }
        return build()
    }

    private fun horizontalSlide(
        enterInitial: (fullWidth: Int) -> Int,
        exitTarget: (fullWidth: Int) -> Int,
        spec: FiniteAnimationSpec<IntOffset>,
    ): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(
            initialOffsetX = enterInitial,
            animationSpec = spec,
        ),
        initialContentExit = slideOutHorizontally(
            targetOffsetX = exitTarget,
            animationSpec = spec,
        ),
        sizeTransform = SizeTransform(clip = false),
    )

    /**
     * 前进动画：新页面从右侧滑入，旧页面向左退出（缩小偏移）
     */
    fun push(): ContentTransform = motion("push") {
        horizontalSlide(
            enterInitial = { it },
            exitTarget = { -it / 4 },
            spec = pushSpec,
        )
    }

    /**
     * 后退动画：新页面从左侧滑入，旧页面向右退出
     */
    fun pop(): ContentTransform = motion("pop") {
        horizontalSlide(
            enterInitial = { -it / 4 },
            exitTarget = { it },
            spec = pushSpec,
        )
    }

    /**
     * 重置动画：淡入淡出（用于回到主页）
     */
    fun reset(): ContentTransform = motion("reset") {
        fadeIn(tween(BadgerMotion.DURATION_BASE, easing = FastOutSlowInEasing)) togetherWith
            fadeOut(tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing))
    }

    /**
     * 无动画：瞬时切换
     */
    fun none(): ContentTransform = fadeIn(tween(0)) togetherWith fadeOut(tween(0))

    /**
     * 从主页进入二级页面的动画
     */
    fun mainToSub(): ContentTransform = push()

    /**
     * 模态进入（扫码页等）：tween + FastOutSlowIn，无弹簧位移
     */
    fun modal(): ContentTransform = motion("modal") {
        horizontalSlide(
            enterInitial = { it },
            exitTarget = { -it / 4 },
            spec = modalSpec,
        )
    }

    /**
     * 从二级页面返回主页的动画
     */
    fun subToMain(): ContentTransform = motion("subToMain") {
        horizontalSlide(
            enterInitial = { -it / 4 },
            exitTarget = { it },
            spec = pushSpec,
        )
    }
}
