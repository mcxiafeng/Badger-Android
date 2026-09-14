package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contact_fields_cache",
    indices = [Index(value = ["fieldKey"], unique = true)]
)
data class ContactFieldCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fieldName: String,
    val fieldKey: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isEnabled: Boolean = true,
    val createTime: Long,
)
