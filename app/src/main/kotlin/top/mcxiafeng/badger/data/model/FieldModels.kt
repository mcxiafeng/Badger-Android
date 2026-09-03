package top.mcxiafeng.badger.data.model

import androidx.compose.runtime.Immutable

/**
 * 联系人字段定义（系统预置字段）。
 *
 * 本类型是历史数据/业务 DTO，不是 V1 HTTP API compatibility facade。
 * V2 cache 表已退役旧 Room 表；当前仍由 FieldRepository / ContactMapper 使用时保留。
 */
@Immutable
data class ContactField(
    val id: Long = 0,
    val fieldName: String,
    val fieldKey: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isEnabled: Boolean = true,
    val createTime: Long = System.currentTimeMillis()
)

/**
 * 自定义字段定义。
 *
 * 本类型是历史数据/业务 DTO，不是 V1 HTTP API compatibility facade。
 * V2 cache 表已退役旧 Room 表；当前仍有业务引用时保留。
 */
@Immutable
data class CustomField(
    val id: Long = 0,
    val fieldName: String,
    val fieldType: String,
    val options: String,
    val sortOrder: Int = 0,
    val isEnabled: Boolean = true,
    val createTime: Long = System.currentTimeMillis()
)

/**
 * 联系人字段值（关联数据）。
 *
 * 本类型是历史数据/业务 DTO，不是 V1 HTTP API compatibility facade。
 * V2 cache 已不再映射旧 `contact_field_values` Room 表；当前仍有业务引用时保留。
 */
@Immutable
data class ContactFieldValue(
    val id: Long = 0,
    val contactId: Long,
    val fieldId: Long? = null,
    val customFieldId: Long? = null,
    val value: String,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

enum class MergeChoice {
    /** 保留已有值，不做任何操作 */
    KEEP,
    /** 替换已有值为新值 */
    REPLACE,
    /** 追加新值（同一字段多个值） */
    APPEND
}

@Immutable
data class FieldMergeEntry(
    val fieldKey: String,
    val fieldName: String,
    val existingValue: String?,
    val newValue: String?,
    val selectedValue: MergeChoice = MergeChoice.APPEND
)
