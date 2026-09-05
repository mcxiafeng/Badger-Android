package top.mcxiafeng.badger.ocr

import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * [KMP K13c] iOS AI OCR 骨架（K16 经 KtorHttpCore 接线 `/api/proxy/ai/tasks/contact_ocr`，
 * 协议约束与 Android actual 相同）。
 */
class IosAiOcrEngine : AiOcrEngine {

    override suspend fun recognizeImage(image: PlatformImage): AiOcrResult {
        BadgerLog.w("AiOcrEngine.ios", "recognizeImage: iOS 骨架未接线（K16）")
        return AiOcrResult.Error("AI 识别即将支持")
    }

    override suspend fun recognizeText(text: String): AiOcrResult {
        BadgerLog.w("AiOcrEngine.ios", "recognizeText: iOS 骨架未接线（K16）")
        return AiOcrResult.Error("AI 识别即将支持")
    }
}
