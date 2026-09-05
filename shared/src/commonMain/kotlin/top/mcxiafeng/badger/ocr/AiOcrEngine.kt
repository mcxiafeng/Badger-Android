package top.mcxiafeng.badger.ocr

import top.mcxiafeng.badger.platform.PlatformImage

/**
 * [KMP K13c] AI 名片识别引擎契约（原 androidMain `AiOcrService` object 的接口化）。
 *
 * Android actual = AiOcrService（OkHttp + ServerApiFactory，Bitmap/Base64 细节封装在 actual 内）；
 * iOS actual = 骨架（K16 经 KtorHttpCore 接线）。
 *
 * 协议约束保持不变：`stream:false`（ModelScope SSE 规避）、文字/视觉模式同超时（60s）、
 * Gson→kotlinx 的 JsonNull 守卫（见 K04 备注④）。
 */
sealed class AiOcrResult {
    /** 服务端结构化解析成功（data 已转 common 领域模型）。 */
    data class Success(val data: ExtractedContactInfo, val rawText: String?) : AiOcrResult()

    /** 网络/解析/上游失败（message 为用户可读文案）。 */
    data class Error(val message: String) : AiOcrResult()
}

interface AiOcrEngine {

    /** 视觉模式：整图识别（AI 启用且服务端下发了 vision 模型时）。 */
    suspend fun recognizeImage(image: PlatformImage): AiOcrResult

    /** 文字模式：ML Kit/Vision OCR 文本 → AI 结构化（慢模型同超时）。 */
    suspend fun recognizeText(text: String): AiOcrResult
}
