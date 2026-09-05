package top.mcxiafeng.badger.pages.person.contact.detail

import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact

/**
 * 构建联系人分享文本。
 *
 * 用于"分享联系方式"功能，将联系人信息格式化为可读文本。
 */
internal fun buildContactShareText(contact: Contact?, fields: List<PersonFieldDisplay>): String {
    if (contact == null) return ""
    val sb = StringBuilder()
    sb.appendLine(contact.name)
    if (!contact.note.isNullOrBlank()) {
        sb.appendLine("备注：${contact.note}")
    }
    fields.forEach { field ->
        sb.appendLine("${field.fieldName}：${field.value}")
    }
    return sb.toString().trim()
}
