package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

private const val TAG = "TopEdgeScrollTester"
private const val FULL_STRENGTH = 1f

/** Null on hosts without a top effect (Pager host, scanner): scroll tracking is disabled there. */
internal val LocalTopEdgeScroll = staticCompositionLocalOf<TopEdgeScrollState?> { null }

/**
 * 顶栏模糊的滚动驱动强度。所有页面共享同一基础磨砂（[TOP_EDGE_BASE_STRENGTH]），
 * 保证页面间与底栏玻璃的观感统一；内容滚入顶区（0..topEdgeScrollTravel）再把强度
 * 从基线平滑加深到满强度。读取方只依赖 [strength]，在绘制阶段取值，因此滚动帧只
 * 触发重绘不触发重组。只统计被滚动容器真实消费的位移（onPostScroll 的 consumed），
 * 空列表上的拖拽、下拉刷新的回拉不会虚增强度。
 */
@Stable
internal class TopEdgeScrollState(private val travelPx: Float) {
    private var scrolledPx by mutableFloatStateOf(0f)
    private var announcedFull = false
    private var announcedBase = true

    /** [TOP_EDGE_BASE_STRENGTH] at rest, 1 once content has scrolled under the bar. */
    val strength: Float
        get() {
            if (travelPx <= 0f) return FULL_STRENGTH
            val t = (scrolledPx / travelPx).coerceIn(0f, FULL_STRENGTH)
            val eased = t * t * (3f - 2f * t)
            return TOP_EDGE_BASE_STRENGTH + (FULL_STRENGTH - TOP_EDGE_BASE_STRENGTH) * eased
        }

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y != 0f) {
                scrolledPx = (scrolledPx - consumed.y).coerceIn(0f, travelPx)
                announce(strength)
            }
            return Offset.Zero
        }
    }

    fun logAttached() {
        Log.d(TAG, "Scroll edge effect attached; travelPx=$travelPx; baseStrength=$TOP_EDGE_BASE_STRENGTH; " +
            "range=$TOP_EDGE_BASE_STRENGTH..$FULL_STRENGTH; drivenBy=consumedScrollOnly")
    }

    /** Only log the two perceptual endpoints, never per frame. */
    private fun announce(strength: Float) {
        if (strength >= FULL_STRENGTH && !announcedFull) {
            announcedFull = true
            announcedBase = false
            Log.d(TAG, "Top edge fully engaged; scrolledPx=$scrolledPx")
        } else if (strength <= TOP_EDGE_BASE_STRENGTH && !announcedBase) {
            announcedBase = true
            announcedFull = false
            Log.d(TAG, "Top edge back to base strength; scrolledPx=$scrolledPx")
        }
    }
}

/** Every page keeps this much mist at rest, so the top and the glass bottom bar always coexist. */
internal const val TOP_EDGE_BASE_STRENGTH = 0.7f
