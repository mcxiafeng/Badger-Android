package top.mcxiafeng.badger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import top.mcxiafeng.badger.ui.navigation.ThemeConfig
import top.mcxiafeng.badger.ui.navigation.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val themeMode by ThemeConfig.themeModeFlow.collectAsState()

    
    val controller = remember(themeMode) {
        ThemeController(colorSchemeMode = themeMode.toColorSchemeMode())
    }

    MiuixTheme(controller = controller) {
        val contentColor = MiuixTheme.colorScheme.onBackground
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalThemeMode provides themeMode,
        ) {
            content()
        }
    }
}
