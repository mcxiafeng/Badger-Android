package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.utils.BadgerLog

@Composable
actual fun CameraPreviewSlot(
    modifier: Modifier,
    isFlashOn: Boolean,
    isScanningPaused: Boolean,
    onImageCaptured: (PlatformImage) -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onQrCodesWithBounds: (List<QrDetection>, Int, Int) -> Unit,
    onTextBlocksDetected: (List<TextBlockBox>, Int, Int) -> Unit,
    onPreviewSizeChanged: (viewWidth: Int, viewHeight: Int, surfaceWidth: Int, surfaceHeight: Int) -> Unit,
    mode: CameraMode,
    aiOcrEnabled: Boolean,
    takePhotoTrigger: Int
) {
    
    SideEffect {
        BadgerLog.w(
            "CameraPreviewSlot",
            "iOS 相机骨架：AVFoundation 实接登记 K16（iosApp 工程），真机验收 K17"
        )
    }
}
