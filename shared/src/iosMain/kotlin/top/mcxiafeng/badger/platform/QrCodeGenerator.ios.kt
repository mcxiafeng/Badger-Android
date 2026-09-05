package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "QrCodeGenerator.ios"

/**
 * [KMP K13c] iOS actual 骨架：CoreImage CIFilter.qrCodeGenerator 生成 QR（K16 接线，
 * 颜色控制需 CGImage 重着色）。当前返回 null——SocialPage QR 卡在 iOS 渲染占位，
 * 调用方已有空值降级路径。
 */
actual object QrCodeGenerator {
    actual fun generate(
        content: String,
        sizePx: Int,
        foregroundColor: Int,
        backgroundColor: Int,
    ): PlatformImage? {
        BadgerLog.w(TAG, "generate: iOS 骨架未接线（K16 CoreImage）: size=$sizePx")
        return null
    }
}
