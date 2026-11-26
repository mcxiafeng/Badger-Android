package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val CLIPPED_STROKE_WIDTH_MULTIPLIER = 2f

/** Miuix retains editing, floating labels, input transformations and keyboard handling. */
@Composable
internal fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = TextFieldDefaults.InsideMargin,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    cornerRadius: Dp = BadgerRadius.surface,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MiuixTheme.textStyles.main,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(colors.borderColor),
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val nativeColors = remember(colors) { colors.copy(backgroundColor = Color.Transparent, borderColor = Color.Transparent) }
    MiuixTextField(value, onValueChange, modifier.g2TextFieldSurface(cornerRadius, colors, interactions),
        insideMargin, nativeColors, 0.dp, label, useLabelAsPlaceholder, enabled, readOnly, textStyle,
        keyboardOptions, keyboardActions, leadingIcon, trailingIcon, singleLine, maxLines, minLines,
        visualTransformation, onTextLayout, interactions, cursorBrush)
}

@Composable
private fun Modifier.g2TextFieldSurface(radius: Dp, colors: TextFieldColors, interactions: MutableInteractionSource): Modifier {
    val focused by interactions.collectIsFocusedAsState()
    val progress = animateFloatAsState(if (focused) 1f else 0f,
        tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing), label = "input focus border")
    val shape = remember(radius) { BadgerShapes.rounded(radius) }
    LaunchedEffect(focused, radius) {
        Log.d("TextFieldTester", "Continuous input; radiusDp=${radius.value}; focused=$focused; nativeBackgroundAndBorder=false")
    }
    return clip(shape).drawWithCache {
        val path = Path().apply {
            addG2RoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius.toPx())))
        }
        onDrawBehind {
            drawPath(path, colors.backgroundColor)
            val fraction = progress.value
            if (fraction > 0f) {
                val stroke = BadgerSize.inputFocusBorderWidth.toPx() * CLIPPED_STROKE_WIDTH_MULTIPLIER * fraction
                drawPath(path, lerp(colors.backgroundColor, colors.borderColor, fraction), style = Stroke(stroke))
            }
        }
    }
}
