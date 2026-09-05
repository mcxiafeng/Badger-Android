package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "PhotoTextRecognizer"

/**
 * [KMP K10] 照片文字识别引擎 iOS actual：Apple Vision `VNRecognizeTextRequest`。
 *
 * 中文识别：recognitionLanguages = ["zh-Hans", "en-US"] + 语言校正；
 * accurate 级别（fast 级别中文质量不足；iOS 14+ 支持中文，真机验收登记 K17）。
 * 执行走 VNImageRequestHandler 的 CGImage 类便捷入口（无 handler 实例化工厂）。
 * 识别率与 Android（ML Kit）双端对照表见 docs/spike/。
 */
@OptIn(ExperimentalForeignApi::class)
actual class PhotoTextRecognizer {

    private fun recognize(image: PlatformImage, withBoundingBoxes: Boolean): Pair<String, List<TextBlockBox>> {
        val cgImage = image.uiImage.CGImage ?: return "" to emptyList()
        val imgWidth = image.width.toFloat()
        val imgHeight = image.height.toFloat()

        var fullText = ""
        val blocks = mutableListOf<TextBlockBox>()
        val request = VNRecognizeTextRequest { req, _ ->
            if (req == null) return@VNRecognizeTextRequest
            @Suppress("UNCHECKED_CAST")
            val observations = req.results as? List<VNRecognizedTextObservation> ?: return@VNRecognizeTextRequest
            fullText = observations.mapNotNull { obs ->
                // K/N 不导入 ObjC 轻量泛型：topCandidates 返回 List<*>，元素需显式 cast
                (obs.topCandidates(1uL).firstOrNull() as? VNRecognizedText)?.string
            }.joinToString("\n")
            if (withBoundingBoxes) {
                for (obs in observations) {
                    obs.boundingBox.useContents {
                        // Vision boundingBox 为归一化坐标、原点左下 → 换算为左上原点像素空间
                        val left = origin.x * imgWidth
                        val top = (1.0 - origin.y - size.height) * imgHeight
                        val right = left + size.width * imgWidth
                        val bottom = top + size.height * imgHeight
                        blocks.add(
                            TextBlockBox(
                                corners = listOf(
                                    QrPoint(left.toFloat(), top.toFloat()),
                                    QrPoint(right.toFloat(), top.toFloat()),
                                    QrPoint(right.toFloat(), bottom.toFloat()),
                                    QrPoint(left.toFloat(), bottom.toFloat())
                                )
                            )
                        )
                    }
                }
            }
        }
        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        request.recognitionLanguages = listOf("zh-Hans", "en-US")
        request.usesLanguageCorrection = true

        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            // 位置参数：initWithCGImage:options: 工厂首字母下沉为 cGImage，命名参数易踩坑
            val handler = VNImageRequestHandler(cgImage, emptyMap<Any?, Any?>())
            handler.performRequests(listOf(request), error.ptr)
            error.value?.let { err ->
                BadgerLog.e(TAG, "Vision 识别失败: ${err.localizedDescription}", null)
                return "" to emptyList()
            }
        }
        return fullText to blocks
    }

    actual suspend fun recognizeText(image: PlatformImage): String =
        recognize(image, withBoundingBoxes = false).first

    actual suspend fun detectTextBlocks(image: PlatformImage): List<TextBlockBox> =
        recognize(image, withBoundingBoxes = true).second

    actual fun close() {
        // Vision 无常驻识别器资源，无操作
    }
}
