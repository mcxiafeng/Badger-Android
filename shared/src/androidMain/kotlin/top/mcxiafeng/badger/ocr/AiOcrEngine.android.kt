package top.mcxiafeng.badger.ocr

import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

class AndroidAiOcrEngine : AiOcrEngine {

    override suspend fun recognizeImage(image: PlatformImage): AiOcrResult =
        withContext(BadgerDispatchers.io) {
            when (val r = AiOcrService.recognizeImageWithFallback(image.bitmap)) {
                is AiOcrService.AiOcrServiceResult.Success ->
                    AiOcrResult.Success(r.data.toExtractedContactInfo(r.rawText), r.rawText)
                is AiOcrService.AiOcrServiceResult.Error -> AiOcrResult.Error(r.message)
            }
        }

    override suspend fun recognizeText(text: String): AiOcrResult =
        withContext(BadgerDispatchers.io) {
            when (val r = AiOcrService.recognizeFromTextWithFallback(text)) {
                is AiOcrService.AiOcrServiceResult.Success ->
                    AiOcrResult.Success(r.data.toExtractedContactInfo(r.rawText), r.rawText)
                is AiOcrService.AiOcrServiceResult.Error -> AiOcrResult.Error(r.message)
            }
        }
}
