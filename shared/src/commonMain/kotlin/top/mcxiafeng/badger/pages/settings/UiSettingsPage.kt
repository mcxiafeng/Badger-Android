package top.mcxiafeng.badger.pages.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.mcxiafeng.badger.pages.settings.components.SettingsGroupCard
import top.mcxiafeng.badger.pages.settings.components.SettingsListScaffold
import top.mcxiafeng.badger.ui.blur.GpuCompat
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.ui.navigation.ThemeConfig
import top.mcxiafeng.badger.ui.navigation.ThemeMode
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.WindowDropdownMenu
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "UiSettingsPage"

/**
 * 界面与导航设置页（重写：共享列表脚手架 + SettingsGroupCard）。
 *
 * 主题模式 / 悬浮导航栏 / 效果模式 / 隐藏标签 / 完整液态效果（GPU 门控）。
 */
@Composable
fun UiSettingsPage(onBack: () -> Unit) {
    var floatingEnabled by remember { mutableStateOf(NavBarConfig.isFloatingEnabled()) }
    val effectMode by NavBarConfig.effectModeFlow.collectAsState(initial = EffectMode.NONE)
    val advancedBlurEnabled by NavBarConfig.advancedBlurFlow.collectAsState(initial = false)
    val hideLabels by NavBarConfig.hideLabelsFlow.collectAsState(initial = false)
    val gpuSupported = remember { GpuCompat.isAdvancedBlurSupported() }

    val themeMode by ThemeConfig.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val themeModeEntry = remember(themeMode) {
        DropdownEntry(
            items = ThemeMode.entries.map { mode ->
                DropdownItem(
                    text = mode.label,
                    selected = themeMode == mode,
                    onClick = {
                        ThemeConfig.saveThemeMode(mode)
                        BadgerLog.d(TAG, "Theme mode: $mode")
                    },
                )
            },
        )
    }

    val effectModeEntry = remember(effectMode) {
        DropdownEntry(
            items = EffectMode.entries.map { mode ->
                DropdownItem(
                    text = effectModeLabel(mode),
                    selected = effectMode == mode,
                    onClick = {
                        NavBarConfig.saveEffectMode(mode)
                        BadgerLog.d(TAG, "Effect mode: $mode")
                    },
                )
            },
        )
    }

    SettingsListScaffold(
        title = SettingsPage.UiSettings.title,
        onBack = onBack,
    ) {
        // ---- 主题模式 ----
        item(key = "theme_mode_card") {
            SettingsGroupCard(
                rows = listOf {
                    WindowDropdownMenu(
                        title = "主题模式",
                        summary = themeMode.label,
                        entry = themeModeEntry,
                    )
                },
            )
        }

        // ---- 导航栏 ----
        item(key = "nav_bar_card") {
            val rows = buildList<@Composable () -> Unit> {
                add {
                    SwitchPreference(
                        title = "悬浮导航栏",
                        summary = "胶囊式底部导航栏",
                        checked = floatingEnabled,
                        onCheckedChange = { newValue ->
                            BadgerLog.d(TAG, "Floating nav bar: $newValue")
                            floatingEnabled = newValue
                            NavBarConfig.saveFloatingEnabled(newValue)
                        },
                    )
                }
                if (floatingEnabled) {
                    add {
                        WindowDropdownMenu(
                            title = "效果模式",
                            summary = effectModeLabel(effectMode),
                            entry = effectModeEntry,
                        )
                    }
                    add {
                        SwitchPreference(
                            title = "隐藏标签",
                            summary = "导航栏仅显示图标（默认关闭，图标+文字）",
                            checked = hideLabels,
                            onCheckedChange = { newValue ->
                                BadgerLog.d(TAG, "Hide labels: $newValue")
                                NavBarConfig.saveHideLabels(newValue)
                            },
                        )
                    }
                }
            }
            SettingsGroupCard(rows = rows)
        }

        // ---- 高级液态效果（浮动 + 液态玻璃 + GPU 支持时）----
        if (floatingEnabled && effectMode == EffectMode.LIQUID_GLASS && gpuSupported) {
            item(key = "advanced_card") {
                SettingsGroupCard(
                    rows = listOf {
                        SwitchPreference(
                            title = "完整液态效果",
                            summary = "边缘折射、色散、倾斜光斑（需 GPU 支持）",
                            checked = advancedBlurEnabled,
                            onCheckedChange = { newValue ->
                                BadgerLog.d(TAG, "Advanced refraction: $newValue")
                                NavBarConfig.saveAdvancedBlurEnabled(newValue)
                            },
                        )
                    },
                )
            }
        }
    }
}

private fun effectModeLabel(mode: EffectMode): String = when (mode) {
    EffectMode.NONE -> "无（同时减少动画）"
    EffectMode.LIQUID_GLASS -> "液态玻璃"
    EffectMode.BG_BLUR -> "标准磨砂"
}
