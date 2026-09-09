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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.TriangleAlert
import androidx.compose.ui.text.style.TextOverflow

/**
 * Auth 页面非表单「镶边」：品牌 Hero / 模式切换器 / 服务器提示条。
 * AuthScreen、ForgotPasswordScreen、SetupStepAccount 共用。
 */

private const val HERO_DISC_SIZE_DP = 64
private const val HERO_ICON_SIZE_DP = 32

/**
 * 品牌 Hero —— 主色 12% 圆角芯片 + 图标（与设置页彩色芯片同语言），
 * 标题/副标在内容切换时淡入淡出。
 */
@Composable
internal fun AuthHero(title: String, subtitle: String, icon: ImageVector) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BadgerSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(HERO_DISC_SIZE_DP.dp)
                .clip(RoundedCornerShape(BadgerRadius.container))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(HERO_ICON_SIZE_DP.dp),
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.md))

        AnimatedContent(
            targetState = title,
            transitionSpec = {
                (fadeIn(tween(BadgerMotion.DURATION_BASE)) +
                    slideInVertically(animationSpec = tween(BadgerMotion.DURATION_BASE)) { it / 8 })
                    .togetherWith(
                        fadeOut(tween(BadgerMotion.DURATION_FAST)) +
                            slideOutVertically(tween(BadgerMotion.DURATION_FAST)) { -it / 8 }
                    )
            },
            label = "authHeroTitle",
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
            transitionSpec = {
                fadeIn(tween(BadgerMotion.DURATION_BASE)) togetherWith
                    fadeOut(tween(BadgerMotion.DURATION_FAST))
            },
            label = "authHeroSubtitle",
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
 * 模式切换器 —— 滑动 pill 的 segmented control，段数由调用方决定
 * （认证主页 2 段：登录/注册；引导页 3 段：登录/注册/忘记密码）。
 *
 * - 选中 pill：surface 底 + 主色低位阴影，制造「提起」的暗示；
 * - pill 仅是指示器不消费点击，点击由各 tab 承担；
 * - 主色在 @Composable 作用域读取后传入 drawBehind（DrawScope 闭包内不可再读 colorScheme）。
 */
@Composable
internal fun AuthModeSwitch(
    tabs: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val safeIndex = selectedIndex.coerceIn(0, tabs.lastIndex)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / tabs.size
        val animatedOffset by animateDpAsState(
            targetValue = tabWidth * safeIndex,
            animationSpec = tween(durationMillis = BadgerMotion.DURATION_BASE, easing = FastOutSlowInEasing),
            label = "authModeSwitchOffset",
        )
        val primaryTint = MiuixTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .width(tabWidth)
                .height(36.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface)
                .drawBehind {
                    // 4dp 低位阴影：Y 偏移 6dp 绘制，留出「提空」感
                    drawRoundRect(
                        color = primaryTint.copy(alpha = 0.18f),
                        cornerRadius = CornerRadius(size.minDimension / 2f, size.minDimension / 2f),
                        topLeft = Offset(0f, 6f),
                        size = Size(size.width, size.height - 6f),
                    )
                },
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == safeIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = enabled && !isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
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
 * 服务器连接状态条 —— 三态常驻，点击均可打开修改对话框：
 * - 探测中：中性底 + 转圈（进页面自动探测）；
 * - 已连接：primaryContainer 蓝调展示当前地址（仍可点击修改）；
 * - 未验证：errorContainer 警示（探测失败或未探测）。
 */
@Composable
internal fun ServerStatusBanner(
    url: String,
    probing: Boolean,
    verified: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val (bgColor, fgColor) = when {
        probing -> colorScheme.surfaceVariant.copy(alpha = 0.55f) to colorScheme.onSurfaceVariantSummary
        verified -> colorScheme.primaryContainer.copy(alpha = 0.6f) to colorScheme.onPrimaryContainer
        else -> colorScheme.errorContainer.copy(alpha = 0.5f) to colorScheme.onErrorContainer
    }
    // [A2 fix] clickable 移到 Card onClick 参数，MiuixIndication 已全局注入无需手动指定
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        insideMargin = PaddingValues(
            horizontal = BadgerSpacing.md,
            vertical = BadgerSpacing.md,
        ),
        cornerRadius = BadgerRadius.card,
        colors = CardDefaults.defaultColors(color = bgColor, contentColor = fgColor),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                probing -> {
                    CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(BadgerSpacing.sm))
                    Text(text = "正在连接服务器…", style = MiuixTheme.textStyles.body2)
                }
                verified -> {
                    Icon(
                        imageVector = Lucide.CircleCheck,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(BadgerSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "已连接服务器", style = MiuixTheme.textStyles.body2)
                        Text(
                            text = url,
                            style = MiuixTheme.textStyles.footnote1,
                            color = colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "修改",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colorScheme.primary,
                    )
                }
                else -> {
                    Icon(
                        imageVector = Lucide.TriangleAlert,
                        contentDescription = null,
                        tint = fgColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(BadgerSpacing.sm))
                    Text(
                        text = "服务器地址尚未验证，点此检查或修改",
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
    }
}
