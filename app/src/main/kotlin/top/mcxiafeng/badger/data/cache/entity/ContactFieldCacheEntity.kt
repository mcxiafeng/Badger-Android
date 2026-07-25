package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * V2 系统字段定义表（映射自 v5 老 `contact_fields` 表）。
 *
 * 字段一一对应，无 V2 新增列。`fieldKey` 是程序内部引用键，做唯一索引避免重复定义。
 *
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2
 */
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
