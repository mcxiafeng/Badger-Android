package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * V2 联系人 ↔ 标签 多对多关联表（映射自 v5 老 `contact_tag` 表）。
 *
 * Q1 决策：新增独立表（而不是把 tags 折叠到 ContactCacheEntity.tagsJson），
 * 保持"按 tag 查所有联系人"查询语义可用。
 *
 * 字段语义与 V1 一致：`source` / `confidence` / `createTime` 均迁移。
 * 复合主键 `(contactId, tagId)` 保留。
 *
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2 + Q1 拍板
 */
@Entity(
    tableName = "contact_tag_cache",
    primaryKeys = ["contactId", "tagId"],
    indices = [
        Index(value = ["tagId"]),
        Index(value = ["contactId", "source"]),
    ]
)
data class ContactTagCacheEntity(
    val contactId: Long,
    val tagId: Long,
    val source: String = "manual",
    val confidence: Float = 1.0f,
    val createTime: Long = 0L,
)
