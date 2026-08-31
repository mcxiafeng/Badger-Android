package top.mcxiafeng.badger.utils

import java.net.URI

/**
 * 日志脱敏工具 —— 把手机号 / 邮箱 / 头像 URL / token / 账号 / 密码 / API Key /
 * Authorization 头等敏感值替换为不可逆的摘要形式，保留有限信息供调试。
 */
object SafeLog {

    /** 账号 / 用户名：只保留长度，最多额外暴露首字符。 */
    fun user(name: String?): String =
        if (name.isNullOrBlank()) "<empty>"
        else if (name.length <= 3) "<user:len=${name.length}>"
        else "<user:len=${name.length},first=${name.first()}>"

    /** 邮箱：保留首字符与域名。 */
    fun email(addr: String?): String =
        if (addr.isNullOrBlank()) "<empty>"
        else {
            val at = addr.indexOf('@')
            if (at <= 0 || at == addr.length - 1) "<email:invalid>"
            else "<email:${addr.first()}***@${addr.substring(at + 1)}>"
        }

    /** 手机号：中国大陆 11 位手机号，中间 4 位打码。其他格式按通用规则。 */
    fun phone(num: String?): String {
        if (num.isNullOrBlank()) return "<empty>"
        val digits = num.filter(Char::isDigit)
        return when (digits.length) {
            11 -> "${digits.substring(0, 3)}****${digits.substring(7)}"
            in 7..14 -> "${digits.take(2)}****${digits.takeLast(2)}"
            else -> "<phone:len=${num.length}>"
        }
    }

    /** token / refreshToken / sessionId：保留前缀 4 字符与长度。 */
    fun token(value: String?): String =
        if (value.isNullOrBlank()) "<empty>"
        else "<token:prefix=${value.take(4)},len=${value.length}>"

    /** Authorization 头值：去掉 token 明文，只保留 scheme。 */
    fun authHeader(value: String?): String =
        if (value.isNullOrBlank()) "<empty>"
        else when {
            value.startsWith("Bearer ", ignoreCase = true) -> "<auth:Bearer+token>"
            value.startsWith("Basic ", ignoreCase = true) -> "<auth:Basic+token>"
            else -> "<auth:scheme=${value.substringBefore(' ')}>"
        }

    /** URL：保留 scheme + host + port，剥掉 path / query / fragment。 */
    fun url(value: String?): String {
        if (value.isNullOrBlank()) return "<empty>"
        return runCatching {
            val parsed = URI(value)
            val scheme = parsed.scheme ?: "<no-scheme>"
            val host = parsed.host ?: "<no-host>"
            val port = if (parsed.port > 0) ":${parsed.port}" else ""
            "$scheme://$host$port/<path-redacted>"
        }.getOrElse { "<url:invalid>" }
    }

    /** 头像 URL：与普通 URL 一样只保留 scheme + host。 */
    fun avatarUrl(value: String?): String = url(value)

    /** API Key / secret：只保留长度，不记录所谓“哈希”。 */
    fun apiKey(value: String?): String =
        if (value.isNullOrBlank()) "<empty>"
        else "<apiKey:len=${value.length}>"

    /** 通用：只保留长度。 */
    fun unknown(value: String?): String =
        if (value.isNullOrBlank()) "<empty>"
        else "<len=${value.length}>"
}
