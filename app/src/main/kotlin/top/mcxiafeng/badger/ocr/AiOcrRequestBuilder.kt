package top.mcxiafeng.badger.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

/**
 * AI OCR 请求构建与工具方法
 *
 * 提取自 AiOcrService，降低主服务类复杂度。
 */
object AiOcrRequestBuilder {
    private const val TAG = "AiOcrRequestBuilder"
    private val gson = Gson()

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

    /** 根据模型名启发式判断是否支持图片输入 */
    fun isVisionModel(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return lower.contains("vision") || lower.contains("vl")
                || lower.contains("4o") || lower.contains("gpt-4")
                || lower.contains("qwen-vl") || lower.contains("qwen2-vl")
                || lower.contains("glm-4v") || lower.contains("clip")
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
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

    fun buildVisionRequestBody(model: String, base64Image: String): String {
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

    fun buildTextRequestBody(model: String, ocrText: String): String {
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

    fun buildTestRequestBody(model: String): String {
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
     * 从 AI 返回的混合文本中提取 JSON 块。
     *
     * AI 经常在 JSON 前后加解释文字，如：
     *   "我来分析这段文字...\n\n{...}\n\n以上就是结果"
     * 也可能在 ```json ``` 代码块中。
     * 此函数找到第一个 '{' 到匹配的 '}'，提取完整 JSON。
     */
    fun extractJsonBlock(text: String): String {
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
}
