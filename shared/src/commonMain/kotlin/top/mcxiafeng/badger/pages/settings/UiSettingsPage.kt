package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.blur.GpuCompat
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.navigation.ThemeConfig
import top.mcxiafeng.badger.ui.navigation.ThemeMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.menu.WindowDropdownMenu
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "UiSettingsPage"

@Composable
fun UiSettingsPage(onBack: () -> Unit) {
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    var floatingEnabled by remember { mutableStateOf(NavBarConfig.isFloatingEnabled()) }
    val effectMode by NavBarConfig.effectModeFlow.collectAsState(initial = EffectMode.NONE)
    val advancedBlurEnabled by NavBarConfig.advancedBlurFlow.collectAsState(initial = false)
    val hideLabels by NavBarConfig.hideLabelsFlow.collectAsState(initial = false)

    val gpuSupported = remember { GpuCompat.isAdvancedBlurSupported() }

    // 主题模式
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
                    text = when (mode) {
                        EffectMode.NONE -> "无"
                        EffectMode.LIQUID_GLASS -> "液态玻璃"
                        EffectMode.BG_BLUR -> "标准磨砂"
                    },
                    selected = effectMode == mode,
                    onClick = {
                        NavBarConfig.saveEffectMode(mode)
                        BadgerLog.d(TAG, "Effect mode: $mode")
                    },
                )
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "UI 设置",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = BadgerSpacing.md, end = BadgerSpacing.md, top = BadgerSpacing.sm, bottom = BadgerSpacing.sm + floatingBarBottomPadding),
        ) {
            // ---- 主题模式卡片 ----
            item(key = "theme_mode_card") {
                Card(
                    modifier = Modifier.padding(vertical = 6.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    WindowDropdownMenu(
                        title = "主题模式",
                        summary = themeMode.label,
                        entry = themeModeEntry,
                    )
                }
            }

            // ---- 导航栏卡片 ----
            item(key = "nav_bar_card") {
                Card(
                    modifier = Modifier.padding(vertical = BadgerSpacing.sm),
                    insideMargin = PaddingValues(0.dp),
                ) {
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
                    if (floatingEnabled) {
                        WindowDropdownMenu(
                            title = "效果模式",
                            summary = when (effectMode) {
                                EffectMode.NONE -> "无"
                                EffectMode.LIQUID_GLASS -> "液态玻璃"
                                EffectMode.BG_BLUR -> "标准磨砂"
                            },
                            entry = effectModeEntry,
                        )
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

            // ---- 高级液态效果卡片（仅在浮动 + 液态玻璃模式下显示；[K14] 折射/倾斜光斑门控） ----
            if (floatingEnabled && effectMode == EffectMode.LIQUID_GLASS && gpuSupported) {
                item(key = "advanced_card") {
                    Card(
                        modifier = Modifier.padding(vertical = 6.dp),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        SwitchPreference(
                            title = "完整液态效果",
                            summary = "边缘折射、色散、倾斜光斑（需 GPU 支持）",
                            checked = advancedBlurEnabled,
                            onCheckedChange = { newValue ->
                                BadgerLog.d(TAG, "Advanced refraction: $newValue")
                                NavBarConfig.saveAdvancedBlurEnabled(newValue)
                            },
                        )
                    }
                }
            }
        }
    }
}