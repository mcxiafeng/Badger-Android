package top.mcxiafeng.badger.ocr

import top.mcxiafeng.badger.platform.PlatformImage

sealed class AiOcrResult {
    
    data class Success(val data: ExtractedContactInfo, val rawText: String?) : AiOcrResult()

    
    data class Error(val message: String) : AiOcrResult()
}

interface AiOcrEngine {

    
    suspend fun recognizeImage(image: PlatformImage): AiOcrResult

    
    suspend fun recognizeText(text: String): AiOcrResult
}
