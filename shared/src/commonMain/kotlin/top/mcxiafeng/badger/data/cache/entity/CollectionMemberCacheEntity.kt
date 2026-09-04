package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * V2 名片夹成员关联表（对应表 `collection_member_cache`）。
 *
 * 替代 V1 `scan_results` 表作为联系人 ↔ 名片夹 多对多关联。
 * 仅保留关联关系（contactId ↔ collectionId），不再存储扫码元数据。
 *
 * 对应规约：docs/architecture-refactor-plan.md Phase 4 Task #20
 */
@Entity(
    tableName = "collection_member_cache",
    primaryKeys = ["contactId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = ContactCacheEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CardCollectionCacheEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["collectionId"])
    ]
)
data class CollectionMemberCacheEntity(
    val contactId: Long,
    val collectionId: Long,
    val addedAt: Long = top.mcxiafeng.badger.shared.util.nowMs(),
)
