package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Native thumb, drag, keyboard, haptics and switch semantics are retained. */
@Composable
internal fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    val target = when {
        checked && enabled -> colors.primary
        checked -> colors.disabledPrimary
        enabled -> colors.secondary
        else -> colors.disabledSecondary
    }
    val track = animateColorAsState(target, tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing), label = "switch track")
    LaunchedEffect(checked, enabled) { Log.d("SwitchTester", "Continuous track; checked=$checked; enabled=$enabled") }
    val surface = modifier.drawWithCache {
        val width = minOf(BadgerSize.switchTrackWidth.roundToPx().toFloat(), size.width)
        val height = minOf(BadgerSize.switchTrackHeight.roundToPx().toFloat(), size.height)
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val path = Path().apply { addG2RoundRect(RoundRect(left, top, left + width, top + height, CornerRadius(height / 2f))) }
        onDrawBehind { drawPath(path, track.value) }
    }
    MiuixSwitch(checked, onCheckedChange, surface,
        SwitchDefaults.switchColors(checkedTrackColor = Color.Transparent, uncheckedTrackColor = Color.Transparent,
            disabledCheckedTrackColor = Color.Transparent, disabledUncheckedTrackColor = Color.Transparent), enabled)
}

@Composable
internal fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    BasicComponent(title = title, summary = summary, modifier = modifier,
        enabled = enabled, onClick = if (enabled) ({
            Log.d("SwitchPreferenceTester", "Preference toggle requested; checked=${!checked}")
            onCheckedChange(!checked)
        }) else null, endActions = { Switch(checked, onCheckedChange, enabled = enabled) })
}
