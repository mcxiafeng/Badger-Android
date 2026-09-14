package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

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
