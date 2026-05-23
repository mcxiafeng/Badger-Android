package top.mcxiafeng.badger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 应用主题包装器
 *
 * 使用 Miuix 主题框架，默认跟随系统深色/浅色模式。
 * 可用模式：System, Light, Dark, MonetSystem, MonetLight, MonetDark
 *
 * @param content 应用内容
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    // 可用模式: System, Light, Dark, MonetSystem, MonetLight, MonetDark
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    return MiuixTheme(
        controller = controller,
        content = content
    )
}
