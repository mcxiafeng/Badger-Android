package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * V2 自定义字段定义表（映射自 v5 老 `custom_fields` 表）。
 *
 * 字段一一对应，无 V2 新增列。
 *
 * 对应规约：Phase 3 Task #30 — expand 阶段，为删除 V1 `custom_fields` 表做准备。
 */
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
