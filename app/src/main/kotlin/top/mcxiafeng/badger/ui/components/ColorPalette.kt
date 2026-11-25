package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect
import top.yukonga.miuix.kmp.basic.ColorPalette as MiuixColorPalette

/** Only preview/grid clipping changes; HSV selection, alpha interaction and callbacks remain native. */
@Composable
internal fun ColorPalette(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    rows: Int = BadgerSize.COLOR_PALETTE_ROWS,
    hueColumns: Int = BadgerSize.COLOR_PALETTE_HUE_COLUMNS,
    includeGrayColumn: Boolean = true,
    showPreview: Boolean = true,
    cornerRadius: Dp = BadgerRadius.surface,
    indicatorRadius: Dp = BadgerSize.colorPaletteIndicatorRadius,
) {
    LaunchedEffect(cornerRadius) { Log.d("ColorPaletteTester", "Continuous palette viewport; radiusDp=${cornerRadius.value}; nativeColorMapping=true") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        if (showPreview) Box(Modifier.fillMaxWidth().height(BadgerSize.colorPalettePreviewHeight)
            .background(color, BadgerShapes.Surface))
        MiuixColorPalette(color, onColorChanged, Modifier.paletteViewport(cornerRadius), rows, hueColumns,
            includeGrayColumn, showPreview = false, cornerRadius = 0.dp, indicatorRadius = indicatorRadius)
    }
}

private fun Modifier.paletteViewport(radius: Dp): Modifier = drawWithCache {
    // Locked Miuix 0.9.3 places a 180dp grid before its alpha slider; preserve that layout and touch mapping.
    val height = minOf(BadgerSize.colorPaletteGridHeight.roundToPx().toFloat(), size.height)
    val path = Path().apply {
        addG2RoundRect(RoundRect(0f, 0f, size.width, height, CornerRadius(radius.toPx())))
        val alphaTop = height + BadgerSpacing.md.roundToPx()
        if (size.height > alphaTop) addG2ColorTrack(Rect(0f, alphaTop, size.width,
            minOf(size.height, alphaTop + BadgerSize.colorSliderTrackHeight.roundToPx())),
            BadgerRadius.surface.toPx(), BadgerSize.colorSliderClipInset.toPx())
    }
    onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
}
