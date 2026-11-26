package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "WorkspacePullRefreshTester"

/**
 * 下拉刷新统一入口，封装 Miuix [PullToRefresh]。其状态机按级别同步 hoisted 的 refreshing：
 * 一帧内 true→false 的丢失脉冲、或回调未置起 refreshing 的空刷新，都会立即播放收尾动画，
 * 指示器不会停留在阈值位置（M3 1.3.1 的 `pullToRefresh` 依赖重组观察到 isRefreshing 跳变，
 * 存在卡在阈值的竞态）。
 *
 * [enabled] 只在当前没有刷新进行时才整体卸载组件；刷新进行中（回调把 busy 置真等
 * 导致 enabled 翻转）保持挂载，让收尾动画完整播放，指示器不会中途消失。
 */
@Composable
internal fun WorkspacePullRefresh(
    refreshing: StateFlow<Boolean>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 顶部悬浮栏高度：刷新头避让顶栏与状态栏，容器满屏接收手势。 */
    topInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val isRefreshing by refreshing.collectAsStateWithLifecycle()
    val mounted = enabled || isRefreshing
    LaunchedEffect(enabled, isRefreshing, topInset) {
        Log.d(TAG, "Pull refresh mounted=$mounted; enabled=$enabled; refreshing=$isRefreshing; topInset=${topInset.value}dp")
    }
    if (!mounted) {
        Box(modifier) { content() }
        return
    }
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        contentPadding = PaddingValues(top = topInset),
        color = MiuixTheme.colorScheme.primary,
        refreshTexts = listOf("下拉刷新", "释放刷新", "正在同步资料", "刷新完成"),
        refreshTextStyle = MiuixTheme.textStyles.subtitle,
        content = content,
    )
}
