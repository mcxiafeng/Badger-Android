package top.mcxiafeng.badger.network

import android.util.Log
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.SHORT_LINK_DOMAINS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.utils.HttpUtil

/**
 * 活链接分层解析引擎
 *
 * 核心原则：先解析，后存储，永远不丢失原始输入。解析成功是锦上添花，失败不阻塞保存。
 *
 * 解析流程：
 * 1. 输入识别 — 区分 HTTPS URL / 账号ID / 未知
 * 2. 链接解析 — 跟踪重定向获取最终URL，从中提取标识符
 * 3. 存储策略 — 三级存储（jumpLink / originalLink / value）
 * 4. 失败兜底 — 解析失败时 jumpLink = rawLink，不阻塞保存
 */
object LinkResolver {

    private const val TAG = "LinkResolver"
    private const val RESOLVE_TIMEOUT_MS = 5000

    data class LinkResolveResult(
        val jumpLink: String,
        val originalLink: String,
        val value: String?,
        val displayName: String?,
        val avatarUrl: String?,
        val errorMessage: String?,
    )

    /**
     * 解析链接：跟踪重定向 + 从最终URL提取标识符 + 构造标准化URL
     *
     * @param fieldKey 平台标识
     * @param rawInput 用户输入的原始内容（可能是链接或账号）
     * @return 解析结果
     */
    suspend fun resolve(fieldKey: String, rawInput: String): LinkResolveResult {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return LinkResolveResult("", "", null, null, null, "输入不能为空")
        }

        val isUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")

        if (!isUrl) {
            // 非 URL 输入 → 走 buildPlatformLink 生成逻辑
            return resolveAccountId(fieldKey, trimmed)
        }

        // URL 输入 → 链接解析流程
        return resolveLink(fieldKey, trimmed)
    }

    /**
     * 处理账号/ID 输入（非 URL）
     */
    private fun resolveAccountId(fieldKey: String, accountId: String): LinkResolveResult {
        val def = FIELD_DEF_MAP[fieldKey]

        if (def == null) {
            // 自定义平台，存为 value
            return LinkResolveResult(
                jumpLink = "",
                originalLink = "",
                value = accountId,
                displayName = null,
                avatarUrl = null,
                errorMessage = null
            )
        }

        val generatedLink = buildPlatformLink(fieldKey, accountId)

        return LinkResolveResult(
            jumpLink = generatedLink,
            originalLink = "",
            value = accountId,
            displayName = null,
            avatarUrl = null,
            errorMessage = null
        )
    }

    /**
     * 处理链接输入 — 核心解析逻辑
     */
    private suspend fun resolveLink(fieldKey: String, rawLink: String): LinkResolveResult {
        val host = try { java.net.URI(rawLink).host?.lowercase() } catch (_: Exception) { null }

        // 判断是否是短链域名
        val isShortLink = host != null && SHORT_LINK_DOMAINS.containsKey(host)

        if (isShortLink) {
            return resolveShortLink(fieldKey, rawLink)
        }

        // 判断是否是平台已知长链接域名
        val detectedKey = PlatformIdExtractor.detectFieldKeyFromUrl(rawLink)
        if (detectedKey != null) {
            return resolveKnownPlatformLink(detectedKey, rawLink, rawLink)
        }

        // 未知域名 → 尝试跟踪重定向
        return resolveUnknownLink(fieldKey, rawLink)
    }

    /**
     * 解析短链：跟踪重定向获取最终URL
     */
    private suspend fun resolveShortLink(fieldKey: String, rawLink: String): LinkResolveResult {
        Log.d(TAG, "解析短链: $rawLink")
        try {
            val finalUrl = HttpUtil.getFinalRedirectUrl(rawLink, RESOLVE_TIMEOUT_MS)
            if (finalUrl != null && finalUrl != rawLink) {
                Log.d(TAG, "短链重定向成功: $finalUrl")
                // 从最终URL检测平台
                val detectedKey = PlatformIdExtractor.detectFieldKeyFromUrl(finalUrl) ?: fieldKey
                return resolveKnownPlatformLink(detectedKey, finalUrl, rawLink)
            }
            // 重定向没有跳转（可能需要 GET 请求解析 JS）
            val htmlResult = resolveByGet(rawLink)
            if (htmlResult != null) {
                Log.d(TAG, "GET 请求解析成功: $htmlResult")
                val detectedKey = PlatformIdExtractor.detectFieldKeyFromUrl(htmlResult) ?: fieldKey
                return resolveKnownPlatformLink(detectedKey, htmlResult, rawLink)
            }
        } catch (e: Exception) {
            Log.w(TAG, "短链解析异常: ${e.message}")
        }

        // 解析失败，尝试从原始 URL 直接提取平台信息（如 tool.gljlw.com 的 ?qq= 参数）
        val directExtract = PlatformIdExtractor.extractByKey(fieldKey, rawLink)
        if (directExtract.value != null || directExtract.displayName != null) {
            Log.d(TAG, "短链重定向失败，直接从 URL 提取成功: key=$fieldKey, value=${directExtract.value}")
            val standardUrl = if (directExtract.value != null) {
                buildPlatformLink(fieldKey, directExtract.value).ifBlank { rawLink }
            } else {
                rawLink
            }
            return LinkResolveResult(
                jumpLink = standardUrl.ifBlank { rawLink },
                originalLink = rawLink,
                value = directExtract.value,
                displayName = directExtract.displayName,
                avatarUrl = directExtract.avatarUrl,
                errorMessage = null
            )
        }

        return LinkResolveResult(
            jumpLink = rawLink,
            originalLink = rawLink,
            value = null,
            displayName = null,
            avatarUrl = null,
            errorMessage = "链接解析超时，已保存原始链接"
        )
    }

    /**
     * 解析已知平台长链接：直接提取标识符
     */
    private suspend fun resolveKnownPlatformLink(
        detectedKey: String,
        finalUrl: String,
        originalInput: String
    ): LinkResolveResult {
        // 从URL中提取标识符
        val extractResult = PlatformIdExtractor.extractByKey(detectedKey, finalUrl)
        val extractedValue = extractResult.value
        val extractedName = extractResult.displayName
        val extractedAvatar = extractResult.avatarUrl

        // 构造标准化 URL
        val standardUrl = if (extractedValue != null) {
            buildPlatformLink(detectedKey, extractedValue).ifBlank { finalUrl }
        } else {
            finalUrl
        }

        val originalLink = if (originalInput != standardUrl) originalInput else ""

        return LinkResolveResult(
            jumpLink = standardUrl.ifBlank { finalUrl },
            originalLink = originalLink,
            value = extractedValue,
            displayName = extractedName,
            avatarUrl = extractedAvatar,
            errorMessage = if (extractResult.errorMessage != null && extractedValue == null) extractResult.errorMessage else null
        )
    }

    /**
     * 解析未知链接：尝试跟踪重定向后发现平台
     */
    private suspend fun resolveUnknownLink(fieldKey: String, rawLink: String): LinkResolveResult {
        Log.d(TAG, "解析未知链接: $rawLink")
        try {
            val finalUrl = HttpUtil.getFinalRedirectUrl(rawLink, RESOLVE_TIMEOUT_MS)
            if (finalUrl != null && finalUrl != rawLink) {
                val detectedKey = PlatformIdExtractor.detectFieldKeyFromUrl(finalUrl)
                if (detectedKey != null) {
                    Log.d(TAG, "未知链接重定向后发现平台: $detectedKey")
                    return resolveKnownPlatformLink(detectedKey, finalUrl, rawLink)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "未知链接解析异常: ${e.message}")
        }

        // 不是已知平台 → 按自定义平台处理
        return LinkResolveResult(
            jumpLink = rawLink,
            originalLink = rawLink,
            value = null,
            displayName = null,
            avatarUrl = null,
            errorMessage = null
        )
    }

    /**
     * 通过 GET 请求获取最终 URL（用于 JS 重定向的短链）
     *
     * 尝试从 HTML 中解析 meta refresh 或 JS 跳转目标。
     */
    private suspend fun resolveByGet(url: String): String? {
        try {
            val html = HttpUtil.get(url, RESOLVE_TIMEOUT_MS, headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
            )) ?: return null

            // 解析 meta refresh
            val metaMatch = Regex("""<meta[^>]*http-equiv=["']?refresh["']?[^>]*content=["']?\d+;\s*url=([^"'\s>]+)""", RegexOption.IGNORE_CASE)
                .find(html)
            if (metaMatch != null) return metaMatch.groupValues[1]

            // 解析 JS location 跳转
            val jsMatch = Regex("""(?:window\.)?location(?:\.href)?\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(html)
            if (jsMatch != null) return jsMatch.groupValues[1]

            // 解析 douyin 特有的路由配置
            val routeMatch = Regex(""""url"\s*:\s*"((?:https?:)?//[^"]+)"""")
                .find(html)
            if (routeMatch != null) {
                val routed = routeMatch.groupValues[1]
                return if (routed.startsWith("//")) "https:$routed" else routed
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET 请求解析异常: ${e.message}")
        }
        return null
    }

    /**
     * 将解析结果转换为 PlatformEntry
     */
    fun toPlatformEntry(result: LinkResolveResult, displayNameOverride: String? = null): PlatformEntry {
        return PlatformEntry(
            displayName = displayNameOverride ?: result.displayName,
            jumpLink = result.jumpLink,
            originalLink = result.originalLink.ifBlank { null },
            value = result.value,
            avatarUrl = result.avatarUrl
        )
    }
}
