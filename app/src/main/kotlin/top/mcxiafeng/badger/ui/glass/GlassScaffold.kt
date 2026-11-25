package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberGlassBackdrop(containerColor, progressiveTopBlur || LocalGlassStyle.current.enabled)
    val pageBackdrop = LocalPageBackdrop.current
    DisposableEffect(pageBackdrop, backdrop) {
        pageBackdrop?.value = backdrop
        onDispose { if (pageBackdrop?.value === backdrop) pageBackdrop?.value = null }
    }
    MiuixScaffold(
        modifier = modifier,
        topBar = { GlassChrome(backdrop, transparentTopBar = progressiveTopBlur) {
            Box {
                if (progressiveTopBlur) Box(Modifier.matchParentSize().progressiveTopBlur())
                topBar()
            }
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
        ) {
            GlassBackdropContent(backdrop) { content(padding) }
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
