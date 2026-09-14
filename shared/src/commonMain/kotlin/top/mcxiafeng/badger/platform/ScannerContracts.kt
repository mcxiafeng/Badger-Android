package top.mcxiafeng.badger.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

data class QrPoint(val x: Float, val y: Float)

data class QrDetection(
    val content: String,
    val corners: List<QrPoint>
)

data class TextBlockBox(
    val corners: List<QrPoint>
)

enum class CameraMode {
    
    PHOTO,

    
    SCAN
}

const val QR_MASK_PADDING_PX = 16

expect class QrCodeDetector() {
    
    fun detectContents(image: PlatformImage): List<String>

    
    fun detectWithBounds(image: PlatformImage): List<QrDetection>

    

    fun maskQrRegions(image: PlatformImage, detections: List<QrDetection>, paddingPx: Int): PlatformImage
}

expect class PhotoTextRecognizer() {
    
    suspend fun recognizeText(image: PlatformImage): String

    
    suspend fun detectTextBlocks(image: PlatformImage): List<TextBlockBox>

    
    fun close()
}

expect fun notifyScannerDialogDismissed()

expect suspend fun loadOrientedImage(bytes: ByteArray): PlatformImage?

fun buildBitmapToComposeMapper(
    bitmapSize: Size,
    viewSize: Size,
): (Offset) -> Offset {
    if (bitmapSize.width <= 0f || bitmapSize.height <= 0f) return { Offset.Zero }
    if (viewSize.width <= 0f || viewSize.height <= 0f) return { Offset.Zero }

    
    val fillScale = maxOf(
        viewSize.width / bitmapSize.width,
        viewSize.height / bitmapSize.height
    )
    val fillOffsetX = (viewSize.width - bitmapSize.width * fillScale) / 2f
    val fillOffsetY = (viewSize.height - bitmapSize.height * fillScale) / 2f

    return { offset ->
        Offset(offset.x * fillScale + fillOffsetX, offset.y * fillScale + fillOffsetY)
    }
}

expect object QrEngineBootstrap {
    suspend fun ensureReady()
}
