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
                
                (obs.topCandidates(1uL).firstOrNull() as? VNRecognizedText)?.string
            }.joinToString("\n")
            if (withBoundingBoxes) {
                for (obs in observations) {
                    obs.boundingBox.useContents {
                        
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
        
    }
}
