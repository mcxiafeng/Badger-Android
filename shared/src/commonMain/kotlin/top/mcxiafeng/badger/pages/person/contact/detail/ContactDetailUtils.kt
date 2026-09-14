package top.mcxiafeng.badger.pages.person.contact.detail

import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact

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
