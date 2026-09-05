package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 二维码生成边界（替代 Methods.generateQRCode 的平台半边）。
 *
 * Android actual = ZXing QRCodeWriter → Bitmap（语义逐行对齐原实现：ERROR_CORRECTION_M、
 * 1:1 边距）；
 * iOS actual = CoreImage CIFilter.qrCodeGenerator（K16 接线，当前返回 null 并记日志）。
 *
 * 颜色参数为 ARGB int（0xAARRGGBB），与原 android.graphics.Color 入参一致。
 */
expect object QrCodeGenerator {
    fun generate(content: String, sizePx: Int, foregroundColor: Int, backgroundColor: Int): PlatformImage?
}
