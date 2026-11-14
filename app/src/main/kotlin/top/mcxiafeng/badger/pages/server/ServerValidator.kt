package top.mcxiafeng.badger.pages.server

import androidx.compose.runtime.Immutable

@Immutable
data class ChoiceServerUiState(
    val resolving: Boolean = false,
    val history: List<String> = emptyList(),
    val input: String = "",
    val https: Boolean = DEFAULT_HTTPS,
    val urlReason: String? = null,
) {
    val canProceed: Boolean get() = input.isNotBlank() && urlReason == null
}

/**
 * 服务器地址校验与规范化工具（移植自 dev 分支 page/server，纯 Kotlin）。
 * 输入语法: host[:port][/path]
 * Scheme 由外部 HTTPS 开关控制；粘贴完整 URL 时自动剥离并采纳其 scheme。
 */
object ServerValidator {

    const val DEFAULT_PORT = 51026
    const val SRV_SERVICE_PREFIX = "_badger._tcp."

    private const val HTTP_PREFIX = "http://"
    private const val HTTPS_PREFIX = "https://"
    private val PORT_RANGE = 1..65535

    private val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val TRAILING_SLASH_REGEX = Regex("/+$")

    private data class ParsedHost(
        val host: String,      // 纯主机名（IPv6 已去括号）
        val port: Int?,        // null 表示未指定
        val path: String?,     // null 表示无路径
        val isIpv6Literal: Boolean,
        val rawHostPort: String // 原始的 host:port 段（用于重建 URL）
    )

    /**
     * 输入预处理：剥空白/控制字符与尾部斜杠；粘贴带 scheme 时剥掉并采纳。
     * @return Pair(净化后的 host[:port][/path], 是否 https)
     */
    fun normalizeInput(raw: String, currentHttps: Boolean): Pair<String, Boolean> {
        val trimmed = raw.filterNot { it.isISOControl() || it.isWhitespace() }
            .replace(TRAILING_SLASH_REGEX, "")

        return when {
            trimmed.startsWith(HTTPS_PREFIX, ignoreCase = true) ->
                trimmed.substring(HTTPS_PREFIX.length).replace(TRAILING_SLASH_REGEX, "") to true
            trimmed.startsWith(HTTP_PREFIX, ignoreCase = true) ->
                trimmed.substring(HTTP_PREFIX.length).replace(TRAILING_SLASH_REGEX, "") to false
            else -> trimmed to currentHttps
        }
    }

    /** 返回拦截原因；null = 结构合法。 */
    fun blockReason(input: String): String? {
        if (input.isBlank()) return "请输入地址"

        val parsed = parseHost(input) ?: return "地址格式无效"

        if (parsed.host.isBlank()) return "缺少主机名"
        if (parsed.port != null && parsed.port !in PORT_RANGE) return "端口超出范围 (1-65535)"

        // 检测裸 IPv6（有多个冒号但没有方括号）
        if (!parsed.isIpv6Literal && ':' in parsed.host) {
            return "IPv6 地址需用方括号包裹，如 [::1]"
        }

        return null
    }

    /** 合成 PublicApi 可用的 base URL（不带尾部斜杠）。 */
    fun composeUrl(input: String, https: Boolean): String {
        val prefix = if (https) HTTPS_PREFIX else HTTP_PREFIX
        return prefix + input.trimEnd('/')
    }

    /** 未写端口补 DEFAULT_PORT；SRV 兜底直连用。 */
    fun withDefaultPort(input: String): String {
        val parsed = parseHost(input) ?: return "$input:$DEFAULT_PORT"
        if (parsed.port != null) return input

        val hostPort = if (parsed.isIpv6Literal) {
            "[${parsed.host}]:$DEFAULT_PORT"
        } else {
            "${parsed.host}:$DEFAULT_PORT"
        }
        return if (parsed.path != null) "$hostPort/${parsed.path}" else hostPort
    }

    /**
     * 可做 SRV 服务发现时返回完整查询名，否则 null。
     * 跳过条件：显式端口 / 带路径 / IP 字面量（v4 或 v6）。
     */
    fun srvQueryName(input: String): String? {
        val parsed = parseHost(input) ?: return null
        if (parsed.path != null || parsed.port != null) return null
        if (parsed.isIpv6Literal || IPV4_REGEX.matches(parsed.host)) return null
        if (parsed.host.isBlank()) return null

        return SRV_SERVICE_PREFIX + parsed.host
    }

    /** SRV 记录端点 → hostport 段（IPv6 target 自动加方括号）。 */
    fun srvHostPort(host: String, port: Int): String =
        if (':' in host) "[$host]:$port" else "$host:$port"

    /**
     * 统一解析入口，避免各函数重复 substring/count 操作。
     * 返回 null 表示完全无法解析（如空字符串或纯斜杠）。
     */
    private fun parseHost(input: String): ParsedHost? {
        if (input.isBlank()) return null

        val slashIdx = input.indexOf('/')
        val hostPortRaw = if (slashIdx >= 0) input.substring(0, slashIdx) else input
        val path = if (slashIdx >= 0) input.substring(slashIdx + 1).trimStart('/') else null

        if (hostPortRaw.isEmpty()) return null

        val isIpv6Literal = hostPortRaw.startsWith("[")
        val host: String
        val port: Int?

        if (isIpv6Literal) {
            val closeBracket = hostPortRaw.indexOf(']')
            if (closeBracket < 0) return null
            host = hostPortRaw.substring(1, closeBracket)
            val afterBracket = hostPortRaw.substring(closeBracket + 1)
            port = when {
                afterBracket.isEmpty() -> null
                afterBracket.startsWith(":") -> afterBracket.substring(1).toIntOrNull()
                else -> return null
            }
        } else {
            val lastColon = hostPortRaw.lastIndexOf(':')
            if (lastColon < 0) {
                host = hostPortRaw
                port = null
            } else {
                host = hostPortRaw.substring(0, lastColon)
                port = hostPortRaw.substring(lastColon + 1).toIntOrNull()
                if (port == null && hostPortRaw.substring(lastColon + 1).isNotEmpty()) {
                    return ParsedHost(
                        host = hostPortRaw,
                        port = null,
                        path = path,
                        isIpv6Literal = false,
                        rawHostPort = hostPortRaw
                    )
                }
            }
        }

        return ParsedHost(
            host = host,
            port = port,
            path = path,
            isIpv6Literal = isIpv6Literal,
            rawHostPort = hostPortRaw
        )
    }
}
