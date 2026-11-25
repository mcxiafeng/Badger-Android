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
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.designsystem.addG2RoundRect
import top.yukonga.miuix.kmp.basic.ColorPicker as MiuixColorPicker
import top.yukonga.miuix.kmp.basic.ColorSpace
import top.yukonga.miuix.kmp.basic.SliderDefaults

/** Keep native color-space conversion, haptics and drag mapping; replace only visible corner masks. */
@Composable
internal fun ColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
    colorSpace: ColorSpace = ColorSpace.HSV,
) {
    LaunchedEffect(colorSpace) { Log.d("ColorPickerTester", "Continuous color tracks; colorSpace=$colorSpace; nativeMapping=true") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        if (showPreview) Box(Modifier.fillMaxWidth().height(BadgerSize.colorPalettePreviewHeight)
            .background(color, BadgerShapes.Surface))
        MiuixColorPicker(color, onColorChanged, Modifier.colorTrackViewports(), false, hapticEffect, colorSpace)
    }
}

private fun Modifier.colorTrackViewports(): Modifier = drawWithCache {
    val height = BadgerSize.colorSliderTrackHeight.roundToPx().toFloat()
    val gap = BadgerSpacing.md.roundToPx().toFloat()
    val radius = BadgerRadius.surface.toPx()
    val inset = BadgerSize.colorSliderClipInset.toPx()
    val path = Path().apply {
        repeat(BadgerSize.COLOR_PICKER_CHANNELS) { channel ->
            val top = channel * (height + gap)
            if (top < size.height) addG2ColorTrack(Rect(0f, top, size.width, minOf(top + height, size.height)), radius, inset)
        }
    }
    onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
}

/** A tiny inset puts the G2 mask wholly inside the native circle clip, without moving its input coordinates. */
internal fun Path.addG2ColorTrack(bounds: Rect, radius: Float, inset: Float) {
    if (bounds.width <= 2f * inset || bounds.height <= 2f * inset) return
    addG2RoundRect(RoundRect(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset,
        CornerRadius(radius)))
}
