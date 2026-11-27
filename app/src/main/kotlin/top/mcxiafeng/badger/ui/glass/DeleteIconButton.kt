package top.mcxiafeng.badger.ui.glass

import android.util.Log
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Circular delete affordance matching [SaveIconButton] parameter for parameter: the same 48dp
 * target, [Modifier.buttonGlass] surface (50% tinted glass while the material is on, fully opaque
 * otherwise), press halo and release recoil. Only the ready color and icon differ; the fill
 * crossfades over the shared 239ms motion.
 */
@Composable
internal fun DeleteIconButton(
    ready: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "删除",
) {
    val style = LocalGlassStyle.current
    val glass = style.buttonsEnabled
    val flatFill = if (ready) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.surfaceVariant
    val animatedFlat by animateColorAsState(
        targetValue = flatFill,
        animationSpec = tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing),
        label = "DeleteButtonFill",
    )
    // Same fill rule as the save button, with the danger accent standing in for the primary one.
    val glassFill = style.buttonFill(ready, MiuixTheme.colorScheme.error)
    val animatedGlassFill by animateColorAsState(
        targetValue = glassFill,
        animationSpec = tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing),
        label = "DeleteButtonGlassFill",
    )
    val interactions = remember { MutableInteractionSource() }
    val feedback = rememberButtonTouchFeedback(enabled)
    LaunchedEffect(glass, style.materialEnabled, ready) {
        Log.d(GLASS_LOG_TAG, "Delete button; glass=$glass; material=${style.materialEnabled}; " +
            "fillAlpha=${if (style.materialEnabled) BUTTON_TINT_ALPHA else 1f}; ready=$ready")
    }
    val glassModifier = if (glass) {
        Modifier.buttonGlass(DeleteButtonRadius, animatedGlassFill, LocalGlassUnderlay.current)
    } else {
        Modifier.background(animatedFlat, CircleShape)
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
            .glassPressFeedback(enabled, DeleteButtonRadius, feedback)
            .then(glassModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.buttonPressContentAlpha(feedback)) {
            val iconTint = if (ready) MiuixTheme.colorScheme.onError else MiuixTheme.colorScheme.onSurface
            val animatedIcon by animateColorAsState(
                targetValue = iconTint,
                animationSpec = tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing),
                label = "DeleteButtonIconTint",
            )
            Icon(
                Icons.Default.Delete,
                contentDescription,
                modifier = Modifier.size(GlassButtonIcon),
                tint = if (glass) glassButtonForeground(classic = animatedIcon) else animatedIcon,
            )
        }
    }
}

private val DeleteButtonRadius = GlassButtonTarget / 2
