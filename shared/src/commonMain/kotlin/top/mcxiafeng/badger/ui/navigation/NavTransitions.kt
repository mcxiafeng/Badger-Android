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

object NavTransitions {

    private const val TAG = "NavTransitionsTester"

    
    private const val DURATION_MS = BadgerMotion.DURATION_BASE

    
    private val pushSpec: FiniteAnimationSpec<IntOffset> = BadgerMotion.pushSpringOffset()

    
    private val modalSpec: FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = DURATION_MS, easing = FastOutSlowInEasing)

    

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

    

    fun push(): ContentTransform = motion("push") {
        horizontalSlide(
            enterInitial = { it },
            exitTarget = { -it / 4 },
            spec = pushSpec,
        )
    }

    

    fun pop(): ContentTransform = motion("pop") {
        horizontalSlide(
            enterInitial = { -it / 4 },
            exitTarget = { it },
            spec = pushSpec,
        )
    }

    

    fun reset(): ContentTransform = motion("reset") {
        fadeIn(tween(BadgerMotion.DURATION_BASE, easing = FastOutSlowInEasing)) togetherWith
            fadeOut(tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing))
    }

    

    fun none(): ContentTransform = fadeIn(tween(0)) togetherWith fadeOut(tween(0))

    

    fun mainToSub(): ContentTransform = push()

    

    fun modal(): ContentTransform = motion("modal") {
        horizontalSlide(
            enterInitial = { it },
            exitTarget = { -it / 4 },
            spec = modalSpec,
        )
    }

    

    fun subToMain(): ContentTransform = motion("subToMain") {
        horizontalSlide(
            enterInitial = { -it / 4 },
            exitTarget = { it },
            spec = pushSpec,
        )
    }
}
