package top.mcxiafeng.badger.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.utils.HttpUtil
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

/**
 * AI OCR 识别结果
 *
 * Gson 反序列化的数据类，字段名须与 AI 返回的 JSON key 完全一致。
 * 平台字段通过 platforms Map 动态映射，新增平台无需修改本类。
 */
data class AiOcrResult(
    @SerializedName("name") val name: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("wechat") val wechat: String? = null,
    @SerializedName("qq") val qq: String? = null,
    @SerializedName("bilibili") val bilibili: String? = null,
    @SerializedName("weibo") val weibo: String? = null,
    @SerializedName("douyin") val douyin: String? = null,
    @SerializedName("github") val github: String? = null,
    @SerializedName("telegram") val telegram: String? = null,
    @SerializedName("xiaohongshu") val xiaohongshu: String? = null,
    @SerializedName("facebook") val facebook: String? = null,
    @SerializedName("x") val x: String? = null,
    @SerializedName("website") val website: String? = null,
    @SerializedName("other") val other: List<String> = emptyList()
) {
    /** AI 返回的 JSON 中与 PLATFORM_FIELDS fieldKey 对应的属性名列表 */
    private val platformPropertyNames = PLATFORM_FIELDS.map { it.fieldKey }

    /**
     * 转换为 ExtractedContactInfo 格式（兼容现有流程）
     *
     * 通过反射将 platformPropertyNames 中的非空属性收集到 platforms Map，
     * other 仅存放无法归入上述字段的额外信息
     */
    fun toExtractedContactInfo(rawText: String): ExtractedContactInfo {
        val platforms = mutableMapOf<String, String>()
        for (key in platformPropertyNames) {
            val value = try {
                javaClass.getDeclaredField(key).let {
                    it.isAccessible = true
                    it.get(this) as? String
                }
            } catch (_: NoSuchFieldException) {
                null // AiOcrResult 中没有该字段（如 telegramGroup、qqGroup），跳过
            }
            if (!value.isNullOrBlank()) platforms[key] = value
        }
        return ExtractedContactInfo(
            name = name,
            phone = phone,
            email = email,
            platforms = platforms,
            rawText = rawText,
            otherInfo = other
        )
    }
}

/**
 * AI OCR 服务调用结果
 */
sealed class AiOcrServiceResult {
    data class Success(val data: AiOcrResult, val rawText: String) : AiOcrServiceResult()
    data class Error(val message: String) : AiOcrServiceResult()
}

/**
 * 模型信息
 */
data class ModelInfo(
    val id: String,
    val supportsVision: Boolean = false
)

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

    /** 根据模型名启发式判断是否支持图片输入 */
    fun isVisionModel(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return lower.contains("vision") || lower.contains("vl")
                || lower.contains("4o") || lower.contains("gpt-4")
                || lower.contains("qwen-vl") || lower.contains("qwen2-vl")
                || lower.contains("glm-4v") || lower.contains("clip")
    }

    private const val SYSTEM_PROMPT = """你是一个专业的联系人图片信息提取助手。请仔细分析用户提供的图片或文字，提取其中的联系人真实联系方式。

【重要原则】
- **只提取真实的联系方式**：手机号、邮箱、各平台账号（QQ/微信/B站/微博/抖音/GitHub/Telegram/小红书/Facebook/X/网站）
- **忽略所有非内容元素**：
  * 水印、logo、品牌标识
  * 装饰性文字、图案、边框
  * 广告语、宣传标语
  * 日期、地点等无关信息
  * **平台标签文字**：单独出现的"抖音"、"小红书"、"微信"、"QQ"、"B站"等平台名称只是标签，不是账号值，不要填入对应字段。只有标签后面跟着的具体ID/链接/号码才是值
- **特殊内容处理**：
  * "求扩列"、"交朋友"、"加好友"等社交请求 → 放入 other 数组
  * 签名档、自我介绍等长文本 → 只提取联系方式，其余放入 other
  * 多段文字混排 → 只提取第一段中的真实联系方式

【防漏识别要点】
1. **逐区域扫描**：图片可能包含多个区域（如二维码标签旁的数字、头像旁的昵称、底部小字等），请逐一检查
2. **图标+文字行**：社交卡片常见格式是"平台图标+账号信息"排列，每个图标后通常跟着对应账号
3. **常见中文缩写**：微信=WeChat=VX=wechat，QQ=qq，B站=bilibili=Bilibili，抖音=douyin=dy，微博=weibo=wb，小红书=xhs=Xiaohongshu，GitHub=gh=github，Telegram=tg=TG，Facebook=fb=FB，X=twitter=推特
4. **手机号格式**：可能带有横线(138-0000-0000)、空格(138 0000 0000)或+86前缀，请统一提取为纯数字
5. **同一平台多值**：如果出现两个QQ号或两个微信号，请在对应字段中用逗号分隔
6. **小字/半透明文字**：名片底部或角落的小字也可能是联系方式，不要忽略
7. **二维码旁文字**：二维码下方的文字通常是该码对应的账号信息

请严格按照以下 JSON 格式返回结果（不要添加任何其他文字说明，只返回 JSON）：
{
  "name": "姓名（如果有）",
  "phone": "手机号（如有多个用逗号分隔）",
  "email": "邮箱地址",
  "qq": "QQ号（纯数字，不要链接）",
  "wechat": "微信号或微信链接",
  "bilibili": "B站UID或链接",
  "weibo": "微博用户名或链接",
  "douyin": "抖音号或链接",
  "github": "GitHub用户名或链接",
  "telegram": "Telegram用户名或链接",
  "xiaohongshu": "小红书用户名或链接",
  "facebook": "Facebook用户名或ID（纯数字ID，不要包含链接）",
  "x": "X(Twitter)用户名或ID（纯数字ID，不要包含链接）",
  "website": "网站链接",
  "other": ["其他无法归入以上字段的信息，包括：求扩列、交友请求、广告文案、水印文字等"]
}

注意事项：
1. 如果某个字段不存在，该字段返回 null，不要填写"无"或"未提供"
2. 手机号请提取纯数字格式（去掉横线、空格、+86等）
3. 邮箱请提取完整格式
4. 请优先将信息归入上述具体字段，只有无法归入任何字段时才放入 other 数组
5. Facebook 可能写成 "FB" 或 "facebook"，请识别为 Facebook
6. 如果有多张名片或多个联系人，优先提取最清晰/最突出的那个
7. 如果图片中没有真实联系方式，返回空 JSON 对象 {"name": null, "phone": null, ...}，不要编造信息
8. 只返回 JSON，不要包含 ```json 等代码块标记，不要有任何解释性文字"""

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

            val base64Image = bitmapToBase64(bitmap)
            Log.d(TAG, "recognizeImage: base64长度=${base64Image.length}")
            val requestBody = buildVisionRequestBody(model, base64Image)
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

            val requestBody = buildTextRequestBody(model, ocrText)
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
            val requestBody = buildTestRequestBody(model)
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

    private fun buildTestRequestBody(model: String): String {
        val request = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 32)
            addProperty("stream", false)
            addProperty("temperature", 0)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "user", "content" to "你好")
            )))
        }
        return gson.toJson(request)
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
                ModelInfo(id = id, supportsVision = isVisionModel(id))
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
            val base64Image = bitmapToBase64(bitmap)
            val requestBody = buildVisionRequestBody(model, base64Image)
            val headers = mapOf("Authorization" to "Bearer $apiKey")
            val response = HttpUtil.post(
                urlStr = endpoint,
                body = requestBody,
                headers = headers,
                timeoutMs = 120_000
            ) ?: return@withContext AiOcrServiceResult.Error("网络请求失败")
            parseResponse(response)
        } catch (e: Exception) {
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
            val requestBody = buildTextRequestBody(model, ocrText)
            val headers = mapOf("Authorization" to "Bearer $apiKey")
            val response = HttpUtil.post(
                urlStr = endpoint,
                body = requestBody,
                headers = headers,
                timeoutMs = 120_000
            ) ?: return@withContext AiOcrServiceResult.Error("网络请求失败")
            parseResponse(response)
        } catch (e: Exception) {
            AiOcrServiceResult.Error(e.message ?: "未知错误")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxSize = 1024
        val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
        } else {
            bitmap
        }
        Log.d(TAG, "bitmapToBase64: 原始=${bitmap.width}x${bitmap.height}, 缩放后=${scaledBitmap.width}x${scaledBitmap.height}")
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildVisionRequestBody(model: String, base64Image: String): String {
        val request = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 1024)
            addProperty("stream", false)
            addProperty("temperature", 0)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to listOf(
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$base64Image")),
                    mapOf("type" to "text", "text" to "请仔细逐区域扫描这张图片，提取所有真实联系方式。注意：1)每个图标旁的文字通常是该平台账号；2)小字和角落文字不要忽略；3)二维码下方的文字是账号信息；4)忽略水印、广告和装饰性文字")
                ))
            )))
        }
        return gson.toJson(request)
    }

    private fun buildTextRequestBody(model: String, ocrText: String): String {
        val request = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 1024)
            addProperty("stream", false)
            addProperty("temperature", 0)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to "以下是通过 OCR 识别出的文字，请从中提取所有真实联系方式。注意识别中文缩写（微信=VX、抖音=dy、微博=wb、小红书=xhs等），手机号可能有横线或空格，忽略水印、广告和装饰性文字：\n\n$ocrText")
            )))
        }
        return gson.toJson(request)
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

            val cleanedContent = extractJsonBlock(content)

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

/**
 * 从 AI 返回的混合文本中提取 JSON 块。
 *
 * AI 经常在 JSON 前后加解释文字，如：
 *   "我来分析这段文字...\n\n{...}\n\n以上就是结果"
 * 也可能在 ```json ``` 代码块中。
 * 此函数找到第一个 '{' 到匹配的 '}'，提取完整 JSON。
 */
private fun extractJsonBlock(text: String): String {
    // 先尝试提取 ```json ... ``` 代码块
    val codeBlockRegex = Regex("""```json\s*\n?(.*?)\n?\s*```""", RegexOption.DOT_MATCHES_ALL)
    val codeBlockMatch = codeBlockRegex.find(text)
    if (codeBlockMatch != null) {
        return codeBlockMatch.groupValues[1].trim()
    }

    // 再尝试提取 ``` ... ``` 代码块
    val genericBlockRegex = Regex("""```\s*\n?(.*?)\n?\s*```""", RegexOption.DOT_MATCHES_ALL)
    val genericBlockMatch = genericBlockRegex.find(text)
    if (genericBlockMatch != null) {
        return genericBlockMatch.groupValues[1].trim()
    }

    // 最后：找到第一个 { 到最后一个匹配的 } 之间的内容
    val firstBrace = text.indexOf('{')
    if (firstBrace < 0) return text.trim()

    var depth = 0
    var lastBrace = -1
    for (i in firstBrace until text.length) {
        when (text[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    lastBrace = i
                    break
                }
            }
        }
    }
    return if (lastBrace > firstBrace) text.substring(firstBrace, lastBrace + 1).trim() else text.trim()
}