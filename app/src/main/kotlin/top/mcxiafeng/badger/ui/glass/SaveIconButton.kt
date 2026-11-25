package top.mcxiafeng.badger.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Every glass control uses one square touch target so toolbar and standalone buttons align. */
internal val GlassButtonTarget = BadgerSize.minimumTouchTarget
internal val GlassButtonIcon = BadgerRadius.icon
private val SaveButtonRadius = GlassButtonTarget / 2

/**
 * Circular save affordance: gray neutral fill while required content is missing, accent once valid,
 * both at the shared glass button opacity. The press halo/shrink comes from [glassPressFeedback].
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
    val fill = if (ready) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant
    val interactions = remember { MutableInteractionSource() }
    val glassModifier = if (glass) {
        Modifier.buttonGlass(SaveButtonRadius, ready)
    } else {
        Modifier.background(fill, CircleShape)
    }
    Box(
        modifier.size(GlassButtonTarget)
            .then(glassModifier)
            .glassPressFeedback(enabled, SaveButtonRadius)
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription,
            modifier = Modifier.size(GlassButtonIcon),
            tint = if (glass) glassButtonForeground()
            else if (ready) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
        )
    }
}
