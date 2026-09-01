package top.mcxiafeng.badger

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import top.mcxiafeng.badger.ui.blur.BlurIntensity
import top.mcxiafeng.badger.ui.blur.GpuCompat
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun rememberAppVisualEffects(
    context: Context,
    pagerState: PagerState,
): AppVisualEffects {
    val floatingEnabled by NavBarConfig.floatingFlow.collectAsState(initial = true)
    val liquidGlassEnabled by NavBarConfig.liquidGlassFlow.collectAsState(initial = true)
    val blurIntensity by NavBarConfig.blurIntensityFlow.collectAsState(initial = BlurIntensity.THICK)
    val advancedBlurEnabled by NavBarConfig.advancedBlurFlow.collectAsState(initial = false)
    val effectMode by NavBarConfig.effectModeFlow.collectAsState(initial = EffectMode.BG_BLUR)

    val gpuAdvancedSupported = remember(context) {
        GpuCompat.isAdvancedBlurSupported(context)
    }
    val effectiveAdvancedBlur = advancedBlurEnabled && gpuAdvancedSupported
    val hazeState = rememberHazeState()
    val backdrop: LayerBackdrop? = if (effectiveAdvancedBlur) rememberLayerBackdrop() else null

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, liquidGlassEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> hazeState.blurEnabled = false
                Lifecycle.Event.ON_START -> hazeState.blurEnabled = liquidGlassEnabled
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        hazeState.blurEnabled = liquidGlassEnabled
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { isScrolling = it }
    }

    return AppVisualEffects(
        floatingEnabled = floatingEnabled,
        liquidGlassEnabled = liquidGlassEnabled,
        blurIntensity = blurIntensity,
        effectMode = effectMode,
        hazeState = hazeState,
        backdrop = backdrop,
        isScrolling = isScrolling,
    )
}

data class AppVisualEffects(
    val floatingEnabled: Boolean,
    val liquidGlassEnabled: Boolean,
    val blurIntensity: BlurIntensity,
    val effectMode: EffectMode,
    val hazeState: HazeState,
    val backdrop: LayerBackdrop?,
    val isScrolling: Boolean,
)