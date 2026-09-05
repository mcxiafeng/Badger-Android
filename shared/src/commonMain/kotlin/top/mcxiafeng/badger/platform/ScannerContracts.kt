package top.mcxiafeng.badger.platform

/**
 * [KMP K10] 扫码引擎公共契约：QR 检测 + 文字识别（OCR）。
 *
 * - Android actual：WeChatQRCode（OpenCV native）+ ML Kit 中文识别，逻辑自
 *   `pages/scanner/QrCodeUtils.kt` / ScannerCamera / ScannerComponents 原样迁移。
 * - iOS actual：CoreImage `CIDetectorTypeQRCode` + Vision `VNRecognizeTextRequest`（中文）。
 *
 * 引擎均为同步/挂起调用，不含 UI；识别率双端对照表见 docs/spike/（真机验收登记 K17）。
 */

/** 图像坐标点（像素空间，角点/框顶点通用）。 */
data class QrPoint(val x: Float, val y: Float)

/** 单个 QR 码检测结果：内容 + 原图像素空间 4 角点（顺序与引擎输出一致）。 */
data class QrDetection(
    val content: String,
    val corners: List<QrPoint>
)

/** 单个文字块检测结果：仅包围框 4 角点（像素空间），不含文字内容。 */
data class TextBlockBox(
    val corners: List<QrPoint>
)

/** 扫码模式（原 ScannerUi.CameraMode，随相机边界迁 shared）。 */
enum class CameraMode {
    /** 多码模式：实时多码框选 + 文字区域检测 */
    PHOTO,

    /** 扫码模式：单码识别 */
    SCAN
}

/** QR 蒙版默认外扩像素（原 maskQrRegions paddingPx 默认值）。 */
const val QR_MASK_PADDING_PX = 16

/**
 * QR 检测引擎（expect）。Android=WeChatQRCode，iOS=CoreImage。
 */
expect class QrCodeDetector {
    /** 识别图像中的全部 QR 码内容（内部自带 fitToMax 预处理）。 */
    fun detectContents(image: PlatformImage): List<String>

    /** 识别 QR 码内容 + 角点，坐标已按预处理缩放比例还原到原图像素空间。 */
    fun detectWithBounds(image: PlatformImage): List<QrDetection>

    /**
     * 将 QR 区域用白色遮盖（防 OCR 误识别 QR 像素），返回新图；
     * 无 QR 码时返回原 [image] 实例（调用方据实例同一性判断是否需释放）。
     */
    fun maskQrRegions(image: PlatformImage, detections: List<QrDetection>, paddingPx: Int): PlatformImage
}

/**
 * 照片文字识别引擎（expect）。Android=ML Kit 中文，iOS=Vision。
 *
 * Android 侧实例级别复用 ML Kit client（每帧新建开销 50-100ms，页面级持有），
 * 用完调 [close] 释放。
 */
expect class PhotoTextRecognizer {
    /** 整图 OCR：返回拼接后的全部文字。 */
    suspend fun recognizeText(image: PlatformImage): String

    /** 仅取文字块包围框（像素空间），用于相机多码模式的框选叠加。 */
    suspend fun detectTextBlocks(image: PlatformImage): List<TextBlockBox>

    /** 释放底层识别器资源（页面 DisposableEffect 调用）。 */
    fun close()
}
