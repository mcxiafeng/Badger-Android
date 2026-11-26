package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    foregroundColor: Color = MiuixTheme.colorScheme.primary,
    backgroundColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    height: Dp = ProgressIndicatorDefaults.DefaultLinearProgressIndicatorHeight,
) {
    val value = progress.coerceIn(0f, 1f)
    LaunchedEffect(height) { Log.d("LinearProgressIndicatorTester", "Continuous progress surface; heightDp=${height.value}") }
    Box(modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
    }.fillMaxWidth().height(height).drawWithCache {
        val radius = CornerRadius(BadgerRadius.surface.toPx())
        val background = Path().apply { addG2RoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }
        val foreground = Path()
        onDrawBehind {
            drawPath(background, backgroundColor)
            val width = minOf(size.height, size.width) + (size.width - minOf(size.height, size.width)) * value
            foreground.rewind()
            foreground.addG2RoundRect(RoundRect(0f, 0f, width, size.height, radius))
            drawPath(foreground, foregroundColor)
        }
    })
}
