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

/**
 * 提供当前 [ThemeMode] 的 CompositionLocal，供子组件读取。
 */
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }

/**
 * 应用主题包装器
 *
 * 从 [ThemeConfig] 读取用户选择的主题模式，动态创建 [ThemeController]。
 * 因为 miuix [ThemeController.colorSchemeMode] 是 val 不可变，
 * 模式切换时需要重建实例。
 *
 * 支持 6 种模式：System, Light, Dark, MonetSystem, MonetLight, MonetDark
 *
 * @param content 应用内容
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val themeMode by ThemeConfig.themeModeFlow.collectAsState()

    // 当 themeMode 变化时重建 ThemeController
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
