package top.mcxiafeng.badger.utils

/**
 * 日志脱敏工具。
 * 敏感凭证只保留有限元数据，绝不记录可用于重放的凭证片段。
 *
 * [KMP K06] 脱敏逻辑纯 Kotlin，已迁 shared commonMain；
 * 日志输出底座走 [BadgerLog] expect/actual（Android=android.util.Log / iOS=NSLog）。
 */
object SafeLog {

    fun user(name: String?): String =
        if (name.isNullOrBlank()) "<empty>"
        else if (name.length <= 3) "<user:len=${name.length}>"
        else "<user:len=${name.length},first=${name.first()}>"

    fun email(addr: String?): String =
        if (addr.isNullOrBlank()) "<empty>"
        else {
            val at = addr.indexOf('@')
            if (at <= 0 || at == addr.length - 1) "<email:invalid>"
            else "<email:${addr.first()}***@${addr.substring(at + 1)}>"
        }

    fun phone(num: String?): String {
        if (num.isNullOrBlank()) return "<empty>"
        val digits = num.filter(Char::isDigit)
        return when (digits.length) {
            11 -> "${digits.substring(0, 3)}****${digits.substring(7)}"
            in 7..14 -> "${digits.take(2)}****${digits.takeLast(2)}"
            else -> "<phone:len=${num.length}>"
        }
    }

    /** token / refreshToken / sessionId：只保留长度，不暴露 token 任意片段。 */
    fun token(value: String?): String =
        if (value.isNullOrBlank()) "<empty>"
        else "<token:len=${value.length}>"

    fun authHeader(value: String?): String =
        if (value.isNullOrBlank()) "<empty>"
        else when {
            value.startsWith("Bearer ", ignoreCase = true) -> "<auth:Bearer+token>"
            value.startsWith("Basic ", ignoreCase = true) -> "<auth:Basic+token>"
            else -> "<auth:scheme=${value.substringBefore(' ')}>"
        }

    fun url(value: String?): String {
        if (value.isNullOrBlank()) return "<empty>"
        return runCatching {
            // 手写宽松解析：java.net.URI 在 iOS target 不可用
            val scheme = value.substringBefore("://", missingDelimiterValue = "").takeIf { it.isNotBlank() }
                ?: return@runCatching "<url:no-scheme>"
            val rest = value.substringAfter("://")
            val host = rest.substringBefore('/').substringBefore(':').takeIf { it.isNotBlank() } ?: "<no-host>"
            val port = rest.substringBefore('/').substringAfter(':', "").takeIf { it.isNotBlank() }?.let { ":$it" } ?: ""
            "$scheme://$host$port/<path-redacted>"
        }.getOrElse { "<url:invalid>" }
    }

    fun avatarUrl(value: String?): String = url(value)

    fun apiKey(value: String?): String =
        if (value.isNullOrBlank()) "<empty>"
        else "<apiKey:len=${value.length}>"

    fun unknown(value: String?): String =
        if (value.isNullOrBlank()) "<empty>"
        else "<len=${value.length}>"
}
