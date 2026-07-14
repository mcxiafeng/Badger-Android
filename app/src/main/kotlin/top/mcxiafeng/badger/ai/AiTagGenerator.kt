package top.mcxiafeng.badger.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.HttpUtil

/**
 * AI 标签生成器
 *
 * 根据联系人自我介绍 + 已有 Tag 列表,调用 LLM 推荐标签。
 * - 共享 [AiOcrConfig] 的 endpoint/api_key 配置(同一供应商通常用同一个 Key),
 *   model 走 [AiOcrConfig.getTagModel] 独立槽(默认 deepseek-chat)。
 * - LLM 调用失败时抛 [AiTagException],调用方应降级为 [fallbackLocal] 或跳过 AI 推荐。
 */
class AiTagGenerator(
    private val context: Context
) {
    private companion object {
        const val TAG = "AiTagGenerator"
        val gson = Gson()

        /** AI 推荐"新标签"时的默认色板(按 confidence 在 5 色间插值)。 */
        val NEW_TAG_PALETTE = longArrayOf(
            0xFF1976D2L, // Material Blue
            0xFF388E3CL, // Material Green
            0xFFE64A19L, // Material Deep Orange
            0xFF7B1FA2L, // Material Purple
            0xFFF57C00L, // Material Orange
        )

        fun colorForConfidence(confidence: Float): Long {
            val idx = (confidence * (NEW_TAG_PALETTE.size - 1)).toInt()
                .coerceIn(0, NEW_TAG_PALETTE.lastIndex)
            return NEW_TAG_PALETTE[idx]
        }
    }

    /** AI 返回的标签候选,带颜色提示 + 匹配标记。 */
    data class TagCandidate(
        val name: String,
        val color: Long,
        /** 是否匹配已有 Tag(true 时直接复用 id;false 时新创建 Tag) */
        val matchedExisting: Boolean,
        val existingTagId: Long? = null,
        val confidence: Float = 0.5f
    )

    /** 把 [HttpResult.Failure] 转成可读的错误信息(含 HTTP code + errorType) */
    private fun friendlyHttpError(failure: HttpResult.Failure): String {
        return when (failure.errorType) {
            HttpResult.ErrorType.AUTH -> "API Key 无效或权限不足 (${failure.code})"
            HttpResult.ErrorType.RATE_LIMIT -> "请求被限流 (429)"
            HttpResult.ErrorType.TIMEOUT -> "请求超时"
            HttpResult.ErrorType.SERVER -> "AI 服务端错误 (${failure.code})"
            HttpResult.ErrorType.NETWORK -> "网络连接失败"
            HttpResult.ErrorType.OTHER -> "请求被拒绝 (${failure.code})"
            HttpResult.ErrorType.UNKNOWN -> "未知错误 (${failure.code})"
        }
    }

    /** 调用 LLM 推荐标签。失败抛 [AiTagException] 触发降级。 */
    suspend fun suggest(bio: String, existingTags: List<Tag>): List<TagCandidate> = withContext(Dispatchers.IO) {
        require(bio.isNotBlank()) { "bio must not be blank" }

        // [修复防御]: AI Tag 入口 fail-fast,三道闸门(开关/隐私/配置)任一未过就抛
        if (!AiOcrConfig.isAiTagEnabled(context)) {
            Log.w(TAG, "suggest: AI 标签未启用,抛 AiTagException 触发降级")
            throw AiTagException("AI 标签功能未启用")
        }
        if (!AiOcrConfig.isAiTagPrivacyAgreed(context)) {
            Log.w(TAG, "suggest: AI 标签隐私未同意,抛 AiTagException 触发降级")
            throw AiTagException("请先在 AI 设置中同意把联系人介绍发送给 AI 服务")
        }

        val endpoint = AiOcrConfig.getApiEndpoint(context)
        val apiKey = AiOcrConfig.getApiKey(context)
        // model 走 Tag 独立槽(默认 deepseek-chat),fallback 到 OCR 的 model 字段
        val model = AiOcrConfig.getTagModel(context)
            .ifBlank { AiOcrConfig.getModel(context) }

        if (endpoint.isBlank() || apiKey.isBlank() || model.isBlank()) {
            Log.w(TAG, "suggest: AI 服务未配置,抛 AiTagException 触发降级")
            throw AiTagException("未配置 AI 服务(endpoint/apiKey/model)")
        }

        val existingNames = existingTags.map { it.name }
        val requestBody = AiTagRequestBuilder.buildRequestBody(model, bio, existingNames)
        val headers = mapOf("Authorization" to "Bearer $apiKey")
        Log.d(TAG, "suggest: 调 LLM, bio.len=${bio.length}, existingTags=${existingTags.size}, model=$model")

        val response = when (val result = HttpUtil.postResult(
            urlStr = endpoint, body = requestBody, headers = headers, timeoutMs = 60_000
        )) {
            is HttpResult.Success -> result.body
            is HttpResult.Failure -> {
                Log.e(TAG, "suggest: ${result.code} ${result.errorType}, body前200=${result.body?.take(200)}")
                throw AiTagException("AI 调用失败: ${friendlyHttpError(result)}")
            }
        }
        Log.d(TAG, "suggest: 收到响应, 长度=${response.length}, 前200字=${response.take(200)}")

        val content = extractContent(response)
            ?: throw AiTagException("API 响应缺少 content 字段")

        val parsed = AiTagRequestBuilder.parseContent(content)
        if (parsed.isEmpty()) {
            Log.w(TAG, "suggest: LLM 解析后候选为空")
            throw AiTagException("LLM 未返回有效标签")
        }
        Log.d(TAG, "suggest: LLM 返回 ${parsed.size} 个候选")

        // 与已有 Tag 比对,生成 candidate 列表
        val existingByName = existingTags.associateBy { it.name }
        parsed.map { p: AiTagRequestBuilder.ParsedTag ->
            val match = existingByName[p.name]
            TagCandidate(
                name = p.name,
                color = match?.color ?: colorForConfidence(p.confidence),
                matchedExisting = match != null,
                existingTagId = match?.id,
                confidence = p.confidence
            )
        }
    }

    /**
     * 本地启发式:对 bio 分词后 substring 匹配已有 Tag.name。
     * 用于 AI 不可用时的降级路径。
     */
    fun fallbackLocal(bio: String, existingTags: List<Tag>): List<TagCandidate> {
        if (existingTags.isEmpty() || bio.isBlank()) return emptyList()
        val bioLower = bio.lowercase()
        return existingTags
            .filter { bioLower.contains(it.name.lowercase()) }
            .map {
                TagCandidate(
                    name = it.name,
                    color = it.color,
                    matchedExisting = true,
                    existingTagId = it.id,
                    confidence = 0.7f
                )
            }
    }

    private fun extractContent(response: String): String? {
        return try {
            val obj = gson.fromJson(response, JsonObject::class.java)
            if (obj.has("error")) {
                val msg = obj.getAsJsonObject("error").get("message")?.asString
                Log.e(TAG, "extractContent: API 错误 - $msg")
                throw AiTagException("API 错误: $msg")
            }
            val choices = obj.getAsJsonArray("choices") ?: return null
            if (choices.size() == 0) return null
            val content = choices[0].asJsonObject
                .getAsJsonObject("message")
                .get("content")?.asString
            content?.takeIf { it.isNotBlank() }
        } catch (e: AiTagException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "extractContent: 解析响应失败", e)
            null
        }
    }
}

/**
 * AI 标签生成失败时抛此异常。调用方应降级为 [AiTagGenerator.fallbackLocal] 或跳过 AI 推荐。
 */
class AiTagException(message: String) : RuntimeException(message)
