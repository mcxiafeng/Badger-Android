package top.mcxiafeng.badger.pages.setupguide

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
/**
 * Step 进度指示器 — 6 个圆点，已完成显示 ✓，当前/已完成高亮 primary，未到达灰色。
 *
 * [a11y]: 整组挂语义"第 N 步，共 M 步"，让 TalkBack 用户能感知引导进度。
 */
@Composable
internal fun StepProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "引导进度：第 ${currentStep + 1} 步，共 $totalSteps 步"
        },
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until totalSteps) {
            val isCompleted = i < currentStep
            val isActive = i == currentStep

            val size by animateDpAsState(
                targetValue = if (isActive) 10.dp else 8.dp,
                animationSpec = tween(300),
            )
            val color by animateColorAsState(
                targetValue = when {
                    isCompleted || isActive -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f)
                },
                animationSpec = tween(300),
            )

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Lucide.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Step 底部导航按钮 — 仅保留 Back / Next，**已彻底移除 Skip 入口**。
 *
 * 必传 `nextEnabled`：每个 step 必须满足其前置条件才能继续，避免「假必填」陷阱。
 *   - Step 0 (ServerUrl) : URL 合法
 *   - Step 1 (Account)   : 已登录（AuthUiState.SignedIn）
 *   - Step 2 (Profile)   : 昵称非空
 *   - Step 3 (Platforms) : 至少 1 个平台
 *   - Step 4 (Style)     : 总是 true
 *
 * [a11y]: 按钮强制最小 48dp 触摸目标，符合 Material Design 触屏规范。
 */
@Composable
internal fun SetupStepNavButtons(
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    nextText: String,
    backText: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
    ) {
        if (onBack != null) {
            TextButton(
                text = backText,
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MIN_TOUCH_TARGET),
            )
        }
        Button(
            onClick = onNext,
            modifier = Modifier
                .weight(if (onBack != null) 1f else 2f)
                .heightIn(min = MIN_TOUCH_TARGET),
            colors = ButtonDefaults.buttonColorsPrimary(),
            enabled = nextEnabled,
        ) {
            Text(text = nextText)
        }
    }
}

/** 最小触摸目标高度,符合 Android Material Design 48dp 规范。 */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * Step 通用布局：内容 + 固定底部主按钮。**不再支持 Skip**。
 *
 * - 内容区域使用 `verticalScroll` 友好的 Column
 * - 底部按钮在 floating nav bar padding 上叠加，与全局风格保持一致
 */
@Composable
internal fun SetupStepScaffold(
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    nextText: String,
    backText: String = "上一步",
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // [修复防御]: 内容底部预留 = 按钮区高度(80dp) + floating nav bar padding，
                // 避免滚动到底被按钮遮住。80dp = 48dp 按钮 + spacing.lg × 2 (16dp 上下间距)。
                .padding(bottom = BOTTOM_NAV_RESERVED + LocalFloatingBarBottomPadding.current),
        ) {
            content()
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = BadgerSpacing.xxl, vertical = BadgerSpacing.lg),
        ) {
            SetupStepNavButtons(
                onBack = onBack,
                onNext = onNext,
                nextEnabled = nextEnabled,
                nextText = nextText,
                backText = backText,
            )
            // [修复防御]: 此处故意不放任何 skip / 跳过入口；
            // 每个 step 必须满足前置条件才能继续 —— 这是 V2 引导的设计契约。
        }
    }
}

/** 底部主按钮区固定占用的高度(48dp 触摸目标 + 上下 spacing.lg×2 ≈ 80dp)。 */
private val BOTTOM_NAV_RESERVED = 80.dp
