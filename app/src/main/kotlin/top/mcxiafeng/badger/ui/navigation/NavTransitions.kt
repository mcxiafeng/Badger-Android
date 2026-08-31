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

    // 500ms 会让带弹性的页面转场明显偏慢；300ms 更适合频繁的二级页 push/pop。
    private const val DURATION_MS = 300

    /**
     * 前进动画：新页面从右侧滑入，旧页面向左退出（缩小偏移）
     */
    fun push(): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = DURATION_MS, easing = NavAnimationEasing)
        ),
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { -it / 4 },
            animationSpec = tween(durationMillis = DURATION_MS, easing = NavAnimationEasing)
        ),
        sizeTransform = SizeTransform(clip = false)
    )

    /**
     * 后退动画：新页面从左侧滑入，旧页面向右退出
     */
    fun pop(): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { -it / 4 },
            animationSpec = tween(durationMillis = DURATION_MS, easing = NavAnimationEasing)
        ),
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = DURATION_MS, easing = NavAnimationEasing)
        ),
        sizeTransform = SizeTransform(clip = false)
    )

    /**
     * 重置动画：淡入淡出（用于回到主页）
     */
    fun reset(): ContentTransform = fadeIn(tween(300)) togetherWith fadeOut(tween(200))

    /**
     * 无动画：瞬时切换
     */
    fun none(): ContentTransform = fadeIn(tween(0)) togetherWith fadeOut(tween(0))

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
            animationSpec = tween(durationMillis = DURATION_MS, easing = NavAnimationEasing)
        ),
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = DURATION_MS, easing = NavAnimationEasing)
        ),
        sizeTransform = SizeTransform(clip = false)
    )
}
