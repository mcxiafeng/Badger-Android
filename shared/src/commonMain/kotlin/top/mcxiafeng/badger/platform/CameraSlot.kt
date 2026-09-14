package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CameraPreviewSlot(
    modifier: Modifier = Modifier,
    isFlashOn: Boolean,
    isScanningPaused: Boolean = false,
    onImageCaptured: (PlatformImage) -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onQrCodesWithBounds: (List<QrDetection>, Int, Int) -> Unit = { _, _, _ -> },
    onTextBlocksDetected: (List<TextBlockBox>, Int, Int) -> Unit = { _, _, _ -> },
    onPreviewSizeChanged: (viewWidth: Int, viewHeight: Int, surfaceWidth: Int, surfaceHeight: Int) -> Unit = { _, _, _, _ -> },
    mode: CameraMode,
    aiOcrEnabled: Boolean = false,
    takePhotoTrigger: Int = 0
)
