package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect

internal const val BUTTON_PRESS_SHRINK = 0.19f
internal const val BUTTON_PRESS_GLOW_ALPHA = 0.55f
private const val BUTTON_PRESS_SHRINK_MS = 90
private const val BUTTON_PRESS_GLOW_IN_MS = 80
private const val BUTTON_PRESS_RELEASE_MS = 220
private const val BUTTON_PRESS_LOG_TAG = "GlassButtonPressTester"

/**
 * Press choreography shared by every glass control: shrink 19% at the touch point, overshoot 19%
 * while held, then spring back with a slight bounce on release. Layout bounds and hit targets never
 * change, so touch geometry stays stable.
 */
@Composable
internal fun Modifier.glassPressFeedback(enabled: Boolean, radius: Dp): Modifier {
    if (!enabled) return this
    val scale = remember { Animatable(1f) }
    val glow = remember { Animatable(0f) }
    val touch = remember { mutableStateOf(Offset.Unspecified) }
    val pressed = remember { mutableStateOf(false) }
    LaunchedEffect(pressed.value, enabled) {
        if (!enabled) {
            scale.snapTo(1f)
            glow.snapTo(0f)
            return@LaunchedEffect
        }
        if (pressed.value) {
            Log.d(BUTTON_PRESS_LOG_TAG, "Press down at ${touch.value}; shrink=$BUTTON_PRESS_SHRINK")
            launch { glow.animateTo(1f, tween(BUTTON_PRESS_GLOW_IN_MS)) }
            scale.stop()
            scale.animateTo(1f - BUTTON_PRESS_SHRINK,
                tween(BUTTON_PRESS_SHRINK_MS, easing = FastOutSlowInEasing))
            scale.animateTo(1f + BUTTON_PRESS_SHRINK,
                spring(dampingRatio = 0.62f, stiffness = 380f, visibilityThreshold = 0.0001f))
        } else {
            Log.d(BUTTON_PRESS_LOG_TAG, "Press release; spring back with bounce")
            launch { glow.animateTo(0f, tween(BUTTON_PRESS_RELEASE_MS)) }
            scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 480f, visibilityThreshold = 0.0001f))
        }
    }
    return this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touch.value = down.position
                pressed.value = true
                waitForUpOrCancellation()
                pressed.value = false
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .drawWithContent {
            val strength = glow.value
            if (strength > 0.01f && touch.value != Offset.Unspecified) {
                val path = Path().apply {
                    addG2RoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius.toPx())))
                }
                clipPath(path) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = strength * BUTTON_PRESS_GLOW_ALPHA), Color.Transparent),
                            center = touch.value,
                            radius = size.maxDimension * 1.1f,
                        ),
                        blendMode = BlendMode.Plus,
                    )
                }
            }
            drawContent()
        }
}
