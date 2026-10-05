package top.mcxiafeng.badger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion

private val ENTRANCE_RISE = 16.dp

/**
 * 卡片进场：延迟 [delayMillis] 后一次性淡入上移，用于网格 / 列表的错落节奏。
 *
 * 动画值仅在绘制期读取（graphicsLayer），不触发重组；item 滚出视口再回来不重播。
 */
@Composable
fun EntranceReveal(
    delayMillis: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis)
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(BadgerMotion.DURATION_BASE),
        label = "entranceReveal",
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * ENTRANCE_RISE.toPx()
        },
    ) {
        content()
    }
}
