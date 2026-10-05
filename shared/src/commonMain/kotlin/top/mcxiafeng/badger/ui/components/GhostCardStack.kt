package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val GHOST_STACK_WIDTH = 108.dp
private val GHOST_STACK_HEIGHT = 76.dp
private val GHOST_CARD_ROTATIONS = listOf(-10f, 8f, 0f)
private val GHOST_CARD_OFFSETS = listOf((-14).dp to 6.dp, 12.dp to 2.dp, 0.dp to 0.dp)
private const val GHOST_CARD_BORDER_ALPHA = 0.35f

/** 空态装饰：三张错位斜叠的"幽灵卡"，配合空状态文案使用。 */
@Composable
fun GhostCardStack(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(width = GHOST_STACK_WIDTH, height = GHOST_STACK_HEIGHT)) {
        GHOST_CARD_ROTATIONS.forEachIndexed { index, rotation ->
            val (offsetX, offsetY) = GHOST_CARD_OFFSETS[index]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = offsetX, y = offsetY)
                    .rotate(rotation)
                    .clip(RoundedCornerShape(BadgerRadius.inner))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .border(
                        width = 1.dp,
                        color = MiuixTheme.colorScheme.primary.copy(alpha = GHOST_CARD_BORDER_ALPHA),
                        shape = RoundedCornerShape(BadgerRadius.inner),
                    ),
            )
        }
    }
}
