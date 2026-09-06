package top.mcxiafeng.badger.pages.setupguide

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.blur.GpuCompat
import top.mcxiafeng.badger.ui.blur.badgerBackdropSource
import top.mcxiafeng.badger.ui.blur.rememberBadgerBackdrop
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.ScanLine
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.User
import top.mcxiafeng.badger.utils.BadgerLog

private const val UI_STYLE_TAG = "SetupStepNavBarEffect"
private const val PAGE_INDEX = 4

private val PREVIEW_TABS = listOf("社交", "名片", "扫描", "设置")
private val PREVIEW_ICONS = listOf(
    Lucide.User,
    Lucide.Folder,
    Lucide.ScanLine,
    Lucide.Settings,
)

/**
 * 引导 Step 4 — 选择外观风格（底栏特效）。
 *
 * 设计契约：
 * - 不可跳过。默认选中当前已配置的模式，用户必须**显式**点击一个选项才能继续。
 * - 首次进入若 effectMode 已是 NONE/BG_BLUR 之一,直接选中 —— 让 0 配置用户也能 next。
 * - 选项三种：经典 / 液态玻璃 / 背景模糊。每张卡片预览真实效果,所见即所得。
 */
@Composable
internal fun SetupStepNavBarEffect(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: SetupGuideViewModel = org.koin.compose.viewmodel.koinViewModel(),
) {
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer

    val currentMode by NavBarConfig.effectModeFlow.collectAsState(initial = EffectMode.BG_BLUR)
    var selectedMode by remember { mutableStateOf(currentMode) }
    val gpuSupported = remember { GpuCompat.isAdvancedBlurSupported() }

    // [修复防御 #B4 重看引导覆盖]: setup 可能被再次触发(SetupGuidePage 文档 line 58-59 写明),
    // 此时用户已经在设置里关掉悬浮底栏 / 切换过 effectMode / 关掉 advanced blur。
    // onNext 必须比对「当前已有值」,与用户选择一致就不落盘,避免 setup 跑完一次后用户的偏好被重置。
    val currentFloating = NavBarConfig.floatingFlow.collectAsState().value
    val currentAdvancedBlur = NavBarConfig.advancedBlurFlow.collectAsState().value

    // [修复防御]: GPU 不支持液态玻璃时禁用该选项 + 默认降级到 BG_BLUR，
    // 避免用户选了 LIQUID_GLASS 但 GPU 跑不动 → 实际看到 NONE 的体验断点。
    LaunchedEffect(gpuSupported) {
        if (selectedMode == EffectMode.LIQUID_GLASS && !gpuSupported) {
            BadgerLog.d(UI_STYLE_TAG, "GPU not support liquid glass → fallback to BG_BLUR")
            selectedMode = EffectMode.BG_BLUR
        }
    }

    // [修复防御]: 此页永远可推进 —— 三个效果各有默认值,不存在「不可达」状态。
    LaunchedEffect(Unit) {
        viewModel.setPageValid(PAGE_INDEX, true)
    }

    SetupStepScaffold(
        onBack = onBack,
        onNext = {
            // [修复防御 #B4]: 只在用户当前选择与已有配置不一致时落盘。
            // 「重看引导」场景:用户已在设置里手动关掉悬浮、选了 NONE;setup 走完不能把这些改回去。
            if (selectedMode != currentMode) {
                NavBarConfig.saveEffectMode(selectedMode)
                BadgerLog.d(UI_STYLE_TAG, "next → effectMode changed: $currentMode → $selectedMode")
            }
            if (!currentFloating) {
                BadgerLog.d(UI_STYLE_TAG, "next → keep floating disabled (user's choice)")
            } else {
                NavBarConfig.saveFloatingEnabled(true)
            }
            val shouldAdvancedBlur = selectedMode == EffectMode.LIQUID_GLASS && gpuSupported
            if (shouldAdvancedBlur != currentAdvancedBlur) {
                NavBarConfig.saveAdvancedBlurEnabled(shouldAdvancedBlur)
                BadgerLog.d(UI_STYLE_TAG, "next → advancedBlur=$shouldAdvancedBlur")
            }
            BadgerLog.d(UI_STYLE_TAG, "next → effectMode=$selectedMode, floating=preserved, advancedBlur=preserved")
            onNext()
        },
        nextEnabled = true,
        nextText = "继续",
        backText = "上一步",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BadgerSpacing.xxl, vertical = BadgerSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepHeader(
                title = "选择外观风格",
                subtitle = "挑一个你喜欢的底栏特效，随时可在设置中修改",
                icon = Lucide.Palette,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

            EffectOptionCard(
                title = "经典",
                subtitle = "不悬浮的常规底栏，省电稳定",
                selected = selectedMode == EffectMode.NONE,
                onClick = { selectedMode = EffectMode.NONE },
            ) {
                ClassicNavBarPreview()
            }
            Spacer(modifier = Modifier.height(BadgerSpacing.md))

            EffectOptionCard(
                title = "液态玻璃",
                subtitle = if (gpuSupported) "悬浮 + 玻璃折射（推荐）"
                else "悬浮 + 玻璃折射（当前设备 GPU 不支持高级效果）",
                selected = selectedMode == EffectMode.LIQUID_GLASS,
                onClick = { selectedMode = EffectMode.LIQUID_GLASS },
                enabled = gpuSupported,
            ) {
                LiquidGlassNavBarPreview()
            }
            Spacer(modifier = Modifier.height(BadgerSpacing.md))

            EffectOptionCard(
                title = "标准磨砂",
                subtitle = "悬浮 + 磨砂背景，兼容性好",
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
                    .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.subtitle.copy(fontWeight = FontWeight.SemiBold),
                        color = if (enabled) MiuixTheme.colorScheme.onSurface
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
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
                            imageVector = Lucide.Check,
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
                .padding(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.sm),
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
            .clip(RoundedCornerShape(BadgerRadius.lg))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        NavigationBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
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
    // [K14] 预览即所得：本地采样源 + 完整液态参数（预览卡内 ColoredStripes 即被采样的背景）
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer
    val backdrop = rememberBadgerBackdrop()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(BadgerRadius.lg)),
    ) {
        Box(modifier = Modifier.badgerBackdropSource(backdrop)) {
            ColoredStripes()
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(vertical = BadgerSpacing.xs),
        ) {
            FloatingNavBar(
                selectedIndex = 0,
                onSelected = {},
                tabs = PREVIEW_TABS,
                icons = PREVIEW_ICONS,
                containerColor = surfaceColor,
                effectMode = EffectMode.LIQUID_GLASS,
                backdrop = backdrop,
                blurActive = true,
                advancedRefraction = true,
            )
        }
    }
}

@Composable
private fun BlurredNavBarPreview() {
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer
    val backdrop = rememberBadgerBackdrop()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(BadgerRadius.lg)),
    ) {
        Box(modifier = Modifier.badgerBackdropSource(backdrop)) {
            ColoredStripes()
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(vertical = BadgerSpacing.xs),
        ) {
            FloatingNavBar(
                selectedIndex = 0,
                onSelected = {},
                tabs = PREVIEW_TABS,
                icons = PREVIEW_ICONS,
                containerColor = surfaceColor,
                effectMode = EffectMode.BG_BLUR,
                backdrop = backdrop,
                blurActive = true,
                advancedRefraction = false,
            )
        }
    }
}
