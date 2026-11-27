package top.mcxiafeng.badger.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Every glass control uses one square touch target so toolbar and standalone buttons align. */
internal val GlassButtonTarget = BadgerSize.minimumTouchTarget
internal val GlassButtonIcon = BadgerRadius.icon
private val SaveButtonRadius = GlassButtonTarget / 2

/**
 * Circular save affordance: gray neutral fill while required content is missing, accent once valid.
 * Flat-mode fill and icon tint cross-fade over 150ms; press feedback uses [glassPressFeedback].
 */
@Composable
internal fun SaveIconButton(
    ready: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "保存",
) {
    val style = LocalGlassStyle.current
    val glass = style.buttonsEnabled
    val colors = MiuixTheme.colorScheme
    val fill by animateColorAsState(
        if (ready) colors.primary else colors.surfaceVariant,
        tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing), label = "saveFill",
    )
    val flatTint by animateColorAsState(
        if (ready) colors.onPrimary else colors.onSurface,
        tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing), label = "saveTint",
    )
    val interactions = remember { MutableInteractionSource() }
    val feedback = rememberButtonTouchFeedback(enabled)
    val glassModifier = if (glass) {
        Modifier.buttonGlass(SaveButtonRadius, ready)
    } else {
        Modifier.background(fill, CircleShape)
    }
    Box(
        modifier.size(GlassButtonTarget)
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .glassPressFeedback(enabled, SaveButtonRadius, feedback)
            .then(glassModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.buttonPressContentAlpha(feedback)) {
            Icon(
                Icons.Default.Check,
                contentDescription,
                modifier = Modifier.size(GlassButtonIcon),
                tint = if (glass && !ready && !style.materialEnabled) MiuixTheme.colorScheme.disabledOnSurface
                else if (glass) glassButtonForeground()
                else flatTint,
            )
        }
    }
}
