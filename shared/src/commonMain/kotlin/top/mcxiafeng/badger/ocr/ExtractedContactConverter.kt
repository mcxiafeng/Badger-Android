package top.mcxiafeng.badger.ocr

import top.mcxiafeng.badger.network.ExtractedContact

fun ExtractedContact.toExtractedContactInfo(rawText: String?): ExtractedContactInfo {
    val platforms = mutableMapOf<String, String>()
    qq?.takeIf { it.isNotBlank() }?.let { platforms["qq"] = it }
    wechat?.takeIf { it.isNotBlank() }?.let { platforms["wechat"] = it }
    bilibili?.takeIf { it.isNotBlank() }?.let { platforms["bilibili"] = it }
    weibo?.takeIf { it.isNotBlank() }?.let { platforms["weibo"] = it }
    douyin?.takeIf { it.isNotBlank() }?.let { platforms["douyin"] = it }
    github?.takeIf { it.isNotBlank() }?.let { platforms["github"] = it }
    telegram?.takeIf { it.isNotBlank() }?.let { platforms["telegram"] = it }
    xiaohongshu?.takeIf { it.isNotBlank() }?.let { platforms["xiaohongshu"] = it }
    facebook?.takeIf { it.isNotBlank() }?.let { platforms["facebook"] = it }
    x?.takeIf { it.isNotBlank() }?.let { platforms["x"] = it }
    website?.takeIf { it.isNotBlank() }?.let { platforms["website"] = it }
    return ExtractedContactInfo(
        name = name,
        phone = phone,
        email = email,
        avatarUrl = avatarUrl,
        rawText = rawText ?: "",
        otherInfo = other,
        platforms = platforms,
    )
}
