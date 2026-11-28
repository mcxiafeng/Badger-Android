package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SCAFFOLD_LOG_TAG = "GlassScaffoldTester"

/**
 * 页面骨架：Miuix Scaffold 槽位之上接线轻量顶部效果——滚动视口渐隐（scrollTopFade）、
 * 共享顶区（TopEffectRegion）与滚动驱动强度（TopEdgeScroll），顶栏保持透明覆盖。
 * 顶区渐进高斯已按用户要求移除（2026-11-28），历史实现见 liquid-glass-interface 分支。
 */
@Composable
internal fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    topEffects: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val topEffectRegion = remember { mutableStateOf<TopEffectRegion?>(null) }
    // The connection sits above every scroll container, so one shared state deepens the fade as
    // content scrolls under the bar; consumed deltas only, so empty pages stay at the baseline.
    val topEdgeScroll = remember(topEffects, density) {
        if (topEffects) TopEdgeScrollState(with(density) { BadgerSize.topEdgeScrollTravel.toPx() }) else null
    }
    LaunchedEffect(topEdgeScroll) { topEdgeScroll?.logAttached() }
    MiuixScaffold(
        modifier = modifier,
        topBar = {
            CompositionLocalProvider(LocalTransparentTopBar provides topEffects) {
                Box(Modifier.onSizeChanged { topBarHeightPx = it.height }) { topBar() }
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        floatingToolbar = floatingToolbar,
        floatingToolbarPosition = floatingToolbarPosition,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
    ) { padding ->
        CompositionLocalProvider(
            // A nested page owns its own region; it must never inherit the host's fade.
            LocalTopEffectRegion provides if (topEffects && topBarHeightPx > 0) topEffectRegion else null,
            LocalTopEdgeScroll provides topEdgeScroll,
        ) {
            Box(Modifier.fillMaxSize().then(
                if (topEdgeScroll != null) Modifier.nestedScroll(topEdgeScroll.connection) else Modifier
            )) {
                content(padding)
                // An invisible measurer that establishes the shared top region the scroll
                // viewports fade into; it draws nothing on its own.
                if (topEffects && topBarHeightPx > 0) {
                    LaunchedEffect(Unit) {
                        Log.d(SCAFFOLD_LOG_TAG, "Top fade region attached; extentDp=${BadgerSize.topEffectBottom.value}; travelDp=${BadgerSize.topEdgeScrollTravel.value}; gaussianRemoved=true")
                    }
                    Box(Modifier.fillMaxWidth()
                        .height(BadgerSize.topEffectBottom)
                        .onGloballyPositioned {
                            topEffectRegion.value = TopEffectRegion(with(density) { BadgerSize.topEffectBottom.toPx() })
                        })
                }
            }
        }
    }
}
