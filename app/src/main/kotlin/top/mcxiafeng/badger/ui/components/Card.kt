package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** Keep Miuix's click/hold/feedback behavior while drawing the shared continuous container outline. */
@Composable
internal fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = BadgerRadius.surface,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    showIndication: Boolean = false,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(cornerRadius) { BadgerShapes.rounded(cornerRadius) }
    val moving = pressFeedbackType != PressFeedbackType.None
    val nativeColors = remember(colors, moving) { if (moving) colors.copy(color = Color.Transparent) else colors }
    LaunchedEffect(cornerRadius, moving) {
        Log.d("CardTester", "Continuous container; radiusDp=${cornerRadius.value}; nativeCornerDisabled=true; movingOutline=$moving")
    }
    if (onClick == null && onLongPress == null && !moving && !holdDownState && !showIndication) {
        MiuixCard(modifier = modifier.clip(shape), cornerRadius = 0.dp, insideMargin = insideMargin,
            colors = colors, content = content)
        return
    }
    MiuixCard(modifier = if (moving) modifier else modifier.clip(shape), cornerRadius = 0.dp,
        insideMargin = if (moving) PaddingValues() else insideMargin,
        colors = nativeColors, pressFeedbackType = pressFeedbackType, showIndication = showIndication,
        holdDownState = holdDownState, onClick = onClick, onLongPress = onLongPress) {
        if (moving) Column(Modifier.fillMaxWidth().background(colors.color, shape).clip(shape).padding(insideMargin), content = content)
        else content()
    }
}
