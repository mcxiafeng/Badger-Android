package top.mcxiafeng.badger

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import top.mcxiafeng.badger.data.AppearanceMode
import top.mcxiafeng.badger.data.AppearanceState
import top.mcxiafeng.badger.pages.settings.AppearanceViewModel
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private const val TAG = "AppThemeTester"
private const val LIGHT_ACCENT_CONTAINER_BLEND = 0.94f
private const val DARK_ACCENT_CONTAINER_BLEND = 0.78f
private const val DISABLED_ACCENT_BLEND = 0.60f

/** 正式入口通过 Hilt 观察外观；预览和独立 UI 宿主可直接使用默认主题。 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val owner = LocalViewModelStoreOwner.current
    val appearance = if (!LocalInspectionMode.current && owner is MainActivity) {
        val viewModel: AppearanceViewModel = hiltViewModel(owner)
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        state.appearance
    } else {
        AppearanceState()
    }
    AppTheme(appearance, content)
}

@Composable
internal fun AppTheme(appearance: AppearanceState, content: @Composable () -> Unit) {
    val controller = remember(appearance) {
        ThemeController(
            colorSchemeMode = when (appearance.mode) {
                AppearanceMode.System -> ColorSchemeMode.System
                AppearanceMode.Light -> ColorSchemeMode.Light
                AppearanceMode.Dark -> ColorSchemeMode.Dark
            },
            lightColors = appearanceColors(appearance, dark = false),
            darkColors = appearanceColors(appearance, dark = true),
        )
    }
    LaunchedEffect(appearance) {
        Log.d(TAG, "应用全局外观：${appearance.mode}/${appearance.accent}")
    }
    SyncSystemBarAppearance(appearance.mode)
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/** ComponentActivity 的 auto 样式以系统为准，此处补齐应用内强制深浅模式。 */
@Composable
private fun SyncSystemBarAppearance(mode: AppearanceMode) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    if (view.isInEditMode) return
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        AppearanceMode.System -> systemDark
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }
    LaunchedEffect(activity, view, dark) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        Log.d(TAG, "系统栏图标已跟随应用主题：dark=$dark")
    }
}

/** 保留 Miuix 中性表面与正文，仅覆盖强调色角色，供导航、表单及其他主题组件共用。 */
internal fun appearanceColors(state: AppearanceState, dark: Boolean): Colors {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val primary = Color(if (dark) state.accent.darkArgb else state.accent.lightArgb)
    // 深色强调色为浅色，使用库的深色中性表面作前景，避免白字与浅色底对比不足。
    val onPrimary = if (dark) base.surface else Color.White
    val container = lerp(primary, base.surface, if (dark) DARK_ACCENT_CONTAINER_BLEND else LIGHT_ACCENT_CONTAINER_BLEND)
    val disabled = lerp(primary, base.surface, DISABLED_ACCENT_BLEND)
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryVariant = primary,
        onPrimaryVariant = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = primary,
        disabledPrimary = disabled,
        disabledPrimaryButton = disabled,
        disabledPrimarySlider = disabled,
        disabledOnPrimary = base.disabledOnSurface,
        disabledOnPrimaryButton = base.disabledOnSurface,
        tertiaryContainer = container,
        onTertiaryContainer = primary,
        tertiaryContainerVariant = container,
        sliderKeyPoint = primary,
        sliderKeyPointForeground = onPrimary,
        sliderBackground = container,
    )
}
