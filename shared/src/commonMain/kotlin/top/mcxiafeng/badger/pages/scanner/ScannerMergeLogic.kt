package top.mcxiafeng.badger.pages.scanner

import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.network.NetworkResolveResult
import top.mcxiafeng.badger.ocr.ALIAS_TO_KEY_MAP
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.PLATFORM_FIELDS

/**
 * 从 resolveStates 和 ocrExtractedInfo 合并名字的纯函数逻辑。
 *
 * 优先级：网络昵称（按 infoPriority）→ OCR 名字 → 本地解析名字 → "未知联系人"
 */
fun computeMergedName(
    resolveStates: Map<String, QrResolveState>,
    ocrResolveStates: Map<String, QrResolveState>,
    ocrExtractedInfo: ExtractedContactInfo?,
    infoPriority: List<ContactType>
): String {
    val allResults = resolveStates.values.mapNotNull { it.networkResult } +
            ocrResolveStates.values.mapNotNull { it.networkResult }
    return infoPriority.firstNotNullOfOrNull { type ->
        allResults.firstOrNull { it.type == type && !it.nickname.isNullOrBlank() }?.nickname
    } ?: allResults.firstOrNull { !it.nickname.isNullOrBlank() }?.nickname
    ?: ocrExtractedInfo?.name?.ifBlank { null }
    ?: resolveStates.values.mapNotNull { it.extractedInfo?.name?.ifBlank { null } }.firstOrNull()
    ?: "未知联系人"
}

/**
 * 从 resolveStates 和 ocrExtractedInfo 合并字段列表的纯函数逻辑。
 *
 * 收集顺序：网络解析结果 → QR 本地解析结果 → OCR 结果
 * 然后按优先级排序 + 同 key:value 去重
 */
fun computeMergedFields(
    resolveStates: Map<String, QrResolveState>,
    ocrResolveStates: Map<String, QrResolveState>,
    ocrExtractedInfo: ExtractedContactInfo?,
    fieldOrder: Map<String, Int>
): List<SelectableField> {
    val fields = mutableListOf<SelectableField>()

    val allNetworkResults = resolveStates.values.mapNotNull { it.networkResult } +
            ocrResolveStates.values.mapNotNull { it.networkResult }
    for (result in allNetworkResults) {
        if (result.type == ContactType.QQGroup) {
            result.contactMap["qqGroup"]?.let { fields.add(SelectableField("qqGroup", "QQ群", it)) }
            continue
        }
        if (result.type == ContactType.TelegramGroup) {
            result.contactMap["telegramGroup"]?.let { fields.add(SelectableField("telegramGroup", "Telegram群", it)) }
            continue
        }
        if (result.type == ContactType.None) continue
        val def = PLATFORM_FIELDS.find { it.contactType == result.type } ?: continue
        val value = result.contactMap[def.fieldKey] ?: continue
        fields.add(SelectableField(def.fieldKey, def.displayName, value))
    }

    // 从二维码本地解析结果提取
    for (state in resolveStates.values) {
        val info = state.extractedInfo ?: continue
        info.phone?.let { phoneStr ->
            phoneStr.split(",", "，", ";", " ").filter { it.isNotBlank() }.forEachIndexed { idx, phone ->
                val key = if (idx == 0) "phone" else "phone_$idx"
                fields.add(SelectableField(key, "电话", phone.trim()))
            }
        }
        info.email?.let { fields.add(SelectableField("email", "邮箱", it)) }
        for ((key, value) in info.platforms) {
            val def = FIELD_DEF_MAP[key]
            fields.add(SelectableField(key, def?.displayName ?: key, value))
        }
    }

    // 从 OCR 结果提取
    ocrExtractedInfo?.let { info ->
        info.phone?.let { phoneStr ->
            phoneStr.split(",", "，", ";", " ").filter { it.isNotBlank() }.forEachIndexed { idx, phone ->
                val key = if (idx == 0) "phone" else "phone_$idx"
                fields.add(SelectableField(key, "电话", phone.trim()))
            }
        }
        info.email?.let { fields.add(SelectableField("email", "邮箱", it)) }
        for ((key, value) in info.platforms) {
            val def = FIELD_DEF_MAP[key]
            fields.add(SelectableField(key, def?.displayName ?: key, value))
        }
        info.otherInfo.forEach { otherItem ->
            val colonIndex = otherItem.indexOfAny(charArrayOf(':', '：'))
            if (colonIndex > 0) {
                val key = otherItem.substring(0, colonIndex).lowercase().trim()
                val value = otherItem.substring(colonIndex + 1).trim()
                val fieldKey = ALIAS_TO_KEY_MAP[key]
                if (fieldKey != null && value.isNotBlank()) {
                    val def = FIELD_DEF_MAP[fieldKey]
                    fields.add(SelectableField(fieldKey, def?.displayName ?: fieldKey, value))
                }
            }
        }
    }

    // 按优先级排序 → 去重
    val sorted = fields.sortedBy { fieldOrder[it.key] ?: 99 }
    return sorted
        .fold(mutableListOf<SelectableField>() to (mutableSetOf<String>() to mutableSetOf<String>())) { (result, pair), field ->
            val (seen, seenValues) = pair
            val dedupeKey = "${field.key}:${field.value}"
            val valueKey = field.value
            if (dedupeKey !in seen && valueKey !in seenValues) {
                seen.add(dedupeKey)
                seenValues.add(valueKey)
                if (result.any { it.key == field.key }) {
                    val idx = result.count { it.key == field.key || it.key.startsWith("${field.key}_") }
                    result.add(field.copy(key = "${field.key}_$idx"))
                } else {
                    result.add(field)
                }
            }
            result to (seen to seenValues)
        }.first
}
