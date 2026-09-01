package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.menu.WindowDropdownMenu
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "UiSettingsPage"
private const val MIN_BLUR_RADIUS_DP = 2f
private const val MAX_BLUR_RADIUS_DP = 64f
private const val BLUR_RADIUS_RANGE_DP = MAX_BLUR_RADIUS_DP - MIN_BLUR_RADIUS_DP

@Composable
fun UiSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    val floatingEnabled by NavBarConfig.floatingFlow.collectAsState(
        initial = NavBarConfig.isFloatingEnabled(context),
    )
    val effectMode by NavBarConfig.effectModeFlow.collectAsState(initial = EffectMode.NONE)
    val blurRadiusDp by NavBarConfig.blurRadiusDpFlow.collectAsState(initial = 12f)
    val advancedBlurEnabled by NavBarConfig.advancedBlurFlow.collectAsState(initial = false)

    val gpuSupported = remember { GpuCompat.isAdvancedBlurSupported(context) }

    val themeMode by ThemeConfig.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val themeModeEntry = remember(themeMode) {
        DropdownEntry(
            items = ThemeMode.entries.map { mode ->
                DropdownItem(
                    text = mode.label,
                    selected = themeMode == mode,
                    onClick = {
                        ThemeConfig.saveThemeMode(context, mode)
                        Log.d(TAG, "Theme mode: $mode")
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
                        EffectMode.BG_BLUR -> "背景模糊"
                    },
                    selected = effectMode == mode,
                    onClick = {
                        NavBarConfig.saveEffectMode(context, mode)
                        Log.d(TAG, "Effect mode: $mode")
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(
                start = BadgerSpacing.md,
                end = BadgerSpacing.md,
                top = BadgerSpacing.sm,
                bottom = BadgerSpacing.sm + floatingBarBottomPadding,
            ),
        ) {
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
                            Log.d(TAG, "Floating nav bar: $newValue")
                            NavBarConfig.saveFloatingEnabled(context, newValue)
                        },
                    )
                    if (floatingEnabled) {
                        WindowDropdownMenu(
                            title = "效果模式",
                            summary = when (effectMode) {
                                EffectMode.NONE -> "无"
                                EffectMode.LIQUID_GLASS -> "液态玻璃"
                                EffectMode.BG_BLUR -> "背景模糊"
                            },
                            entry = effectModeEntry,
                        )
                    }
                }
            }

            if (floatingEnabled && effectMode == EffectMode.BG_BLUR) {
                item(key = "blur_card") {
                    Card(
                        modifier = Modifier.padding(vertical = 6.dp),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            val normalizedBlurRadius = blurRadiusDp.coerceIn(
                                MIN_BLUR_RADIUS_DP,
                                MAX_BLUR_RADIUS_DP,
                            )
                            Text(
                                text = "模糊半径: ${"%.1f".format(normalizedBlurRadius)} dp",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Slider(
                                modifier = Modifier.fillMaxWidth(),
                                value = ((normalizedBlurRadius - MIN_BLUR_RADIUS_DP) / BLUR_RADIUS_RANGE_DP)
                                    .coerceIn(0f, 1f),
                                onValueChange = { newValue ->
                                    val radius = newValue.coerceIn(0f, 1f) * BLUR_RADIUS_RANGE_DP + MIN_BLUR_RADIUS_DP
                                    NavBarConfig.saveBlurRadiusDp(context, radius)
                                },
                            )
                        }
                    }
                }
            }

            if (floatingEnabled && effectMode == EffectMode.LIQUID_GLASS && gpuSupported) {
                item(key = "advanced_card") {
                    Card(
                        modifier = Modifier.padding(vertical = 6.dp),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        SwitchPreference(
                            title = "高级液态效果",
                            summary = "折射、色散、陀螺仪光照（需 GPU 支持，实验性）",
                            checked = advancedBlurEnabled,
                            onCheckedChange = { newValue ->
                                Log.d(TAG, "Advanced blur: $newValue")
                                NavBarConfig.saveAdvancedBlurEnabled(context, newValue)
                            },
                        )
                    }
                }
            }
        }
    }
}