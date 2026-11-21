package top.mcxiafeng.badger.data.sync

import com.google.gson.JsonObject
import top.mcxiafeng.badger.ocr.ALL_FIELDS

/** A fresh device's unchanged built-in defaults are not edits to an existing cloud configuration. */
internal fun isDefaultSystemDefinition(value: JsonObject): Boolean {
    if (!value.flag("isSystem") || !value.flag("isEnabled")) return false
    val key = value.str("fieldKey") ?: return false
    val index = ALL_FIELDS.indexOfFirst { it.fieldKey == key }
    if (index < 0) return false
    val definition = ALL_FIELDS[index]
    return value.str("fieldName") == definition.displayName && value.str("icon") == key &&
        value.num("sortOrder") == (index + 1).toLong()
}

internal class TagNameConflictException : IllegalStateException(
    "云端已有另一个同名标签。请先调整本机标签名称再同步，两侧资料均已保留。")
