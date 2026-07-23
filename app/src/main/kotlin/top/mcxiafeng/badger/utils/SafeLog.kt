package top.mcxiafeng.badger.utils

import java.net.URI

/**
 * 日志脱敏工具 —— 把手机号 / 邮箱 / 头像 URL / token / 账号 / 密码 / API Key /
 * Authorization 头等敏感值替换为 `<redacted:TYPE>`，保留可读的字段名供调试。
 *
 * 设计原则：
 * - 永远不要打印 token / Authorization / password / API Key 原文 —— 即使
 *   是 debug build，logcat 也是进程级可读的，攻击者拿到 adb 就能 dump。
 * - 手机号中间四位打码；邮箱保留首字符和 @ 域名；头像 URL 保留 host 段。
 * - 不修改原字符串；纯函数，O(n)，可放心嵌进日志表达式。
 *
 * 使用：
 *   `Log.d(TAG, "login: user=${SafeLog.user(username)} phone=${SafeLog.phone(p)}")`
 */
object SafeLog {

    /** 账号 / 用户名：保留 3 字符以内长度供排错，长内容截断。 */
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
        val digits = num.filter { it.isDigit() }
        return when (digits.length) {
            11 -> "${digits.substring(0, 3)}****${digits.substring(7)}"
            in 7..14 -> "${digits.take(2)}****${digits.takeLast(2)}"
            else -> "<phone:len=${num.length}>"
        }
    }

    /** token / refreshToken / sessionId：保留前缀 4 字符与长度。 */
    fun token(t: String?): String =
        if (t.isNullOrBlank()) "<empty>"
        else "<token:prefix=${t.take(4)},len=${t.length}>"

    /** Authorization 头值：去掉 token 明文，只保留 scheme。 */
    fun authHeader(v: String?): String =
        if (v.isNullOrBlank()) "<empty>"
        else if (v.startsWith("Bearer ", ignoreCase = true)) "<auth:Bearer+token>"
        else if (v.startsWith("Basic ", ignoreCase = true)) "<auth:Basic+token>"
        else "<auth:scheme=${v.substringBefore(' ')}>"

    /** URL：保留 scheme + host，剥掉 path / query / fragment（避免 token 漏出）。 */
    fun url(u: String?): String {
        if (u.isNullOrBlank()) return "<empty>"
        return try {
            val parsed = URI(u)
            val scheme = parsed.scheme ?: "<no-scheme>"
            val host = parsed.host ?: "<no-host>"
            val port = if (parsed.port > 0) ":${parsed.port}" else ""
            "$scheme://$host$port/<path-redacted>"
        } catch (_: Exception) {
            "<url:invalid>"
        }
    }

    /** 头像 URL：保留 scheme + host；avatar 链接常带查询参数 token，统一打码。 */
    fun avatarUrl(u: String?): String = url(u)

    /** API Key / secret：纯哈希长度，不泄密。 */
    fun apiKey(k: String?): String =
        if (k.isNullOrBlank()) "<empty>"
        else "<apiKey:len=${k.length}>"

    /** 通用：未知类型按长度保留。 */
    fun unknown(v: String?): String =
        if (v.isNullOrBlank()) "<empty>"
        else "<len=${v.length}>"
}