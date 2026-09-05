package top.mcxiafeng.badger.ocr

import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

/**
 * [KMP K13c] AI OCR 引擎的平台实现。
 *
 * - Android actual = [AndroidAiOcrEngine]（封装 AiOcrService：OkHttp + ServerApiFactory，
 *   Bitmap→JPEG base64，2048px 上限）；
 * - iOS actual = [IosAiOcrEngine] 骨架（K16 经 KtorHttpCore 接线）。
 *
 * Koin 注册：app 壳层 `single<AiOcrEngine> { ... }`；调用方（ScannerComponents）经
 * KoinComponentBy 静态取用（与原 object AiOcrService 调用形态等价）。
 */

/** Android 实现：AiOcrService 阻塞调用包 IO 调度器。 */
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
