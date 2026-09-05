package top.mcxiafeng.badger.utils

import top.mcxiafeng.badger.ocr.SHORT_LINK_DOMAINS

private const val TAG = "ShortLinkSupport"

/**
 * [KMP K13b] 短链域名判定（common 化，原 ShortLinkUtils.kt 的 java.net.URI 解析
 * 改为手写宽松 scheme://host 提取——iOS 无 java.net，与 SafeLog.url 同款降级）。
 */
fun isShortLink(url: String): Boolean {
    val host = extractHost(url)?.lowercase() ?: return false
    return SHORT_LINK_DOMAINS.containsKey(host)
}

/** 宽松提取 scheme://host[:port] 的 host 段（非法输入返回 null）。 */
private fun extractHost(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val rest = url.substring(schemeEnd + 3)
    val pathStart = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = if (pathStart < 0) rest else rest.substring(0, pathStart)
    // 去掉 user@ 前缀与 :port 后缀
    val hostPort = authority.substringAfterLast('@')
    val host = hostPort.substringBefore(':')
    return host.takeIf { it.isNotBlank() }
}
