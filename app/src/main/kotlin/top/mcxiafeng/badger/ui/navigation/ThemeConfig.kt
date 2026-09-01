package top.mcxiafeng.badger.ui.navigation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * 主题模式枚举
 *
 * 映射到 miuix [ColorSchemeMode]，支持 6 种模式：
 * - 跟随系统 / 浅色 / 深色 / 动态色彩(系统) / 动态色彩(浅色) / 动态色彩(深色)
 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
    MONET_SYSTEM("动态色彩（跟随系统）"),
    MONET_LIGHT("动态色彩（浅色）"),
    MONET_DARK("动态色彩（深色）"),
    ;

    fun toColorSchemeMode(): ColorSchemeMode = when (this) {
        SYSTEM -> ColorSchemeMode.System
        LIGHT -> ColorSchemeMode.Light
        DARK -> ColorSchemeMode.Dark
        MONET_SYSTEM -> ColorSchemeMode.MonetSystem
        MONET_LIGHT -> ColorSchemeMode.MonetLight
        MONET_DARK -> ColorSchemeMode.MonetDark
    }
}

/**
 * 主题偏好持久化管理
 *
 * 与 [NavBarConfig] 模式一致：SharedPreferences + StateFlow。
 * 存储在 `badger_settings` 的 `theme_mode` key 中。
 *
 * 旧版本使用 ordinal(Int) 持久化。读取时保留兼容，写入时改用枚举名，
 * 避免今后调整枚举顺序后把用户已有设置映射到错误的主题。
 */
object ThemeConfig {
    private const val TAG = "ThemeConfig"
    private const val PREFS_NAME = "badger_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    private val _themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawValue = prefs.all[KEY_THEME_MODE]
        _themeModeFlow.value = when (rawValue) {
            is String -> ThemeMode.entries.firstOrNull { it.name == rawValue } ?: ThemeMode.SYSTEM
            is Int -> ThemeMode.entries.getOrElse(rawValue) { ThemeMode.SYSTEM }
            else -> ThemeMode.SYSTEM
        }
        Log.d(TAG, "Initialized: themeMode=${_themeModeFlow.value}")
    }

    fun saveThemeMode(context: Context, mode: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeModeFlow.value = mode
        Log.d(TAG, "Saved: themeMode=$mode")
    }
}
