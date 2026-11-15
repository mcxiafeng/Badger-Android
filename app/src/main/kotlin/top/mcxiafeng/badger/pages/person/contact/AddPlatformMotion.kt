package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing

/** 只在步骤改变时转场；输入更新不触发整页动画，退出内容保留自己的状态快照。 */
@Composable
internal fun PlatformStepContent(
    state: AddPlatformUiState,
    modifier: Modifier = Modifier,
    content: @Composable (AddPlatformUiState) -> Unit
) {
    val savedSteps = rememberSaveableStateHolder()
    val offset = with(LocalDensity.current) { BadgerSpacing.lg.roundToPx() }
    AnimatedContent(
        targetState = state,
        contentKey = { it.step },
        modifier = modifier,
        label = "Platform step",
        transitionSpec = {
            val direction = if (targetState.step.ordinal > initialState.step.ordinal) 1 else -1
            (fadeIn(tween(BadgerMotion.DURATION_FAST)) +
                slideInVertically(tween(BadgerMotion.DURATION_FAST)) { direction * offset }) togetherWith
                (fadeOut(tween(BadgerMotion.DURATION_FAST)) +
                    slideOutVertically(tween(BadgerMotion.DURATION_FAST)) { -direction * offset })
        }
    ) { snapshot ->
        savedSteps.SaveableStateProvider(snapshot.step.name) { content(snapshot) }
    }
}

internal fun LazyItemScope.platformItemMotion(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(BadgerMotion.DURATION_FAST),
    placementSpec = tween(BadgerMotion.DURATION_FAST),
    fadeOutSpec = tween(BadgerMotion.DURATION_FAST)
)
