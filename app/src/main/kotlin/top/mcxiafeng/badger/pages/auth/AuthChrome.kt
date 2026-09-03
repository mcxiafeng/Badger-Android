package top.mcxiafeng.badger.pages.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.pages.settings.account.DEFAULT_SERVER_URL
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

/**
 * 品牌 Hero 头部 —— [redesign-existing-projects] "品牌 + 渐变 + 镜头锚点"。
 * 圆形 brand 盘由 Radial Gradient 模拟光照,标题/副标在模式切换时淡入淡出。
 */
@Composable
internal fun HeroHeader(mode: AuthMode) {
    val (title, subtitle) = when (mode) {
        AuthMode.Login -> "欢迎回来" to "使用账号继续"
        AuthMode.Register -> "创建账号" to "完成下面几项即可开始"
        AuthMode.ForgotPassword -> "找回密码" to "输入邮箱,设置新密码"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BadgerSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.42f),
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.04f),
                        ),
                        center = Offset(54f, 54f),
                        radius = 130f,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val heroIcon = when (mode) {
                AuthMode.Login -> Icons.Filled.Person
                AuthMode.Register -> Icons.Filled.PersonAdd
                AuthMode.ForgotPassword -> Icons.Filled.LockReset
            }
            Icon(
                imageVector = heroIcon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.md))

        AnimatedContent(
            targetState = title,
            transitionSpec = {
                (fadeIn(tween(220)) +
                    slideInVertically(animationSpec = tween(220)) { it / 8 })
                    .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 8 })
            },
            label = "heroTitle",
        ) { text ->
            Text(
                text = text,
                style = MiuixTheme.textStyles.headline1,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))

        AnimatedContent(
            targetState = subtitle,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "heroSubtitle",
        ) { text ->
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * Miuix 模式 segmented control —— pill 在 tab 之间滑动，替代原 alpha 叠加 chip。
 *
 * 设计依据：
 *   - 选中态：surface 底 + 主色文字 + 主色 16% 阴影，区别于 surfaceVariant 浅灰底；
 *   - Pill 使用 [animateDpAsState] tween 280ms FastOutSlowInEasing 弹性滑动；
 *   - Pill 自身无 indication —— 点击由各自 tab 内部的 clickable 承担,
 *     pill 只是视觉指示器,不消费点击事件。
 */
@Composable
internal fun ModeSegmentedControl(
    modes: List<Pair<AuthMode, String>>,
    selected: AuthMode,
    enabled: Boolean,
    onSelect: (AuthMode) -> Unit,
) {
    val selectedIndex = modes.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / modes.size
        val targetOffset = tabWidth * selectedIndex
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "segmentOffset",
        )
        // Pill：surface 底 + 主色阴影,带轻微 lift 制造"按下会抬起"的暗示。
        // [修复防御]: 主色 colorScheme 是 @Composable 上下文读取,不能放进 drawBehind 的
        // DrawScope 闭包 —— 必须 hoist 到 pill Box 外部(@Composable 作用域)。
        val primaryTint = MiuixTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .width(tabWidth)
                .height(36.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface)
                .drawBehind {
                    // 模拟"提起的按钮"的 4dp 阴影层。
                    // 阴影 Y 偏移 6dp,只在低位绘制,留下 6dp 的"提空"感。
                    drawRoundRect(
                        color = primaryTint.copy(alpha = 0.18f),
                        cornerRadius = CornerRadius(
                            size.minDimension / 2f,
                            size.minDimension / 2f,
                        ),
                        topLeft = Offset(0f, 6f),
                        size = Size(size.width, size.height - 6f),
                    )
                },
        )

        // Tab 行 —— clickable 但 indication=null(MiuixIndication 已在 pill 上由 elevation 取代)
        Row(modifier = Modifier.fillMaxWidth()) {
            modes.forEach { (mode, label) ->
                val isSelected = mode == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = enabled && !isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(mode) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MiuixTheme.textStyles.subtitle,
                        color = if (isSelected) {
                            MiuixTheme.colorScheme.onSurface
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
            }
        }
    }
}

/**
 * 服务器地址 banner —— 可点击的轻量 Card。
 * 配色:errorContainer +0.5 alpha 与 Miuix 主基调保持和谐。
 */
@Composable
internal fun ServerHintBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = MiuixIndication(),
                onClickLabel = "修改服务器地址",
                onClick = onClick,
            ),
        insideMargin = PaddingValues(
            horizontal = BadgerSpacing.md,
            vertical = BadgerSpacing.md,
        ),
        cornerRadius = BadgerRadius.lg,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
    ) {
        // [修复防御]: 不要在 Card 的 modifier 上额外加 .clip(CircleShape) —— Card 已经
        // 通过 cornerRadius 自带圆角,再 clip 成 CircleShape 会让一个宽而扁的卡片被
        // 50% 圆角切成只剩中间一点椭圆可见(因为 fillMaxWidth 让宽度 >> 高度,而
        // RoundedCornerShape(50%) 在窄高矩形上等价于椭圆)。
        Text(
            text = "当前服务器地址未配置（默认 ${DEFAULT_SERVER_URL}），点此修改 →",
            style = MiuixTheme.textStyles.body2,
        )
    }
}
