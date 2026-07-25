package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * V2 联系人字段值表（映射自 v5 老 `contact_field_values` 表）。
 *
 * Q1 决策：**保留** `fieldId` + `customFieldId` 两套（与 V1 同语义）。V2 协议稳定后再考虑合并。
 * V2 新增列：
 * - `displayOrder`：UI 排序权重（V1 用 sortOrder 散在 ContactField 表，本表也加一份便于快速排序）
 * - `serverVersion` / `isLocalOnly`：与 ContactCacheEntity 语义一致
 *
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2 + Q1 拍板
 */
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
    val serverVersion: Long = 0L,
    val isLocalOnly: Boolean = true,
)
