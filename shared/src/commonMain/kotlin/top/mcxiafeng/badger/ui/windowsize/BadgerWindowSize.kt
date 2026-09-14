package top.mcxiafeng.badger.ui.windowsize

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "BadgerWindowSize"

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
val LocalBadgerWindowSizeClass = staticCompositionLocalOf {
    
    WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
internal fun windowSizeClassFromDp(width: Dp, height: Dp): WindowSizeClass =
    WindowSizeClass.calculateFromSize(DpSize(width, height))

internal fun gridColumnsForWidthClass(widthClass: WindowWidthSizeClass): Int = when (widthClass) {
    WindowWidthSizeClass.Compact -> 2
    WindowWidthSizeClass.Medium -> 3
    WindowWidthSizeClass.Expanded -> 4
    
    else -> 2
}

@Composable
fun rememberBadgerWindowSizeClass(): WindowSizeClass {
    var sizeDp by remember { mutableStateOf(DpSize(0.dp, 0.dp)) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        sizeDp = DpSize(maxWidth, maxHeight)
    }
    val sizeClass = windowSizeClassFromDp(sizeDp.width, sizeDp.height)
    LaunchedEffect(sizeDp) {
        BadgerLog.d(
            TAG,
            "window size ${sizeDp.width.value.toInt()}x${sizeDp.height.value.toInt()}dp -> " +
                "width=${sizeClass.widthSizeClass} height=${sizeClass.heightSizeClass}",
        )
    }
    return sizeClass
}
