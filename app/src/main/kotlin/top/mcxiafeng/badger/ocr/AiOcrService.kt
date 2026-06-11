package top.mcxiafeng.badger.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.utils.HttpUtil

/**
 * AI OCR 服务
 *
 * 支持两种模式：
 * - Vision 模式：将图片直接发到 AI（需模型支持图片输入）
 * - 纯文本模式：ML Kit OCR 提取文字后发给 AI 解析
 */
object AiOcrService {
    private const val TAG = "AiOcrService"
    private val gson = Gson()
    /** 降级时最多尝试的模型数量（含初始模型） */
    private const val MAX_FALLBACK_ATTEMPTS = 3

    /**
     * Vision 模式：将图片发到 AI 识别
     */
    suspend fun recognizeImage(
        context: android.content.Context,
        bitmap: Bitmap
    ): AiOcrServiceResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = AiOcrConfig.getApiEndpoint(context)
            val apiKey = AiOcrConfig.getApiKey(context)
            val model = AiOcrConfig.getModel(context)

            Log.d(TAG, "recognizeImage: endpoint=${endpoint.take(80)}, model=$model, bitmap=${bitmap.width}x${bitmap.height}, apiKey=${if (apiKey.isNotBlank()) "已配置(${apiKey.length}字符)" else "未配置"}")

            if (endpoint.isBlank() || apiKey.isBlank()) {
                Log.w(TAG, "recognizeImage: 未配置 API Endpoint 或 API Key")
                return@withContext AiOcrServiceResult.Error("未配置 API Endpoint 或 API Key")
            }
            if (model.isBlank()) {
                Log.w(TAG, "recognizeImage: 未配置模型")
                return@withContext AiOcrServiceResult.Error("未配置模型")
            }

            val base64Image = AiOcrRequestBuilder.bitmapToBase64(bitmap)
            Log.d(TAG, "recognizeImage: base64长度=${base64Image.length}")
            val requestBody = AiOcrRequestBuilder.buildVisionRequestBody(model, base64Image)
            Log.d(TAG, "recognizeImage: 请求体长度=${requestBody.length}")

            val headers = mapOf("Authorization" to "Bearer $apiKey")
            val response = HttpUtil.post(
                urlStr = endpoint, body = requestBody, headers = headers, timeoutMs = 120_000
            )

            if (response == null) {
                Log.e(TAG, "recognizeImage: 网络请求失败，response 为 null")
                return@withContext AiOcrServiceResult.Error("网络请求失败")
            }
            Log.d(TAG, "recognizeImage: 收到响应，长度=${response.length}, 前200字=${response.take(200)}")
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "AI OCR vision mode failed", e)
            AiOcrServiceResult.Error(e.message ?: "未知错误")
        }
    }

    /**
     * 纯文本模式：将 OCR 文字发给 AI 解析
     */
    suspend fun recognizeFromText(
        context: android.content.Context,
        ocrText: String
    ): AiOcrServiceResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = AiOcrConfig.getApiEndpoint(context)
            val apiKey = AiOcrConfig.getApiKey(context)
            val model = AiOcrConfig.getModel(context)

            Log.d(TAG, "recognizeFromText: endpoint=${endpoint.take(80)}, model=$model, ocrText长度=${ocrText.length}, 前200字=${ocrText.take(200)}")

            if (endpoint.isBlank() || apiKey.isBlank()) {
                Log.w(TAG, "recognizeFromText: 未配置 API Endpoint 或 API Key")
                return@withContext AiOcrServiceResult.Error("未配置 API Endpoint 或 API Key")
            }

            val requestBody = AiOcrRequestBuilder.buildTextRequestBody(model, ocrText)
            Log.d(TAG, "recognizeFromText: 请求体长度=${requestBody.length}")

            val headers = mapOf("Authorization" to "Bearer $apiKey")
            val response = HttpUtil.post(
                urlStr = endpoint, body = requestBody, headers = headers, timeoutMs = 120_000
            )

            if (response == null) {
                Log.e(TAG, "recognizeFromText: 网络请求失败，response 为 null")
                return@withContext AiOcrServiceResult.Error("网络请求失败")
            }
            Log.d(TAG, "recognizeFromText: 收到响应，长度=${response.length}, 前200字=${response.take(200)}")
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "AI OCR text mode failed", e)
            AiOcrServiceResult.Error(e.message ?: "未知错误")
        }
    }

    /**
     * 测试 API 连接
     *
     * 发送一条简短纯文本请求验证连通性，不走 SYSTEM_PROMPT。
     */
    suspend fun testApi(context: android.content.Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val endpoint = AiOcrConfig.getApiEndpoint(context)
            val apiKey = AiOcrConfig.getApiKey(context)
            val model = AiOcrConfig.getModel(context)

            Log.d(TAG, "testApi: endpoint=${endpoint.take(80)}, model=$model, apiKey=${if (apiKey.isNotBlank()) "已配置(${apiKey.length}字符)" else "未配置"}")

            if (endpoint.isBlank()) return@withContext Result.failure(IllegalArgumentException("未配置 API 地址"))
            if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("未配置 API Key"))
            if (model.isBlank()) return@withContext Result.failure(IllegalArgumentException("未配置模型"))

            val startTime = System.currentTimeMillis()
            val requestBody = AiOcrRequestBuilder.buildTestRequestBody(model)
            Log.d(TAG, "testApi: 发送测试请求, body长度=${requestBody.length}")

            val headers = mapOf("Authorization" to "Bearer $apiKey")
            val response = HttpUtil.post(urlStr = endpoint, body = requestBody, headers = headers, timeoutMs = 30_000)

            if (response == null) {
                Log.e(TAG, "testApi: 网络请求失败，response 为 null, 耗时=${System.currentTimeMillis() - startTime}ms")
                return@withContext Result.failure(Exception("网络请求失败（无响应）"))
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "testApi: 收到响应, 耗时=${elapsed}ms, 长度=${response.length}, 前200字=${response.take(200)}")

            val jsonResponse = gson.fromJson(response, JsonObject::class.java)
            if (jsonResponse.has("error")) {
                val errorMsg = jsonResponse.getAsJsonObject("error").get("message")?.asString ?: "API 返回错误"
                Log.e(TAG, "testApi: API 返回错误 - $errorMsg, 耗时=${elapsed}ms")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val choices = jsonResponse.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                Log.e(TAG, "testApi: 无 choices, 耗时=${elapsed}ms, 响应键=${jsonResponse.keySet()}")
                return@withContext Result.failure(Exception("API 未返回有效结果"))
            }

            val content = choices[0].asJsonObject.getAsJsonObject("message").get("content")?.asString
            Log.d(TAG, "testApi: 连接成功, AI返回=${content?.take(100)}, 耗时=${elapsed}ms")
            Result.success("连接成功（耗时 ${elapsed}ms）")
        } catch (e: Exception) {
            Log.e(TAG, "testApi: 异常 - ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取可用模型列表
     *
     * 调用 /v1/models 或 /models 端点获取 API 支持的模型列表。
     */
    suspend fun fetchModels(
        context: android.content.Context
    ): Pair<List<ModelInfo>, String?> = withContext(Dispatchers.IO) {
        try {
            val endpoint = AiOcrConfig.getApiEndpoint(context)
            val apiKey = AiOcrConfig.getApiKey(context)

            if (endpoint.isBlank()) return@withContext Pair(emptyList(), "未配置 API 地址")
            if (apiKey.isBlank()) return@withContext Pair(emptyList(), "未配置 API Key")

            // 从端点推导 models 端点
            val apiPath = AiOcrConfig.getApiPath(context)
            val modelsUrl = endpoint.replace(Regex(Regex.escape(apiPath) + "$"), "").trimEnd('/') + "/models"
            Log.d(TAG, "fetchModels: 推导 models URL=$modelsUrl")

            val headers = mapOf("Authorization" to "Bearer $apiKey")
            val response = HttpUtil.get(modelsUrl, headers = headers)
                ?: return@withContext Pair(emptyList(), "网络请求失败（无响应），请检查 API 地址是否正确")

            val json = try {
                gson.fromJson(response, JsonObject::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "fetchModels: 响应非 JSON 格式", e)
                return@withContext Pair(emptyList(), "响应格式错误，请确认 API 地址指向 OpenAI 兼容接口")
            }

            // 检查 API 返回的错误
            if (json.has("error")) {
                val errorMsg = json.getAsJsonObject("error").get("message")?.asString ?: "API 返回错误"
                Log.e(TAG, "fetchModels: API 错误 - $errorMsg")
                return@withContext Pair(emptyList(), errorMsg)
            }

            val dataArray = json.getAsJsonArray("data")
                ?: return@withContext Pair(emptyList(), "响应中无 data 字段，请确认 API 地址格式正确")

            val models = dataArray.mapNotNull { item ->
                val obj = item.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                ModelInfo(id = id, supportsVision = AiOcrRequestBuilder.isVisionModel(id))
            }.sortedBy { it.id }

            val error = if (models.isEmpty()) "未获取到模型列表" else null
            Pair(models, error)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch models", e)
            Pair(emptyList(), "获取失败: ${e.message}")
        }
    }

    /**
     * 带降级的 Vision 模式识别
     *
     * 当前模型失败时，如果 autoFallback 开启，自动尝试 API 中的下一个可用模型。
     * 最多尝试 MAX_FALLBACK_ATTEMPTS 个模型。
     */
    suspend fun recognizeImageWithFallback(
        context: android.content.Context,
        bitmap: Bitmap
    ): AiOcrServiceResult {
        val firstResult = recognizeImage(context, bitmap)
        if (firstResult is AiOcrServiceResult.Success) return firstResult
        if (!AiOcrConfig.isAutoFallback(context)) return firstResult

        return fallbackToNextModels(context) { model ->
            recognizeImageWithModel(context, bitmap, model)
        }
    }

    /**
     * 带降级的纯文本模式识别
     */
    suspend fun recognizeFromTextWithFallback(
        context: android.content.Context,
        ocrText: String
    ): AiOcrServiceResult {
        val firstResult = recognizeFromText(context, ocrText)
        if (firstResult is AiOcrServiceResult.Success) return firstResult
        if (!AiOcrConfig.isAutoFallback(context)) return firstResult

        return fallbackToNextModels(context) { model ->
            recognizeFromTextWithModel(context, ocrText, model)
        }
    }

    /**
     * 降级逻辑：获取模型列表，跳过当前模型，依次尝试后续模型
     */
    private suspend fun fallbackToNextModels(
        context: android.content.Context,
        tryModel: suspend (String) -> AiOcrServiceResult
    ): AiOcrServiceResult {
        val currentModel = AiOcrConfig.getModel(context)
        val (allModels, _) = fetchModels(context)
        if (allModels.isEmpty()) {
            Log.w(TAG, "fallbackToNextModels: 无法获取模型列表，降级失败")
            return AiOcrServiceResult.Error("当前模型失败且无法获取可用模型列表")
        }

        // 找到当前模型之后的所有模型
        val currentIndex = allModels.indexOfFirst { it.id == currentModel }
        val fallbackModels = if (currentIndex >= 0) {
            allModels.drop(currentIndex + 1)
        } else {
            allModels.filter { it.id != currentModel }
        }

        if (fallbackModels.isEmpty()) {
            Log.w(TAG, "fallbackToNextModels: 当前模型是列表中最后一个，无降级目标")
            return AiOcrServiceResult.Error("当前模型失败且无可用降级模型")
        }

        val attempts = fallbackModels.take(MAX_FALLBACK_ATTEMPTS - 1)
        for (model in attempts) {
            Log.d(TAG, "fallbackToNextModels: 降级尝试模型 ${model.id}")
            val result = tryModel(model.id)
            if (result is AiOcrServiceResult.Success) {
                Log.d(TAG, "fallbackToNextModels: 降级成功，使用模型 ${model.id}")
                return result
            }
            Log.w(TAG, "fallbackToNextModels: 模型 ${model.id} 也失败: ${(result as AiOcrServiceResult.Error).message}")
        }

        Log.w(TAG, "fallbackToNextModels: 所有降级模型均失败")
        return AiOcrServiceResult.Error("所有模型均请求失败")
    }

    /**
     * Vision 模式：指定模型名识别
     */
    private suspend fun recognizeImageWithModel(
        context: android.content.Context,
        bitmap: Bitmap,
        model: String
    ): AiOcrServiceResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = AiOcrConfig.getApiEndpoint(context)
            val apiKey = AiOcrConfig.getApiKey(context)
            if (endpoint.isBlank() || apiKey.isBlank()) {
                return@withContext AiOcrServiceResult.Error("未配置 API Endpoint 或 API Key")
            }
            val base64Image = AiOcrRequestBuilder.bitmapToBase64(bitmap)
            val requestBody = AiOcrRequestBuilder.buildVisionRequestBody(model, base64Image)
            val headers = mapOf("Authorization" to "Bearer $apiKey")
            val response = HttpUtil.post(
                urlStr = endpoint,
                body = requestBody,
                headers = headers,
                timeoutMs = 120_000
            ) ?: return@withContext AiOcrServiceResult.Error("网络请求失败")
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "recognizeImage vision mode failed", e)
            AiOcrServiceResult.Error(e.message ?: "未知错误")
        }
    }

    /**
     * 纯文本模式：指定模型名识别
     */
    private suspend fun recognizeFromTextWithModel(
        context: android.content.Context,
        ocrText: String,
        model: String
    ): AiOcrServiceResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = AiOcrConfig.getApiEndpoint(context)
            val apiKey = AiOcrConfig.getApiKey(context)
            if (endpoint.isBlank() || apiKey.isBlank()) {
                return@withContext AiOcrServiceResult.Error("未配置 API Endpoint 或 API Key")
            }
            val requestBody = AiOcrRequestBuilder.buildTextRequestBody(model, ocrText)
            val headers = mapOf("Authorization" to "Bearer $apiKey")
            val response = HttpUtil.post(
                urlStr = endpoint,
                body = requestBody,
                headers = headers,
                timeoutMs = 120_000
            ) ?: return@withContext AiOcrServiceResult.Error("网络请求失败")
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "recognizeFromText text mode failed", e)
            AiOcrServiceResult.Error(e.message ?: "未知错误")
        }
    }

    private fun parseResponse(response: String): AiOcrServiceResult {
        try {
            Log.d(TAG, "parseResponse: 原始响应长度=${response.length}")
            val jsonResponse = gson.fromJson(response, JsonObject::class.java)

            if (jsonResponse.has("error")) {
                val error = jsonResponse.getAsJsonObject("error")
                val message = error.get("message")?.asString ?: "API 返回错误"
                Log.e(TAG, "parseResponse: API 返回错误 - $message, error对象=${error}")
                return AiOcrServiceResult.Error(message)
            }

            val choicesElement = jsonResponse.get("choices")
            if (choicesElement == null || choicesElement.isJsonNull || !choicesElement.isJsonArray) {
                Log.e(TAG, "parseResponse: choices 为空或非数组, choicesElement=$choicesElement, 响应键=${jsonResponse.keySet()}, usage=${jsonResponse.get("usage")}")
                return AiOcrServiceResult.Error("API 未返回有效结果（choices 为空），可能模型不可用或请求格式不兼容")
            }
            val choices = choicesElement.asJsonArray
            if (choices.size() == 0) {
                Log.e(TAG, "parseResponse: 无 choices, 响应键=${jsonResponse.keySet()}")
                return AiOcrServiceResult.Error("API 未返回有效结果")
            }

            val content = choices[0].asJsonObject
                .getAsJsonObject("message")
                .get("content")?.asString

            if (content.isNullOrBlank()) {
                Log.e(TAG, "parseResponse: content 为空, choice=${choices[0].asJsonObject}")
                return AiOcrServiceResult.Error("AI 未返回识别结果")
            }

            val cleanedContent = AiOcrRequestBuilder.extractJsonBlock(content)

            Log.d(TAG, "parseResponse: AI 原始返回=${content.take(500)}")
            Log.d(TAG, "parseResponse: 清理后JSON=${cleanedContent.take(500)}")

            val result = gson.fromJson(cleanedContent, AiOcrResult::class.java)
            Log.d(TAG, "parseResponse: 解析结果 name=${result.name}, phone=${result.phone}, email=${result.email}, wechat=${result.wechat}, qq=${result.qq}, bilibili=${result.bilibili}, weibo=${result.weibo}, douyin=${result.douyin}, github=${result.github}, telegram=${result.telegram}, xiaohongshu=${result.xiaohongshu}, facebook=${result.facebook}, x=${result.x}, website=${result.website}, other=${result.other}")
            return AiOcrServiceResult.Success(result, cleanedContent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response (长度=${response.length}): ${response.take(300)}", e)
            return AiOcrServiceResult.Error("解析结果失败: ${e.message}")
        }
    }
}
