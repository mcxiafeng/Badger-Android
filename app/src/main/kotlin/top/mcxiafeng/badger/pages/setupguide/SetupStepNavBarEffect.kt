package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.blur.GpuCompat
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val UI_STYLE_TAG = "SetupStepNavBarEffect"

private val PREVIEW_TABS = listOf("社交", "名片", "扫描", "设置")
private val PREVIEW_ICONS = listOf(
    MiuixIcons.Contacts,
    MiuixIcons.Folder,
    MiuixIcons.Scan,
    MiuixIcons.Settings,
)

@Composable
internal fun SetupStepNavBarEffect(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer

    val currentMode by NavBarConfig.effectModeFlow.collectAsState(initial = EffectMode.BG_BLUR)
    var selectedMode by remember { mutableStateOf(currentMode) }
    val gpuSupported = remember { GpuCompat.isAdvancedBlurSupported(context) }

    SetupStepScaffold(
        onBack = onBack,
        onSkip = {
            Log.d(UI_STYLE_TAG, "UI style step skipped")
            onSkip()
        },
        onNext = {
            NavBarConfig.saveFloatingEnabled(context, true)
            NavBarConfig.saveEffectMode(context, selectedMode)
            if (selectedMode == EffectMode.LIQUID_GLASS && gpuSupported) {
                NavBarConfig.saveAdvancedBlurEnabled(context, true)
            } else {
                NavBarConfig.saveAdvancedBlurEnabled(context, false)
            }
            Log.d(UI_STYLE_TAG, "UI style step completed, mode=$selectedMode")
            onNext()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = BadgerSpacing.xl)
                .padding(bottom = 72.dp + floatingBarBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "选择外观风格",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            Text(
                text = "挑一个你喜欢的，后续随时可在设置中修改",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

            EffectOptionCard(
                title = "经典",
                subtitle = "不悬浮的常规底栏",
                selected = selectedMode == EffectMode.NONE,
                onClick = { selectedMode = EffectMode.NONE },
            ) {
                ClassicNavBarPreview()
            }
            Spacer(modifier = Modifier.height(12.dp))

            EffectOptionCard(
                title = "液态玻璃",
                subtitle = if (gpuSupported) "悬浮 + 玻璃折射（推荐）" else "悬浮 + 玻璃折射（当前设备 GPU 不支持高级效果）",
                selected = selectedMode == EffectMode.LIQUID_GLASS,
                onClick = { selectedMode = EffectMode.LIQUID_GLASS },
                enabled = gpuSupported,
            ) {
                LiquidGlassNavBarPreview()
            }
            Spacer(modifier = Modifier.height(12.dp))

            EffectOptionCard(
                title = "背景模糊",
                subtitle = "悬浮 + Haze 模糊背景",
                selected = selectedMode == EffectMode.BG_BLUR,
                onClick = { selectedMode = EffectMode.BG_BLUR },
            ) {
                BlurredNavBarPreview()
            }
        }
    }
}

@Composable
private fun EffectOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f)
            selected -> MiuixTheme.colorScheme.primary
            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
        },
        label = "border",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            selected -> MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
            else -> MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        label = "container",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        insideMargin = PaddingValues(0.dp),
    ) {
        Box(modifier = Modifier.background(containerColor)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(
                            text = title,
                            style = MiuixTheme.textStyles.subtitle,
                            color = if (enabled) MiuixTheme.colorScheme.onSurface
                            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = subtitle,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            preview()
        }
    }
}

@Composable
private fun ColoredStripes() {
    Row(modifier = Modifier.fillMaxSize()) {
        listOf(
            Color(0xFF4CAF50),
            Color(0xFF2196F3),
            Color(0xFFFF9800),
            Color(0xFFE91E63),
            Color(0xFF9C27B0),
            Color(0xFF00BCD4),
        ).forEach { color ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(color.copy(alpha = 0.7f)),
            )
        }
    }
}

@Composable
private fun ClassicNavBarPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            showDivider = false,
        ) {
            PREVIEW_TABS.forEachIndexed { index, label ->
                NavBarItem(
                    title = label,
                    icon = PREVIEW_ICONS[index],
                    selected = index == 0,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassNavBarPreview() {
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        ColoredStripes()
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(vertical = 4.dp),
        ) {
            FloatingNavBar(
                selectedIndex = 0,
                pageOffset = 0f,
                onSelected = {},
                tabs = PREVIEW_TABS,
                icons = PREVIEW_ICONS,
                color = surfaceColor,
                liquidGlassEnabled = true,
                hazeState = null,
                backdrop = null,
                blurIntensity = top.mcxiafeng.badger.ui.blur.BlurIntensity.THICK,
                effectMode = EffectMode.LIQUID_GLASS,
                isScrolling = false,
            )
        }
    }
}

@Composable
private fun BlurredNavBarPreview() {
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer
    val hazeState = remember { HazeState().apply { blurEnabled = true } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Box(modifier = Modifier.hazeSource(state = hazeState)) {
            ColoredStripes()
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(vertical = 4.dp),
        ) {
            FloatingNavBar(
                selectedIndex = 0,
                pageOffset = 0f,
                onSelected = {},
                tabs = PREVIEW_TABS,
                icons = PREVIEW_ICONS,
                color = surfaceColor.copy(alpha = 0.6f),
                liquidGlassEnabled = true,
                hazeState = hazeState,
                backdrop = null,
                blurIntensity = top.mcxiafeng.badger.ui.blur.BlurIntensity.THICK,
                effectMode = EffectMode.BG_BLUR,
                isScrolling = false,
            )
        }
    }
}


