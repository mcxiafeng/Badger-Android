package top.mcxiafeng.badger.utils

import top.mcxiafeng.badger.ocr.SHORT_LINK_DOMAINS

private const val TAG = "ShortLinkSupport"

fun isShortLink(url: String): Boolean {
    val host = extractHost(url)?.lowercase() ?: return false
    return SHORT_LINK_DOMAINS.containsKey(host)
}

private fun extractHost(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val rest = url.substring(schemeEnd + 3)
    val pathStart = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = if (pathStart < 0) rest else rest.substring(0, pathStart)
    
    val hostPort = authority.substringAfterLast('@')
    val host = hostPort.substringBefore(':')
    return host.takeIf { it.isNotBlank() }
}
