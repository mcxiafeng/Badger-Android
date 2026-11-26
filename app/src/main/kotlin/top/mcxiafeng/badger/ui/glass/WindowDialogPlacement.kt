package top.mcxiafeng.badger.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.layout.DialogDefaults

@Immutable
internal data class WindowDialogSpec(
    val title: String?, val titleColor: Color, val summary: String?, val summaryColor: Color,
    val background: Color, val outside: DpSize, val inside: DpSize,
    val defaultInsets: Boolean, val dim: Boolean, val guard: Dp,
)

/** Preserve the existing phone-bottom/tablet-centre geometry while the visible panel moves. */
@Composable
internal fun WindowDialogPlacement(
    motion: PopupMotionState, spec: WindowDialogSpec, modifier: Modifier,
    onDismiss: (() -> Unit)?, content: @Composable () -> Unit,
) {
    val size = LocalWindowInfo.current.containerDpSize
    val large = size.width >= BadgerSize.dialogExpandedWidth && size.height >= BadgerSize.dialogExpandedHeight
    val insets = if (spec.defaultInsets) WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.ime)
        else WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)
    val horizontal = maxOf(spec.outside.width, spec.guard)
    val bottom = maxOf(spec.outside.height, spec.guard)
    val height = remember { mutableIntStateOf(0) }
    val bottomPx = with(LocalDensity.current) { bottom.toPx() } + WindowInsets.navigationBars.getBottom(LocalDensity.current)
    PopupDim(motion, spec.dim && !LocalGlassStyle.current.enabled)
    Box(Modifier.fillMaxSize()
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() },
            enabled = motion.visible && onDismiss != null) { onDismiss?.invoke() }
        .windowInsetsPadding(insets).padding(horizontal = horizontal).padding(bottom = bottom),
        contentAlignment = if (large) Alignment.Center else Alignment.BottomCenter) {
        Column(modifier.widthIn(max = (DialogDefaults.MaxWidth - spec.guard * 2).coerceAtLeast(Dp.Hairline))
            .heightIn(max = if (large) size.height * BadgerSize.DIALOG_LARGE_HEIGHT_FRACTION - spec.guard * 2 else Dp.Unspecified)
            .onSizeChanged { height.intValue = it.height }
            .graphicsLayer {
                val progress = motion.progress.value
                alpha = progress.coerceIn(0f, 1f)
                if (large) {
                    scaleX = BadgerSize.DIALOG_ENTER_SCALE + (1f - BadgerSize.DIALOG_ENTER_SCALE) * progress
                    scaleY = scaleX
                } else translationY = (1f - progress) * (height.intValue + bottomPx)
            }
            .pointerInput(Unit) { detectTapGestures { } },
            content = { content() })
    }
}
