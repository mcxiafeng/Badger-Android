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
 * 顶区渐进高斯（progressiveTopBlur）与滚动驱动强度（TopEdgeScroll），顶栏保持透明覆盖。
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
    progressiveTopBlur: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    // The blur layer samples the recorded page content; recording is only needed for pages that blur.
    val backdrop = rememberGlassBackdrop(containerColor, progressiveTopBlur)
    // The blur keeps its strongest point at the screen top, so it covers the status bar area too.
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val topEffectRegion = remember { mutableStateOf<TopEffectRegion?>(null) }
    // The connection sits above every scroll container, so one shared state deepens the mist as
    // content scrolls under the bar; consumed deltas only, so empty pages stay at the baseline.
    val topEdgeScroll = remember(progressiveTopBlur, density) {
        if (progressiveTopBlur) TopEdgeScrollState(with(density) { BadgerSize.topEdgeScrollTravel.toPx() }) else null
    }
    LaunchedEffect(topEdgeScroll) { topEdgeScroll?.logAttached() }
    MiuixScaffold(
        modifier = modifier,
        topBar = {
            CompositionLocalProvider(LocalTransparentTopBar provides progressiveTopBlur) {
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
            LocalGlassBackdrop provides null,
            // A nested page owns its own region; it must never inherit the host's fade.
            LocalTopEffectRegion provides if (progressiveTopBlur && topBarHeightPx > 0) topEffectRegion else null,
            LocalTopEdgeScroll provides topEdgeScroll,
        ) {
            Box(Modifier.fillMaxSize().then(
                if (topEdgeScroll != null) Modifier.nestedScroll(topEdgeScroll.connection) else Modifier
            )) {
                // Scroll viewports fade before recording; pinned siblings remain unchanged.
                GlassBackdropContent(backdrop) { content(padding) }
                if (progressiveTopBlur && topBarHeightPx > 0) {
                    LaunchedEffect(Unit) { Log.d(SCAFFOLD_LOG_TAG, "Top effects attached; blurRadiusDp=${BadgerSize.topProgressiveBlurRadius.value}; travelDp=${BadgerSize.topEdgeScrollTravel.value}") }
                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        Box(Modifier.fillMaxWidth()
                            .height(BadgerSize.topEffectBottom)
                            .onGloballyPositioned {
                                topEffectRegion.value = TopEffectRegion(with(density) { BadgerSize.topEffectBottom.toPx() })
                            }
                            .progressiveTopBlur(topEffectRegion, topEdgeScroll))
                    }
                }
            }
        }
    }
}
