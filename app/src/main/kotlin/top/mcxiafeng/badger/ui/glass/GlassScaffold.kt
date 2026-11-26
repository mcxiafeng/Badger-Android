package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.LocalOverscrollFactory
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

internal val LocalPageBackdrop = staticCompositionLocalOf<MutableState<GlassBackdrop?>?> { null }

@Composable
internal fun GlassPage(glassButtons: Boolean = true, content: @Composable () -> Unit) {
    val backdrop = remember { mutableStateOf<GlassBackdrop?>(null) }
    val style = LocalGlassStyle.current
    if (!glassButtons) {
        LaunchedEffect(style.enabled) { Log.d(GLASS_LOG_TAG, "Page uses flat buttons; glass surfaces=${style.enabled}") }
    }
    CompositionLocalProvider(
        LocalPageBackdrop provides backdrop,
        LocalGlassStyle provides if (glassButtons) style else style.copy(glassButtons = false),
        content = content,
    )
}

internal inline fun <reified T : NavKey> NavEntryBuilder.glassEntry(
    glassButtons: Boolean = true,
    crossinline content: @Composable (T) -> Unit,
) {
    entry<T> { route -> GlassPage(glassButtons) { content(route) } }
}

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
    popupHost: @Composable () -> Unit = { GlassPopupHost() },
    containerColor: Color = MiuixTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    progressiveTopBlur: Boolean = true,
    overscrollSpring: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberGlassBackdrop(containerColor, progressiveTopBlur || LocalGlassStyle.current.opticsEnabled)
    val pageBackdrop = LocalPageBackdrop.current
    DisposableEffect(pageBackdrop, backdrop) {
        pageBackdrop?.value = backdrop
        onDispose { if (pageBackdrop?.value === backdrop) pageBackdrop?.value = null }
    }
    // The blur keeps its strongest point at the screen top, so it covers the status bar area too.
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val topEffectRegion = remember { mutableStateOf<TopEffectRegion?>(null) }
    MiuixScaffold(
        modifier = modifier,
        topBar = { GlassChrome(backdrop, transparentTopBar = progressiveTopBlur) {
            Box(Modifier.onSizeChanged { topBarHeightPx = it.height }) { topBar() }
        } },
        bottomBar = bottomBar,
        floatingActionButton = { GlassChrome(backdrop, content = floatingActionButton) },
        floatingActionButtonPosition = floatingActionButtonPosition,
        floatingToolbar = { GlassChrome(backdrop, content = floatingToolbar) },
        floatingToolbarPosition = floatingToolbarPosition,
        snackbarHost = snackbarHost,
        popupHost = popupHost,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
    ) { padding ->
        CompositionLocalProvider(
            LocalGlassBackdrop provides null,
            LocalMenuBackdrop provides backdrop,
            LocalGlassUnderlay provides containerColor,
            // A nested page owns its own region; it must never inherit the host's fade.
            LocalTopEffectRegion provides if (progressiveTopBlur && topBarHeightPx > 0) topEffectRegion else null,
            // The spring below owns edge feedback; the platform stretch/glow would double it.
            LocalOverscrollFactory provides null,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Scroll viewports fade before recording; pinned siblings remain unchanged.
                ProvideSpringOverscroll(overscrollSpring) {
                    GlassBackdropContent(backdrop) { content(padding) }
                }
                if (progressiveTopBlur && topBarHeightPx > 0) {
                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        Box(Modifier.fillMaxWidth()
                            .height(BadgerSize.topEffectBottom)
                            .onGloballyPositioned {
                                topEffectRegion.value = TopEffectRegion(with(density) { BadgerSize.topEffectBottom.toPx() })
                            }
                            .progressiveTopBlur(topEffectRegion))
                    }
                }
            }
        }
    }
}

/** Miuix's popup container otherwise draws an opaque surface behind the sampled glass. */
@Composable
internal fun GlassPopupHost() {
    val colors = MiuixTheme.colorScheme
    MiuixTheme(colors = colors.copy(surfaceContainer = Color.Transparent)) { MiuixPopupHost() }
}

@Composable
private fun GlassChrome(
    backdrop: GlassBackdrop?,
    transparentTopBar: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalMenuBackdrop provides backdrop,
        LocalGlassUnderlay provides (backdrop?.color ?: Color.Unspecified),
        LocalTransparentTopBar provides transparentTopBar,
        content = content,
    )
}
