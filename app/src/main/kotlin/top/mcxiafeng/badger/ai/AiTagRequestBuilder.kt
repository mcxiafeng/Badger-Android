package top.mcxiafeng.badger.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * AI 标签生成的请求/响应构建工具。
 *
 * 复用 [top.mcxiafeng.badger.ocr.AiOcrService] 的 OpenAI 兼容 chat 接口；
 * 仅构造请求体与解析响应（不重复网络层）。
 */
internal object AiTagRequestBuilder {

    private val gson = Gson()

    /**
     * LLM 返回的单个标签(name + confidence 0~1)。
     * 暴露给 [AiTagGenerator] 用于生成 [AiTagGenerator.TagCandidate]。
     */
    data class ParsedTag(val name: String, val confidence: Float)

    /**
     * 构造请求体 JSON。
     *
     * @param model 模型名
     * @param bio 用户输入的个人介绍文本
     * @param existingTagNames 已有 Tag 名字列表(传给 LLM 要求优先复用)
     */
    fun buildRequestBody(model: String, bio: String, existingTagNames: List<String>): String {
        val messages = JsonArray()

        // system prompt：要求 LLM 优先复用已有 Tag、严格 JSON 输出
        val systemMsg = JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", buildSystemPrompt(existingTagNames))
        }
        messages.add(systemMsg)

        // user message：bio + 已有 tag 列表(再次显式列出,避免 LLM 漏读)
        val userMsg = JsonObject().apply {
            addProperty("role", "user")
            val tagsText = if (existingTagNames.isEmpty()) {
                "(暂无已有标签)"
            } else {
                existingTagNames.joinToString("、")
            }
            addProperty(
                "content",
                "联系人自我介绍如下：\n\"\"\"\n$bio\n\"\"\"\n\n" +
                    "已有标签列表：$tagsText\n\n" +
                    "请根据上述内容为该联系人推荐标签,严格按 JSON 格式返回。"
            )
        }
        messages.add(userMsg)

        val body = JsonObject().apply {
            addProperty("model", model)
            add("messages", messages)
            // 温度低一点,要求更稳定的输出
            addProperty("temperature", 0.3)
        }
        return gson.toJson(body)
    }

    /**
     * 解析 LLM 返回的 content,提取 {"tags": [{"name": "...", "confidence": 0.9}, ...]}。
     *
     * 兼容 ```json``` 代码块与裸 JSON 输出(参考 AiOcrRequestBuilder.extractJsonBlock)。
     */
    fun parseContent(content: String): List<ParsedTag> {
        val cleaned = extractJsonBlock(content)
        if (cleaned.isBlank()) return emptyList()
        return try {
            val obj = gson.fromJson(cleaned, JsonObject::class.java)
            val tagsArray = obj.getAsJsonArray("tags") ?: return emptyList()
            tagsArray.mapNotNull { item ->
                val o = item.asJsonObject
                val name = o.get("name")?.asString?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val confidence = runCatching { o.get("confidence")?.asFloat ?: 0.5f }
                    .getOrDefault(0.5f)
                ParsedTag(name = name, confidence = confidence.coerceIn(0f, 1f))
            }
        } catch (e: Exception) {
            android.util.Log.w("AiTagRequestBuilder", "parseContent failed: ${e.message}")
            emptyList()
        }
    }

    private fun buildSystemPrompt(existingTagNames: List<String>): String {
        val reuseRule = if (existingTagNames.isEmpty()) {
            "5. 当前没有已有标签,你可以自由推荐 1~5 个新标签。"
        } else {
            "5. **优先复用已有标签**:能从已有标签列表中匹配的,必须选已有标签;只有当已有标签无法准确表达时才创建新标签。\n" +
                "   已有标签列表：" + existingTagNames.joinToString("、") + "\n" +
                "   如果你推荐的新标签与已有标签意思相近(如\"家里人\"和\"家人\"),必须合并为已有标签。"
        }
        return """
            你是一名联系人分类助手,负责根据联系人的自我介绍为其推荐 1~5 个简洁标签。

            规则:
            1. 标签名要简短(1~4 个汉字 / 1~2 个英文单词),如"家人"、"高中同学"、"工程师"、"程序员"、"广州"。
            2. 避免过度细分的标签(如"喜欢吃辣的程序员"应简化为"程序员")。
            3. 标签应来自自我介绍的明确信息,不要凭空捏造。
            4. 严格按以下 JSON 格式返回,不要包含任何 JSON 之外的内容(不要 markdown 代码块标记):
               {"tags": [{"name": "标签名", "confidence": 0.0~1.0}, ...]}
               confidence 表示你对这个标签的把握程度。
            $reuseRule
        """.trimIndent()
    }

    /**
     * 提取 JSON 块:剥离 ```json ... ``` 围栏,处理嵌套花括号。
     * 与 [top.mcxiafeng.badger.ocr.AiOcrRequestBuilder.extractJsonBlock] 同源。
     */
    private fun extractJsonBlock(text: String): String {
        val trimmed = text.trim()
        // ```json ... ``` 围栏
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```").find(trimmed)
        if (fenced != null) return fenced.groupValues[1].trim()
        // 找最外层 { ... }
        val firstBrace = trimmed.indexOf('{')
        if (firstBrace < 0) return ""
        var depth = 0
        for (i in firstBrace until trimmed.length) {
            when (trimmed[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return trimmed.substring(firstBrace, i + 1)
                }
            }
        }
        return trimmed.substring(firstBrace)
    }
}
