package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index

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
