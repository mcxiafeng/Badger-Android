package top.mcxiafeng.badger.pages.person.contact.detail

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSemanticColors
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check

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
            .clip(RoundedCornerShape(BadgerRadius.container))
            .background(containerColor)
            .border(BorderStroke(1.dp, SolidColor(borderColor)), RoundedCornerShape(BadgerRadius.container))
            .clickable(onClick = onClick)
            .padding(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.sm),
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
                    imageVector = Lucide.Check,
                    contentDescription = "已选中",
                    tint = cs.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

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
        .clip(RoundedCornerShape(BadgerRadius.inner))
        .background(cs.surfaceVariant)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.md)

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
    val textColor = if (selected) cs.primary else cs.onSurface
    val dotColor = Color(tag.color).let { if (it.alpha == 0f) cs.primary else it }

    Column(
        modifier = modifier
            .widthIn(min = 96.dp)
            .clip(RoundedCornerShape(BadgerRadius.inner))
            .background(containerColor)
            .border(BorderStroke(1.dp, SolidColor(borderColor)), RoundedCornerShape(BadgerRadius.inner))
            .clickable(onClick = onClick)
            .padding(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.md),
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
                    imageVector = Lucide.Check,
                    contentDescription = "已选中",
                    tint = cs.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        
        val progressColor = when {
            confidence >= 0.7f -> BadgerSemanticColors.success
            confidence >= 0.4f -> BadgerSemanticColors.warning
            else -> BadgerSemanticColors.danger
        }
        LinearProgressIndicator(
            progress = confidence.coerceIn(0f, 1f),
            modifier = Modifier
                .padding(top = BadgerSpacing.xxs)
                .clip(RoundedCornerShape(BadgerSpacing.xxs)),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = progressColor,
                backgroundColor = cs.outline.copy(alpha = 0.25f),
            ),
        )
    }
}
