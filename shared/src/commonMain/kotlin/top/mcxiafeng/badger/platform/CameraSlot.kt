package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [KMP K10] 相机扫码面（expect Composable 槽位）：预览 + 拍照 + 帧分析回调 + 手电筒。
 *
 * - Android actual：现有 CameraX 实现（原 `pages/scanner/ScannerCamera.kt` 逻辑不动迁入
 *   shared androidMain）——预览 PreviewView、ImageAnalysis 喂 WeChatQRCode、拍照经
 *   EXIF 旋转后回调、点击对焦、手电筒 onDispose 关闭等既有约束全部保持。
 * - iOS actual：AVFoundation 骨架（AVCaptureSession + metadata QR 检测 + Vision OCR），
 *   CMP iOS 实接登记 K16（iosApp 工程）/ 真机验收 K17。
 *
 * scanner 页 UI（ScannerPage）保持平台无关，只消费本槽位与 [QrCodeDetector] 等引擎契约。
 *
 * @param isFlashOn 手电筒开关（重组时重新 enableTorch）
 * @param isScanningPaused 暂停分析（结果对话框打开时停帧，帧直接 close）
 * @param onImageCaptured 拍照完成回调，图像已按 EXIF 旋转；所有权移交调用方
 * @param onQrCodeDetected 扫码模式：识别到单码内容（500ms 节流 + 2s 关闭冷却由 actual 内部维持）
 * @param onQrCodesWithBounds 多码模式：QR 检测结果 + 对应 bitmap 宽高（像素空间）
 * @param onTextBlocksDetected 多码模式：文字块包围框 + 对应 bitmap 宽高（像素空间）
 * @param onPreviewSizeChanged PreviewView 布局尺寸与内部 surface 尺寸变化
 * @param mode 相机模式（决定分析器走单码/多码路径）
 * @param aiOcrEnabled 多码模式是否同时检测文字区域（ML Kit 派发协程，不阻塞 analyzer 线程）
 * @param takePhotoTrigger 拍照触发计数器（自增触发一次拍照）
 */
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
