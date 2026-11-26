package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.Tag
import top.yukonga.miuix.kmp.basic.Icon
import top.mcxiafeng.badger.ui.components.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.glassPressFeedback
import top.mcxiafeng.badger.ui.glass.rememberButtonTouchFeedback
import top.mcxiafeng.badger.ui.glass.buttonPressContentAlpha
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import top.mcxiafeng.badger.ui.glass.buttonGlassOrBackground
import top.mcxiafeng.badger.ui.glass.glassButtonForeground

/**
 * 共享的 Tag 视觉组件，供 TagPickerDialog / AiTagPreviewDialog / TagManagerDialog 共用。
 *
 * 设计目标：让三个对话框在视觉上保持一致——横向 chip / 列表行 的 leading 是
 * 「彩色圆点 + 文本」，选中态使用 primary 边框 + primary alpha 背景 + ✓ 图标。
 *
 * - [TagChip]：用于 Picker / AI 预览两个 FlowRow chip 行（圆点 + 名称 + 可选 ✓）。
 * - [TagRow]：用于 ManagerDialog 的列表行（圆点 + 名称 + 副标题 + 自定义 trailing）。
 *
 * 颜色陷阱：tag.color 为 Long 时 `Color(0L)` 会变全透明，必须显式包一层避免（参见
 * feedback_miuix_rules.md 中的"Color陷阱"条目）。
 */

@Composable
internal fun TagChip(
    tag: Tag,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCheckmark: Boolean = true,
) {
    val cs = MiuixTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) cs.primary.copy(alpha = 0.14f) else cs.surfaceVariant,
        label = "TagChipBg"
    )
    val borderColor = if (selected) cs.primary else cs.outline.copy(alpha = 0.5f)
    val textColor = if (selected) cs.primary else cs.onSurface
    val dotColor = Color(tag.color).let { if (it.alpha == 0f) cs.primary else it }

    Box(
        modifier = modifier
            .clip(BadgerShapes.rounded(20.dp))
            .background(containerColor)
            .border(BorderStroke(1.dp, SolidColor(borderColor)), BadgerShapes.rounded(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tag.name,
                style = MiuixTheme.textStyles.body2,
                color = textColor,
            )
            if (selected && showCheckmark) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = cs.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * 列表行样式 —— 单行 Surface 容器，左侧色点 + 名称 + 副标题，右侧自定义 trailing 区。
 *
 * 行高 = intrinsic（不固定），让 subtitle 自然撑开；容器沿用统一内容 Shape。
 */
@Composable
internal fun TagRow(
    tag: Tag,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val cs = MiuixTheme.colorScheme
    val dotColor = Color(tag.color).let { if (it.alpha == 0f) cs.primary else it }

    val baseModifier = modifier
        .clip(BadgerShapes.Surface)
        .background(cs.surfaceVariant)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 12.dp, vertical = 10.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag.name,
                style = MiuixTheme.textStyles.body1,
                color = cs.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2,
                    color = cs.onSurfaceVariantSummary,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/**
 * 进度条 chip —— 用于 AiTagPreviewDialog 表示 confidence。
 */
@Composable
internal fun TagChipWithProgress(
    tag: Tag,
    selected: Boolean,
    confidence: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MiuixTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) cs.primary.copy(alpha = 0.14f) else cs.surfaceVariant,
        label = "TagChipWithProgressBg"
    )
    val borderColor = if (selected) cs.primary else cs.outline.copy(alpha = 0.5f)
    val textColor = glassButtonForeground(if (selected) cs.primary else cs.onSurface)
    val dotColor = Color(tag.color).let { if (it.alpha == 0f) cs.primary else it }
    val enabledGlass = LocalGlassStyle.current.buttonsEnabled
    val feedback = rememberButtonTouchFeedback(enabledGlass)

    Column(
        modifier = modifier
            .widthIn(min = 96.dp)
            .glassPressFeedback(enabledGlass, 14.dp, feedback)
            .clip(BadgerShapes.rounded(14.dp))
            .buttonGlassOrBackground(14.dp, containerColor)
            .border(BorderStroke(1.dp, SolidColor(borderColor)), BadgerShapes.rounded(14.dp))
            .clickable(onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = if (enabledGlass) null else LocalIndication.current)
            .buttonPressContentAlpha(feedback)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tag.name,
                style = MiuixTheme.textStyles.body2,
                color = textColor,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (selected) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = cs.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        // confidence 进度条 4dp 高；颜色用 primary，让选中 / 未选中对比可见。
        LinearProgressIndicator(
            progress = confidence.coerceIn(0f, 1f),
            modifier = Modifier
                .padding(top = 2.dp)
                .clip(BadgerShapes.Capsule),
            foregroundColor = cs.primary,
            backgroundColor = cs.outline.copy(alpha = 0.25f),
        )
    }
}
