package top.mcxiafeng.badger.data

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

// ============================================================
// 历史字段 DTO（非 V1 HTTP API）
// ============================================================

/**
 * 联系人字段定义（系统预置字段）。
 *
 * 本类型是历史数据/业务 DTO，不是 V1 HTTP API compatibility facade。
 * V2 cache 表已退役旧 Room 表；当前仍由 FieldRepository / ContactMapper 使用时保留。
 */
@Immutable
data class ContactField(
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
 * 本类型是历史数据/业务 DTO，不是 V1 HTTP API compatibility facade。
 * V2 cache 表已退役旧 Room 表；当前仍有业务引用时保留。
 */
@Immutable
data class CustomField(
    val id: Long = 0,
    val fieldName: String,
    val fieldType: String,
    val options: String,
    val sortOrder: Int = 0,
    val isEnabled: Boolean = true,
    val createTime: Long = System.currentTimeMillis()
)

/**
 * 联系人字段值（关联数据）。
 *
 * 本类型是历史数据/业务 DTO，不是 V1 HTTP API compatibility facade。
 * V2 cache 已不再映射旧 `contact_field_values` Room 表；当前仍有业务引用时保留。
 */
@Immutable
data class ContactFieldValue(
    val id: Long = 0,
    val contactId: Long,
    val fieldId: Long? = null,
    val customFieldId: Long? = null,
    val value: String,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

// ============================================================
// 共享平台 JSON shape（供 V2 cache 使用）
// ============================================================

/**
 * 社交平台条目（共享 JSON shape）。
 *
 * 该类型用于 V2 cache 中 `platformsJson` 的 Gson 序列化/反序列化。
 * 字段名是稳定的 JSON wire format，不代表需要保留 V1 HTTP API。
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
// 业务包装类型（Repository / UI 间共用，与 cache entity 解耦）
// ============================================================

/**
 * 联系人字母桶统计。
 *
 * 由 ContactCacheDao.getLetterIndex 返回，UI 侧栏渲染。
 */
@Immutable
data class LetterCount(val letter: String, val count: Int)

/**
 * 联系人及其所有字段值的组合数据类。
 *
 * UI 侧一次性展示联系人完整信息时使用，内嵌 ContactCacheEntity。
 * [Phase 5] 原名 ContactWithFields，向 Person* 命名收敛。
 */
@Immutable
data class PersonWithFields(
    val contact: top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity,
    val fieldValues: List<PersonFieldDisplay>
)

/**
 * 联系人字段值的展示数据类。
 *
 * UI 层直接渲染。
 * [Phase 5] 原名 ContactFieldDisplay，向 Person* 命名收敛。
 */
@Immutable
data class PersonFieldDisplay(
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
 * [A3] 用扁平投影（避免 @Embedded 与 cache JOIN 冲突）。
 * KSP 必须在 @Database 同 module 看到该类，所以它在 Models.kt 顶层定义。
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
    /**
     * UI 投影 → 实体的便捷转换。
     *
     * [T08 警告] 本转换**不携带 identity 字段**（serverId / personMembers 丢失），仅限 UI 展示
     * 与只读场景。**禁止把返回值直接用于写路径**（@Update 全行覆盖会把已同步名片夹孤立出同步
     * 体系，F3）。写路径必须经 `sync/Identity.kt` 的 `rebaseCollection` 用 DB existing 行 rebase。
     */
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
 * 由 ContactRepository.checkDuplicate 返回；existingContact 改为 ContactCacheEntity。
 */
@Immutable
data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val existingContact: top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity?,
    val similarityScore: Float,
    val matchFields: List<String>
)

// ============================================================
// 字段合并（merge contact 用）
// ============================================================

enum class MergeChoice {
    /** 保留已有值，不做任何操作 */
    KEEP,
    /** 替换已有值为新值 */
    REPLACE,
    /** 追加新值（同一字段多个值） */
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