package top.mcxiafeng.badger.pages.todo

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The image midpoint aligns to the card's right quarter. Increase the Gaussian radius to its left. */
internal object TodoGaussianBlur : Transformation() {
    const val THUMBNAIL_EDGE = 108
    private const val BLUR_STEPS = 24
    private const val MAX_SIGMA = 3.6
    private const val SIGMA_WINDOW = 3.0
    private const val WEIGHT_SCALE = 4096
    override val cacheKey = "badger.todo.progressiveGaussian.$THUMBNAIL_EDGE"

    private val kernels = (0..BLUR_STEPS).map { step ->
        val sigma = MAX_SIGMA * step / BLUR_STEPS
        val radius = (sigma * SIGMA_WINDOW).roundToInt()
        IntArray(radius * 2 + 1) { index ->
            val offset = index - radius
            if (offset == 0) WEIGHT_SCALE
            else (WEIGHT_SCALE * exp(-0.5 * (offset / sigma) * (offset / sigma))).roundToInt()
        }
    }
    private val normalizers = kernels.map { it.sum() }

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val factor = minOf(1f, THUMBNAIL_EDGE.toFloat() / maxOf(input.width, input.height))
        val width = (input.width * factor).roundToInt().coerceAtLeast(1)
        val height = (input.height * factor).roundToInt().coerceAtLeast(1)
        val sample = input.scale(width, height)
        val pixels = IntArray(width * height)
        try {
            sample.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            if (sample !== input) sample.recycle()
        }
        val intermediate = IntArray(pixels.size)
        convolve(pixels, intermediate, width, height, horizontal = true)
        convolve(intermediate, pixels, width, height, horizontal = false)
        Log.d(TAG, "Prepared progressive Gaussian task background layer, ${width}x$height")
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun convolve(from: IntArray, to: IntArray, width: Int, height: Int, horizontal: Boolean) {
        val midpoint = width / 2f
        for (x in 0 until width) {
            val step = ((1f - x / midpoint).coerceIn(0f, 1f) * BLUR_STEPS).roundToInt()
            val kernel = kernels[step]
            val radius = kernel.size / 2
            val normalizer = normalizers[step]
            for (y in 0 until height) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                for (index in kernel.indices) {
                    val offset = index - radius
                    val sourceX = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                    val sourceY = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                    val pixel = from[sourceY * width + sourceX]
                    val weight = kernel[index]
                    alpha += (pixel ushr ALPHA_SHIFT) * weight
                    red += ((pixel ushr RED_SHIFT) and CHANNEL_MASK) * weight
                    green += ((pixel ushr GREEN_SHIFT) and CHANNEL_MASK) * weight
                    blue += (pixel and CHANNEL_MASK) * weight
                }
                to[y * width + x] = ((alpha / normalizer) shl ALPHA_SHIFT) or
                    ((red / normalizer) shl RED_SHIFT) or ((green / normalizer) shl GREEN_SHIFT) or (blue / normalizer)
            }
        }
    }

    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val CHANNEL_MASK = 255
    private const val TAG = "TodoGaussianBlurTester"
}
