package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Scale
import java.io.File
import kotlin.math.roundToInt
import top.mcxiafeng.badger.data.todo.TodoBackgroundScale
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TodoCardBackground(
    file: File, scale: TodoBackgroundScale, modifier: Modifier = Modifier,
    contentDescription: String? = null, onError: () -> Unit = {},
) {
    var imageReady by remember(file) { mutableStateOf(false) }
    val tint = if (MiuixTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE) Color.Black else Color.White
    val scrimColor = CardDefaults.defaultColors().color
    LaunchedEffect(scrimColor) { Log.d(TAG, "Left scrim follows the default task card color: $scrimColor") }
    val placement = remember(scale) { TodoImageScale(scale) }
    Box(modifier.clipToBounds()) {
        AsyncImage(model = file, contentDescription = contentDescription, modifier = Modifier.matchParentSize(),
            alignment = RightQuarter, contentScale = placement, onState = { state ->
                imageReady = state is AsyncImagePainter.State.Success
                if (state is AsyncImagePainter.State.Error) {
                    Log.w(TAG, "Could not load the task background (${state.result.throwable.javaClass.simpleName})")
                    onError()
                }
            })
        if (imageReady) {
            TodoSoftBackground(file, placement, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().drawWithCache {
                val scrim = Brush.horizontalGradient(
                    0f to scrimColor, FULL_SCRIM_POSITION to scrimColor, CLEAR_SCRIM_POSITION to scrimColor.copy(alpha = 0f),
                )
                onDrawBehind {
                    drawRect(tint, alpha = BACKGROUND_TINT_ALPHA)
                    drawRect(scrim)
                }
            })
        }
    }
}

@Composable
private fun TodoSoftBackground(file: File, placement: ContentScale, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(context, file) {
        ImageRequest.Builder(context).data(file).size(TodoGaussianBlur.THUMBNAIL_EDGE)
            .scale(Scale.FIT).allowHardware(false).transformations(TodoGaussianBlur).build()
    }
    AsyncImage(model = request, contentDescription = null, alignment = RightQuarter, contentScale = placement,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val blend = Brush.horizontalGradient(
                    0f to Color.Black, BLUR_BLEND_POSITION to Color.Black, IMAGE_CENTER_FRACTION to Color.Transparent,
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(blend, blendMode = BlendMode.DstIn)
                }
            },
        onError = { Log.w(TAG, "Could not load the blurred task background") },
    )
}

/** The right half is the image viewport. Fill may extend into the card under the scrim. */
private class TodoImageScale(private val mode: TodoBackgroundScale) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size) =
        (if (mode == TodoBackgroundScale.FILL) ContentScale.Crop else ContentScale.Fit)
            .computeScaleFactor(srcSize, Size(dstSize.width * IMAGE_VIEWPORT_FRACTION, dstSize.height))
}

private val RightQuarter = Alignment { size, space, _ ->
    IntOffset((space.width * IMAGE_CENTER_FRACTION - size.width / 2f).roundToInt(), (space.height - size.height) / 2)
}
private const val IMAGE_VIEWPORT_FRACTION = 0.5f
private const val IMAGE_CENTER_FRACTION = 0.75f
private const val BLUR_BLEND_POSITION = 0.625f
private const val BACKGROUND_TINT_ALPHA = 0.25f
private const val FULL_SCRIM_POSITION = 0.25f
private const val CLEAR_SCRIM_POSITION = 0.5f
private const val DARK_SURFACE_LUMINANCE = 0.5f
private const val TAG = "TodoCardBackgroundTester"
