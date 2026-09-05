package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * [KMP K10] 相机扫码面 iOS actual：AVFoundation 骨架（仅编译占位，无预览画面）。
 *
 * K16（iosApp 工程）实接时按以下语义实现，与 Android actual 对齐：
 * - 预览：AVCaptureSession + .builtInWideAngleCamera（back）+ AVCaptureVideoPreviewLayer
 *   经 ComposeUIView/UIViewRepresentable 嵌入本 Composable 槽位；
 * - 扫码（[CameraMode.SCAN]）：AVCaptureMetadataOutput metadataObjects（AVMetadataObjectTypeQRCode）
 *   委托回调 → 500ms 节流 + 2s 关闭冷却 → [onQrCodeDetected]；
 * - 多码（[CameraMode.PHOTO]）：metadataOutput 同源回调 → [onQrCodesWithBounds]
 *   （坐标换算 preview→bitmap 空间需对齐 Android FILL_CENTER 语义，参考 QrCoordinateMapper）；
 *   文字区域经 [PhotoTextRecognizer.detectTextBlocks] → [onTextBlocksDetected]；
 * - 拍照：AVCapturePhotoOutput + PhotoCaptureDelegate → UIImage（EXIF 方向已含）→
 *   PlatformImage → [onImageCaptured]（所有权移交调用方）；
 * - 手电筒：AVCaptureDevice torchMode 开关；[isScanningPaused]=true 时停止分发回调帧；
 * - 点击对焦：AVCaptureDeviceFocusPointOfInterest + exposurePointOfInterest。
 *
 * 真机验收（识别率对照表 / 权限文案 / 热身时序）登记 K17。
 */
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
    // 骨架占位：无 UI（cmp-foundation 布局随 K13/CMP 坐标统一切换时一并落）
    SideEffect {
        BadgerLog.w(
            "CameraPreviewSlot",
            "iOS 相机骨架：AVFoundation 实接登记 K16（iosApp 工程），真机验收 K17"
        )
    }
}
