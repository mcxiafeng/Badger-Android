package top.mcxiafeng.badger.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.mcxiafeng.badger.data.CardCollectionWithCount
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactFieldValue
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity

/**
 * V2 cache entity ↔ UI 消费类型映射器。
 *
 * [A3] 集中所有 V2 cache entity 与 UI 包装类之间的互转逻辑;Repository 调用本类,
 * 不允许 Repository 内联手写 toX/toCacheEntity。
 *
 * 关键映射:
 * - `ContactCacheEntity.platformsJson` ↔ `PlatformEntry`(Gson 序列化)
 * - `UserProfileCacheEntity.platformsJson` ↔ `PlatformEntry`(Gson 序列化)
 * - `ContactFieldValueCacheEntity` ↔ `ContactFieldDisplay`(UI 展示层)
 * - `CardCollectionCacheEntity` + `contactCount` ↔ `CardCollectionWithCount`
 *
 * 注意:Repository 内部 helper 调用,本类不对外暴露。
 */
internal object ContactMapper {

    private val gson = Gson()
    private val platformsType = object : TypeToken<Map<String, PlatformEntry>>() {}.type

    // ========== Contact ↔ ContactCacheEntity ==========

    fun ContactCacheEntity.toContact(): ContactCacheEntity = this

    fun ContactCacheEntity.toContactWithFields(fields: List<ContactFieldDisplay>): ContactWithFields =
        ContactWithFields(contact = this, fieldValues = fields)

    fun ContactFieldValueCacheEntity.toFieldDisplay(
        fieldName: String,
        fieldKey: String?,
        icon: String?,
        sortOrder: Int,
    ): ContactFieldDisplay = ContactFieldDisplay(
        valueId = id,
        fieldId = fieldId,
        customFieldId = customFieldId,
        fieldName = fieldName,
        fieldKey = fieldKey,
        icon = icon,
        fieldType = null,
        value = value,
        sortOrder = sortOrder,
    )

    fun ContactFieldValue.toCacheEntity(): ContactFieldValueCacheEntity = ContactFieldValueCacheEntity(
        id = id,
        contactId = contactId,
        fieldId = fieldId,
        customFieldId = customFieldId,
        value = value,
        displayOrder = 0,
        createTime = createTime,
        updateTime = updateTime,
        serverVersion = 0L,
        isLocalOnly = true,
    )

    fun ContactFieldValueCacheEntity.toFieldValue(): ContactFieldValue = ContactFieldValue(
        id = id,
        contactId = contactId,
        fieldId = fieldId,
        customFieldId = customFieldId,
        value = value,
        createTime = createTime,
        updateTime = updateTime,
    )

    fun ContactFieldCacheEntity.toContactField(): ContactField = ContactField(
        id = id,
        fieldName = fieldName,
        fieldKey = fieldKey,
        icon = icon,
        sortOrder = sortOrder,
        isSystem = isSystem,
        isEnabled = isEnabled,
        createTime = createTime,
    )

    // ========== ContactPlatform ↔ ContactPlatformCacheEntity ==========

    fun ContactPlatformCacheEntity.toPlatform(): ContactPlatform = ContactPlatform(
        id = id,
        contactId = contactId,
        platformKey = platformKey,
        value = value,
        displayName = displayName,
        jumpLink = jumpLink,
        originalLink = originalLink,
        avatarUrl = avatarUrl,
    )

    fun ContactPlatform.toCacheEntity(): ContactPlatformCacheEntity = ContactPlatformCacheEntity(
        id = id,
        contactId = contactId,
        platformKey = platformKey,
        value = value,
        displayName = displayName,
        jumpLink = jumpLink,
        originalLink = originalLink,
        avatarUrl = avatarUrl,
        serverVersion = 0L,
        isLocalOnly = true,
    )

    // ========== CardCollection ↔ CardCollectionCacheEntity ==========

    fun CardCollectionCacheEntity.toCardCollection(): CardCollectionCacheEntity = this

    fun CardCollectionWithCount.toCacheEntity(): CardCollectionCacheEntity = CardCollectionCacheEntity(
        id = id,
        name = name,
        description = description,
        backgroundImagePath = backgroundImagePath,
        dominantColor = dominantColor,
        coverAvatarUrl = coverAvatarUrl,
        createTime = createTime,
        serverVersion = serverVersion,
        isLocalOnly = isLocalOnly,
    )

    // ========== UserProfile ↔ UserProfileCacheEntity ==========

    fun UserProfileCacheEntity.toUserProfile(): UserProfileCacheEntity = this

    fun UserProfileCacheEntity.toPlatformsMap(): Map<String, PlatformEntry>? = decodePlatformsMap(platformsJson)

    fun encodePlatformsMap(map: Map<String, PlatformEntry>?): String {
        if (map.isNullOrEmpty()) return "{}"
        return gson.toJson(map)
    }

    fun decodePlatformsMap(json: String?): Map<String, PlatformEntry>? {
        if (json.isNullOrBlank() || json == "{}") return null
        return runCatching { gson.fromJson<Map<String, PlatformEntry>>(json, platformsType) }
            .getOrNull()
    }

    // ========== Tag ↔ TagCacheEntity ==========

    fun TagCacheEntity.toTag(): TagCacheEntity = this

    // ========== ContactTagCrossRef ↔ ContactTagCacheEntity ==========

    fun ContactTagCacheEntity.toCrossRef(): ContactTagCacheEntity = this

    // ========== DuplicateCheckResult mapping ==========

    fun DuplicateCheckResult.toDuplicateCheckResult(
        existingContact: ContactCacheEntity?,
    ): DuplicateCheckResult = DuplicateCheckResult(
        isDuplicate = isDuplicate,
        existingContact = existingContact,
        similarityScore = similarityScore,
        matchFields = matchFields,
    )
}