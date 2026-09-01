package top.mcxiafeng.badger.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * 统一页面过渡动画定义
 *
 * 提供三种导航方向的动画:
 * - [push]: 从右向左滑入（前进）
 * - [pop]: 从左向右滑出（后退）
 * - [reset]: 淡入淡出（重置到主页）
 */
object NavTransitions {

    /** 普通二级页面切换动画时长，避免过长动画造成拖沓与 jank。 */
    private const val NAVIGATION_DURATION_MS = 300
    private const val RESET_ENTER_DURATION_MS = 300
    private const val RESET_EXIT_DURATION_MS = 200
    private const val NO_ANIMATION_DURATION_MS = 0

    /**
     * 前进动画：新页面从右侧滑入，旧页面向左退出（缩小偏移）
     */
    fun push(): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = NAVIGATION_DURATION_MS, easing = NavAnimationEasing),
        ),
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { -it / 4 },
            animationSpec = tween(durationMillis = NAVIGATION_DURATION_MS, easing = NavAnimationEasing),
        ),
        sizeTransform = SizeTransform(clip = false),
    )

    /**
     * 后退动画：新页面从左侧滑入，旧页面向右退出
     */
    fun pop(): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { -it / 4 },
            animationSpec = tween(durationMillis = NAVIGATION_DURATION_MS, easing = NavAnimationEasing),
        ),
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = NAVIGATION_DURATION_MS, easing = NavAnimationEasing),
        ),
        sizeTransform = SizeTransform(clip = false),
    )

    /**
     * 重置动画：淡入淡出（用于回到主页）
     */
    fun reset(): ContentTransform =
        fadeIn(tween(RESET_ENTER_DURATION_MS)) togetherWith fadeOut(tween(RESET_EXIT_DURATION_MS))

    /**
     * 无动画：瞬时切换
     */
    fun none(): ContentTransform =
        fadeIn(tween(NO_ANIMATION_DURATION_MS)) togetherWith fadeOut(tween(NO_ANIMATION_DURATION_MS))

    /**
     * 从主页进入二级页面的动画
     */
    fun mainToSub(): ContentTransform = push()

    /**
     * 从二级页面返回主页的动画
     */
    fun subToMain(): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { -it / 4 },
            animationSpec = tween(durationMillis = NAVIGATION_DURATION_MS, easing = NavAnimationEasing),
        ),
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = NAVIGATION_DURATION_MS, easing = NavAnimationEasing),
        ),
        sizeTransform = SizeTransform(clip = false),
    )
}