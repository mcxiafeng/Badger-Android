package top.mcxiafeng.badger.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class ContactField(
    val id: Long = 0,
    val fieldName: String,
    val fieldKey: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isEnabled: Boolean = true,
    val createTime: Long = top.mcxiafeng.badger.shared.util.nowMs()
)

@Immutable
data class CustomField(
    val id: Long = 0,
    val fieldName: String,
    val fieldType: String,
    val options: String,
    val sortOrder: Int = 0,
    val isEnabled: Boolean = true,
    val createTime: Long = top.mcxiafeng.badger.shared.util.nowMs()
)

@Immutable
data class ContactFieldValue(
    val id: Long = 0,
    val contactId: Long,
    val fieldId: Long? = null,
    val customFieldId: Long? = null,
    val value: String,
    val createTime: Long = top.mcxiafeng.badger.shared.util.nowMs(),
    val updateTime: Long = top.mcxiafeng.badger.shared.util.nowMs()
)

enum class MergeChoice {
    
    KEEP,
    
    REPLACE,
    
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
