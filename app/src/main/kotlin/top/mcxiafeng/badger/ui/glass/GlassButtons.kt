package top.mcxiafeng.badger.ui.glass

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton as MaterialIconButtonBase
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingActionButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val GlassIconRadius = 24.dp
private val FloatingActionRadius = 30.dp

@Composable
private fun Modifier.continuousButton(enabled: Boolean = true, radius: Dp = GlassButtonTarget): Modifier =
    buttonGlass(radius, enabled).clip(BadgerShapes.rounded(radius))

@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = GlassButtonTarget,
    minWidth: Dp = GlassButtonTarget,
    minHeight: Dp = GlassButtonTarget,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = LocalGlassStyle.current.buttonsEnabled
    val styledColors = if (glass) ButtonColors(
        Color.Transparent, Color.Transparent, glassButtonForeground(), colors.disabledContentColor,
    ) else colors
    MiuixButton(onClick,
        modifier.continuousButton(enabled, cornerRadius).glassPressFeedback(glass && enabled, cornerRadius),
        enabled, 0.dp, minWidth, minHeight, styledColors, insideMargin, interactionSource, indication, content)
}

@Composable
internal fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = GlassButtonTarget,
    minWidth: Dp = GlassButtonTarget,
    minHeight: Dp = GlassButtonTarget,
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    textStyle: TextStyle? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
) {
    val glass = LocalGlassStyle.current.buttonsEnabled
    val styledColors = if (glass) TextButtonColors(
        Color.Transparent, Color.Transparent, glassButtonForeground(), colors.disabledTextColor,
    ) else colors
    MiuixTextButton(text, onClick,
        modifier.continuousButton(enabled, cornerRadius).glassPressFeedback(glass && enabled, cornerRadius),
        enabled, 0.dp, minWidth, minHeight, styledColors, insideMargin, textStyle, interactionSource, indication)
}

/**
 * Accepts either an [icon] with [contentDescription] or a trailing content lambda so existing
 * call sites keep their custom artwork. Glass mode unifies the target to 48dp with a circular fill.
 */
@Composable
internal fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
    minWidth: Dp = Dp.Unspecified,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    iconSize: Dp = GlassButtonIcon,
    content: (@Composable () -> Unit)? = null,
) {
    val glass = LocalGlassStyle.current.buttonsEnabled
    val grouped = LocalGroupedActions.current
    val unified = glass || grouped
    val radius = if (cornerRadius != Dp.Unspecified) cornerRadius
    else if (unified) GlassIconRadius else IconButtonDefaults.CornerRadius
    val resolvedMinWidth = if (minWidth != Dp.Unspecified) minWidth
    else if (unified) GlassButtonTarget else IconButtonDefaults.MinWidth
    val resolvedMinHeight = if (minHeight != Dp.Unspecified) minHeight
    else if (unified) GlassButtonTarget else IconButtonDefaults.MinHeight
    MiuixIconButton(
        onClick,
        modifier.continuousButton(enabled, radius).glassPressFeedback(unified && enabled, radius),
        enabled, holdDownState, if (glass) Color.Transparent else backgroundColor,
        0.dp, resolvedMinHeight, resolvedMinWidth,
    ) {
        GlassButtonContent(enabled) {
            if (content != null) {
                content()
            } else if (icon != null) {
                Icon(icon, contentDescription, modifier = Modifier.size(iconSize))
            }
        }
    }
}

@Composable
internal fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = MiuixTheme.colorScheme.primary,
    shadowElevation: Dp = FloatingActionButtonDefaults.ShadowElevation,
    minWidth: Dp = FloatingActionButtonDefaults.MinWidth,
    minHeight: Dp = FloatingActionButtonDefaults.MinHeight,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val glass = LocalGlassStyle.current.buttonsEnabled
    MiuixFloatingActionButton(onClick,
        modifier.continuousButton(true, FloatingActionRadius).glassPressFeedback(glass, FloatingActionRadius),
        shape, if (glass) Color.Transparent else containerColor, shadowElevation, minWidth, minHeight) {
        GlassButtonContent(content = {
            if (content != null) {
                content()
            } else if (icon != null) {
                Icon(icon, contentDescription, modifier = Modifier.size(GlassButtonIcon))
            }
        })
    }
}

@Composable
internal fun GlassButtonContent(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (LocalGlassStyle.current.buttonsEnabled) {
        val color = if (enabled) glassButtonForeground() else MiuixTheme.colorScheme.disabledOnSurface
        CompositionLocalProvider(LocalContentColor provides color, content = content)
    } else {
        content()
    }
}

private const val BLACK_WHITE_CONTRAST_CROSSOVER = 0.179f

@Composable
internal fun glassButtonForeground(
    classic: Color = MiuixTheme.colorScheme.onSurface,
    underlay: Color = LocalGlassUnderlay.current,
): Color {
    if (!LocalGlassStyle.current.buttonsEnabled) return classic
    val colors = MiuixTheme.colorScheme
    val background = if (underlay == Color.Unspecified) colors.surface else underlay
    val approximateFill = colors.primary.copy(alpha = BUTTON_TINT_ALPHA).compositeOver(background)
    return if (approximateFill.luminance() > BLACK_WHITE_CONTRAST_CROSSOVER) Color.Black else Color.White
}

@Composable
internal fun GlassActionText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MiuixTheme.textStyles.body1,
    color: Color = MiuixTheme.colorScheme.primary,
    classicPadding: PaddingValues = PaddingValues(),
) {
    val padding = if (LocalGlassStyle.current.buttonsEnabled) {
        PaddingValues(horizontal = BadgerSpacing.sm, vertical = BadgerSpacing.xs)
    } else {
        classicPadding
    }
    Text(text, modifier.continuousButton().clickable(role = Role.Button, onClick = onClick).padding(padding),
        style = style, color = glassButtonForeground(color))
}

/** Retains the Material icon button's accessibility, ripple and enabled state. */
@Composable
internal fun MaterialIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!LocalGlassStyle.current.buttonsEnabled) {
        MaterialIconButtonBase(onClick, modifier, enabled, content = content)
        return
    }
    Box(modifier.widthIn(min = GlassButtonTarget).heightIn(min = GlassButtonTarget)
        .continuousButton(), propagateMinConstraints = true) {
        MaterialIconButtonBase(onClick, enabled = enabled, content = content)
    }
}
