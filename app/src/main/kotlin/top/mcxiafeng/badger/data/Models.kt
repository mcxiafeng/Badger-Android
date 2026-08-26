package top.mcxiafeng.badger.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import com.google.gson.annotations.SerializedName
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

// ============================================================
// V1 保留 entity(系统字段定义 + 自定义字段 + 字段值 + 扫码历史 + 平台兼容垫)
// ============================================================

/**
 * 联系人字段定义(系统预置字段)。
 *
 * V2 协议未涉及系统字段,保留 V1 表 + V1 entity,UI 消费形态不变。
 */
@Entity(
    tableName = "contact_fields",
    indices = [Index(value = ["fieldKey"], unique = true)]
)
@Immutable
data class ContactField(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fieldName: String,
    val fieldKey: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isEnabled: Boolean = true,
    val createTime: Long = System.currentTimeMillis()
)

/**
 * 自定义字段定义。
 *
 * V2 协议未涉及,保留 V1 表 + V1 entity。
 */
@Entity(
    tableName = "custom_fields",
    indices = [Index(value = ["sortOrder"])]
)
@Immutable
data class CustomField(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fieldName: String,
    val fieldType: String,
    val options: String,
    val sortOrder: Int = 0,
    val isEnabled: Boolean = true,
    val createTime: Long = System.currentTimeMillis()
)

/**
 * 联系人字段值(关联表)。
 *
 * V2 协议未涉及,保留 V1 表 + V1 entity;由 FieldRepository 消费。
 */
@Entity(
    tableName = "contact_field_values",
    foreignKeys = [
        ForeignKey(
            entity = top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ContactField::class,
            parentColumns = ["id"],
            childColumns = ["fieldId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomField::class,
            parentColumns = ["id"],
            childColumns = ["customFieldId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["contactId", "fieldId"]),
        Index(value = ["contactId", "customFieldId"]),
        Index(value = ["contactId"])
    ]
)
@Immutable
data class ContactFieldValue(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val fieldId: Long? = null,
    val customFieldId: Long? = null,
    val value: String,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

/**
 * 扫描结果记录。
 *
 * V2 协议保留作"扫码历史",由 CollectionRepository 消费。
 */
@Entity(
    tableName = "scan_results",
    foreignKeys = [
        ForeignKey(
            entity = top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["contactId", "collectionId"]),
        Index(value = ["collectionId"]),
        Index(value = ["contactId"])
    ]
)
@Immutable
data class ScanResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val collectionId: Long,
    val scannedTime: Long = System.currentTimeMillis(),
    val sourceType: String,
    val rawData: String? = null,
    val ocrText: String? = null,
    val qrCodeContent: String? = null,
    val confidence: Float = 0f
)

/**
 * 联系人平台条目 V1 表(平台兼容垫,V2 cache 表为主)。
 *
 * A3 决策:V1 `contact_platforms` 表保留,作为兼容垫;V2 `contact_platforms_cache` 为主写路径。
 * V1 `ContactPlatform` entity 保留,供 V1 `ContactPlatformDao` + 跨表查询使用。
 */
@Entity(
    tableName = "contact_platforms",
    foreignKeys = [ForeignKey(
        entity = top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity::class,
        parentColumns = ["id"],
        childColumns = ["contactId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["platformKey"]),
        Index(value = ["contactId", "platformKey"], unique = true)
    ]
)
@Immutable
data class ContactPlatform(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val platformKey: String,
    val value: String? = null,
    val displayName: String? = null,
    val jumpLink: String = "",
    val originalLink: String? = null,
    val avatarUrl: String? = null
)

// ============================================================
// 共享平台 JSON shape(供 V1 / V2 cache 互转)
// ============================================================

/**
 * 社交平台条目(共用的 JSON shape)。
 *
 * 同时作为 V1 `Contact.platforms: Map<String, PlatformEntry>` 和 V2 `ContactCacheEntity.platformsJson`
 * 反序列化结果。Gson @SerializedName 字段名固定,用于两个 schema 共享 JSON wire format。
 */
@Immutable
data class PlatformEntry(
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("jumpLink") val jumpLink: String = "",
    @SerializedName("originalLink") val originalLink: String? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

// ============================================================
// 业务包装类型(Repository / UI 间共用,与 cache entity 解耦)
// ============================================================

/**
 * 联系人字母桶统计。
 *
 * 由 ContactCacheDao.getLetterIndex 返回,UI 侧栏渲染。
 */
@Immutable
data class LetterCount(val letter: String, val count: Int)

/**
 * 联系人及其所有字段值的组合数据类。
 *
 * UI 侧一次性展示联系人完整信息时使用,字段对齐原 V1 ContactWithFields,内嵌 ContactCacheEntity。
 */
@Immutable
data class ContactWithFields(
    val contact: top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity,
    val fieldValues: List<ContactFieldDisplay>
)

/**
 * 联系人字段值的展示数据类。
 *
 * UI 层直接渲染;字段对齐原 V1 ContactFieldDisplay。
 */
@Immutable
data class ContactFieldDisplay(
    val valueId: Long,
    val fieldId: Long?,
    val customFieldId: Long?,
    val fieldName: String,
    val fieldKey: String?,
    val icon: String?,
    val fieldType: String?,
    val value: String,
    val sortOrder: Int
)

/**
 * 名片夹及联系人数量。
 *
 * [A3] 用扁平投影(避免 @Embedded 与 cache JOIN 冲突)。
 * KSP 必须在 @Database 同 module 看到该类,所以它在 Models.kt 顶层定义。
 */
@Immutable
data class CardCollectionWithCount(
    @androidx.room.ColumnInfo(name = "id") val id: Long,
    @androidx.room.ColumnInfo(name = "name") val name: String,
    @androidx.room.ColumnInfo(name = "description") val description: String?,
    @androidx.room.ColumnInfo(name = "backgroundImagePath") val backgroundImagePath: String?,
    @androidx.room.ColumnInfo(name = "dominantColor") val dominantColor: Long?,
    @androidx.room.ColumnInfo(name = "coverAvatarUrl") val coverAvatarUrl: String?,
    @androidx.room.ColumnInfo(name = "createTime") val createTime: Long,
    @androidx.room.ColumnInfo(name = "isLocalOnly") val isLocalOnly: Boolean,
    @androidx.room.ColumnInfo(name = "contactCount") val contactCount: Int,
) {
    fun toCacheEntity(): CardCollectionCacheEntity = CardCollectionCacheEntity(
        id = id,
        name = name,
        description = description,
        backgroundImagePath = backgroundImagePath,
        dominantColor = dominantColor,
        coverAvatarUrl = coverAvatarUrl,
        createTime = createTime,
        isLocalOnly = isLocalOnly,
    )
}

/**
 * 重复联系人检查结果。
 *
 * 由 ContactRepository.checkDuplicate 返回;existingContact 改为 ContactCacheEntity。
 */
@Immutable
data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val existingContact: top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity?,
    val similarityScore: Float,
    val matchFields: List<String>
)

// ============================================================
// 字段合并(merge contact 用)
// ============================================================

enum class MergeChoice {
    /** 保留已有值,不做任何操作 */
    KEEP,
    /** 替换已有值为新值 */
    REPLACE,
    /** 追加新值(同一字段多个值) */
    APPEND
}

@Immutable
data class FieldMergeEntry(
    val fieldKey: String,
    val fieldName: String,
    val existingValue: String?,
    val newValue: String?,
    val selectedValue: MergeChoice = MergeChoice.APPEND
)

// ============================================================
// QAuxv 导入流程类型
// ============================================================

sealed class QAuxvConflictAction {
    data object Skip : QAuxvConflictAction()
    data object Replace : QAuxvConflictAction()
    data object InsertAnyway : QAuxvConflictAction()
}

@Immutable
data class QAuxvImportSummary(
    val inserted: Int = 0,
    val replaced: Int = 0,
    val skipped: Int = 0,
)

@Immutable
data class QAuxvImportProgress(
    val phase: Phase,
    val current: Int,
    val total: Int,
) {
    enum class Phase { AvatarDownloading, Writing }

    fun displayLabel(): String = when (phase) {
        Phase.AvatarDownloading -> "下载头像"
        Phase.Writing -> "写入联系人"
    }
}