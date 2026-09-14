package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contact_field_values_cache",
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["contactId", "fieldId"]),
        Index(value = ["contactId", "customFieldId"]),
    ]
)
data class ContactFieldValueCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val fieldId: Long? = null,
    val customFieldId: Long? = null,
    val value: String,
    val displayOrder: Int = 0,
    val createTime: Long,
    val updateTime: Long,
    val isLocalOnly: Boolean = true,
)
