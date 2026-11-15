package top.mcxiafeng.badger.domain

import android.util.Log
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.ocr.ADDABLE_PLATFORMS
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.LinkSource
import top.mcxiafeng.badger.ocr.SHORT_LINK_DOMAINS
import top.mcxiafeng.badger.ocr.buildPlatformLink

/** 只解析本机文本，不访问输入中的网址，也不推测无平台标识的账号。 */
class ParsePlatformInputUseCase @Inject constructor() {
    operator fun invoke(text: String): List<PlatformInputResult> {
        val results = text.lineSequence().mapIndexedNotNull { index, line ->
            line.trim().takeIf(String::isNotBlank)?.let { parseLine(index + 1, it) }
        }.toList()
        Log.d(TAG, "解析完成: lines=${results.size}, recognized=${results.count { it.entry != null }}")
        return results
    }

    fun manual(fieldKey: String, input: String, displayName: String = ""): PlatformInputResult {
        val value = input.trim()
        if (value.isBlank()) return PlatformInputResult(error = "请输入账号或链接")
        val uri = parseWebUrl(value)
        if (uri == null && (value.contains("://") || value.startsWith("http", true))) {
            return PlatformInputResult(error = "请输入有效的 HTTP 或 HTTPS 链接")
        }
        if (fieldKey == "website" && uri == null) {
            return PlatformInputResult(error = "网站需要完整的 HTTP 或 HTTPS 链接")
        }
        val def = FIELD_DEF_MAP[fieldKey]
        val entry = if (uri != null) {
            PlatformEntry(jumpLink = value, originalLink = value)
        } else {
            PlatformEntry(
                value = value,
                jumpLink = if (def?.linkSource == LinkSource.AUTO) buildPlatformLink(fieldKey, value) else ""
            )
        }
        Log.d(TAG, "手动条目校验通过: preset=${def != null}, hasLink=${uri != null}")
        return PlatformInputResult(fieldKey = fieldKey, entry = entry.copy(
            displayName = displayName.trim().ifBlank { null }
        ))
    }

    private fun parseLine(lineNumber: Int, line: String): PlatformInputResult {
        val labelled = LABEL_PATTERN.matchEntire(line)
        val key = labelled?.groupValues?.get(1)?.trim()?.let { label ->
            ADDABLE_PLATFORMS.firstOrNull { def ->
                def.fieldKey.equals(label, true) || def.displayName.equals(label, true) ||
                    def.aliases.any { it.equals(label, true) }
            }?.fieldKey
        }
        if (key != null) {
            return manual(key, labelled.groupValues[2]).copy(lineNumber = lineNumber)
        }
        val urls = URL_PATTERN.findAll(line).toList()
        if (urls.size != 1) {
            return PlatformInputResult(lineNumber = lineNumber, error =
                if (urls.size > 1) "一行请只输入一个链接" else "未识别平台，请使用“平台：账号”或手动添加")
        }
        val url = urls.single().value.trimEnd(*TRAILING_PUNCTUATION)
        val uri = parseWebUrl(url)
            ?: return PlatformInputResult(lineNumber = lineNumber, error = "链接格式不正确")
        val detectedKey = detectKey(uri)
        return PlatformInputResult(
            lineNumber = lineNumber,
            fieldKey = detectedKey,
            entry = PlatformEntry(jumpLink = url, originalLink = url)
        )
    }

    private fun parseWebUrl(value: String): URI? = try {
        URI(value).takeIf {
            (it.scheme.equals("https", true) || it.scheme.equals("http", true)) &&
                !it.host.isNullOrBlank() && it.rawUserInfo == null
        }
    } catch (error: URISyntaxException) {
        // 仅记录错误类别；原文可能包含账号、令牌或私密分享参数。
        Log.d(TAG, "链接格式校验未通过")
        null
    }

    private fun detectKey(uri: URI): String {
        val host = uri.host.lowercase()
        SHORT_LINK_DOMAINS[host]?.let { return it }
        // 按域名边界匹配，避免把 github.com.example.org 当作 GitHub。
        return HOST_KEYS.entries.filter { (domain, _) ->
            host == domain || host.endsWith(".$domain")
        }.maxByOrNull { it.key.length }?.value ?: "website"
    }

    companion object {
        private const val TAG = "ParsePlatformInputUseCaseTester"
        private val LABEL_PATTERN = Regex("^([^:：\\s]+)\\s*[:：]\\s*(.*)$")
        private val URL_PATTERN = Regex("https?://[^\\s<>\"，。；]+", RegexOption.IGNORE_CASE)
        private val TRAILING_PUNCTUATION = charArrayOf(')', '）', ']', '】', '}', '>', ',', '.', ';', '!', '！')
        private val HOST_KEYS = mapOf(
            "bilibili.com" to "bilibili", "qq.com" to "qq", "gljlw.com" to "qq",
            "wechat.com" to "wechat", "weixin.qq.com" to "wechat",
            "douyin.com" to "douyin", "iesdouyin.com" to "douyin",
            "weibo.com" to "weibo", "github.com" to "github", "t.me" to "telegram",
            "xiaohongshu.com" to "xiaohongshu", "facebook.com" to "facebook",
            "fb.com" to "facebook", "x.com" to "x", "twitter.com" to "x", "t.co" to "x"
        )
    }
}

data class PlatformInputResult(
    val lineNumber: Int = 0,
    val fieldKey: String? = null,
    val entry: PlatformEntry? = null,
    val error: String? = null
)
