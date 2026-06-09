package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.blur.BlurIntensity
import top.mcxiafeng.badger.ui.blur.GpuCompat
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SetupStepUiStyle(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    var floatingEnabled by remember { mutableStateOf(NavBarConfig.isFloatingEnabled(context)) }
    var liquidGlassEnabled by remember { mutableStateOf(NavBarConfig.isLiquidGlassEnabled(context)) }
    val blurIntensity by NavBarConfig.blurIntensityFlow.collectAsState(initial = BlurIntensity.THICK)
    val advancedBlurEnabled by NavBarConfig.advancedBlurFlow.collectAsState(initial = false)

    val gpuSupported = remember { GpuCompat.isAdvancedBlurSupported(context) }

    SetupStepScaffold(
        onBack = onBack,
        onSkip = {
            Log.d(TAG, "UI style step skipped")
            onSkip()
        },
        onNext = {
            Log.d(TAG, "UI style step completed")
            onNext()
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "选择外观风格",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "个性化你的导航栏",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                SwitchPreference(
                    title = "悬浮导航栏",
                    summary = "胶囊式底部导航栏，进入主界面后生效",
                    checked = floatingEnabled,
                    onCheckedChange = { newValue ->
                        floatingEnabled = newValue
                        NavBarConfig.saveFloatingEnabled(context, newValue)
                        Log.d(TAG, "Floating nav bar: $newValue")
                    }
                )
            }
            if (floatingEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    SwitchPreference(
                        title = "液态玻璃",
                        summary = "给导航栏添加磨砂玻璃效果",
                        checked = liquidGlassEnabled,
                        onCheckedChange = { newValue ->
                            liquidGlassEnabled = newValue
                            NavBarConfig.saveLiquidGlassEnabled(context, newValue)
                            Log.d(TAG, "Liquid glass: $newValue")
                        }
                    )
                }
            }
            if (floatingEnabled && liquidGlassEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    SmallTitle(text = "模糊效果", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                    BasicComponent(
                        title = "模糊强度",
                        summary = when (blurIntensity) {
                            BlurIntensity.THIN -> "轻薄"
                            BlurIntensity.THICK -> "标准"
                            BlurIntensity.APPLE_DOCK -> "Apple Dock"
                        },
                        onClick = {
                            val next = BlurIntensity.entries.getOrElse(blurIntensity.ordinal + 1) { BlurIntensity.entries.first() }
                            NavBarConfig.saveBlurIntensity(context, next)
                            Log.d(TAG, "Blur intensity: $next")
                        },
                    )
                    if (gpuSupported) {
                        SwitchPreference(
                            title = "高级液态效果",
                            summary = "折射、色散、陀螺仪光照（实验性）",
                            checked = advancedBlurEnabled,
                            onCheckedChange = { newValue ->
                                NavBarConfig.saveAdvancedBlurEnabled(context, newValue)
                                Log.d(TAG, "Advanced blur: $newValue")
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            NavBarPreview(
                floatingEnabled = floatingEnabled,
                liquidGlassEnabled = liquidGlassEnabled
            )
        }
    }
}

@Composable
private fun NavBarPreview(
    floatingEnabled: Boolean,
    liquidGlassEnabled: Boolean = false
) {
    val tabs = listOf("社交", "名片", "扫描", "设置")
    val icons = listOf(
        Icons.Outlined.Person,
        Icons.Outlined.CreditCard,
        Icons.Outlined.QrCodeScanner,
        Icons.Outlined.Settings
    )

    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceColor)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                contentAlignment = Alignment.Center
            ) {
                if (floatingEnabled) {
                    FloatingNavBar(
                        selectedIndex = 0,
                        pageOffset = 0f,
                        onSelected = {},
                        tabs = tabs,
                        icons = icons,
                        color = surfaceColor,
                        liquidGlassEnabled = liquidGlassEnabled
                    )
                } else {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        showDivider = false
                    ) {
                        tabs.forEachIndexed { index, label ->
                            NavBarItem(
                                title = label,
                                icon = icons[index],
                                selected = index == 0,
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}