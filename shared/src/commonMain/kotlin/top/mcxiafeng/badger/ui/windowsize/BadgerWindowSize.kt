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

/**
 * 全局窗口尺寸档位（K18 大屏适配的单一数据源）。
 *
 * 由 [rememberBadgerWindowSizeClass] 计算后经 [LocalBadgerWindowSizeClass] 下发，
 * 页面只需读 CompositionLocal 即可做双栏/网格/对话框宽度的响应式切换。
 * 类型来自 material3-window-size-class（CMP 1.9 稳定线），双端同一套枚举。
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
val LocalBadgerWindowSizeClass = staticCompositionLocalOf {
    // 默认档位按手机竖屏（Compact）——实际值始终由 App 根层注入
    WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
}

/**
 * 纯函数：dp 尺寸 → [WindowSizeClass]。
 * 走官方 [WindowSizeClass.calculateFromSize]（阈值 600/840/480/900 由库内定义，不重复实现）。
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
internal fun windowSizeClassFromDp(width: Dp, height: Dp): WindowSizeClass =
    WindowSizeClass.calculateFromSize(DpSize(width, height))

/**
 * 名片夹网格列数策略（K18）：Compact=2 / Medium=3 / Expanded=4。
 */
internal fun gridColumnsForWidthClass(widthClass: WindowWidthSizeClass): Int = when (widthClass) {
    WindowWidthSizeClass.Compact -> 2
    WindowWidthSizeClass.Medium -> 3
    WindowWidthSizeClass.Expanded -> 4
    // WindowWidthSizeClass 是 value class（非枚举）：将来扩档时兜底保持 Compact 行为
    else -> 2
}

/**
 * 在 App 根层计算当前窗口尺寸档位。
 * CMP 的 calculateWindowSizeClass() 尚未 common 化（Android 需 Activity），
 * 统一用 BoxWithConstraints 量取实际可用尺寸，双端行为一致。
 */
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
