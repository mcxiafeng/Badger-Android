package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "custom_fields_cache",
    indices = [Index(value = ["sortOrder"])]
)
data class CustomFieldCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fieldName: String,
    val fieldType: String,
    val options: String,
    val sortOrder: Int = 0,
    val isEnabled: Boolean = true,
    val createTime: Long,
)
