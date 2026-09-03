package top.mcxiafeng.badger.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.mcxiafeng.badger.data.CardCollectionWithCount
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.data.CustomField
import top.mcxiafeng.badger.data.PersonFieldDisplay
import top.mcxiafeng.badger.data.ContactFieldValue
import top.mcxiafeng.badger.data.PersonWithFields
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.cache.entity.CustomFieldCacheEntity
import top.mcxiafeng.badger.data.cache.entity.PersonProfileCacheEntity
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.network.PersonDto
import top.mcxiafeng.badger.network.ProfileDto
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.utils.PinyinUtils

/**
 * V2 cache entity ↔ UI 消费类型映射器。
 *
 * [A3] 集中所有 V2 cache entity 与 UI 包装类之间的互转逻辑;Repository 调用本类,
 * 不允许 Repository 内联手写 toX/toCacheEntity。
 *
 * 关键映射:
 * - `ContactCacheEntity.platformsJson` ↔ `PlatformEntry`(Gson 序列化)
 * - `UserProfileCacheEntity.platformsJson` ↔ `PlatformEntry`(Gson 序列化)
 * - `ContactFieldValueCacheEntity` ↔ `PersonFieldDisplay`(UI 展示层)
 * - `CardCollectionCacheEntity` + `contactCount` ↔ `CardCollectionWithCount`
 *
 * ProfileDto 字段映射（对齐 `Badger-Server/docs/api-handover.md` §4.1 Profile 字段表）：
 * - `ProfileDto.avatarURL` ↔ `ContactCacheEntity.avatarUrl` / `UserProfileCacheEntity.avatarPath`
 * - `ProfileDto.description` ↔ `ContactCacheEntity.bio` / `UserProfileCacheEntity.bio`（旧 `signature` 已改名）
 * - `ProfileDto.contactMap` ↔ `platformsJson`（`Map<String,PlatformEntry>`，platformKey → value 非空条目）
 * - `ProfileDto.sex` / `backgroundURL` / `country` / `region` / `birthday` / `extra`
 *   当前未持久化，由 Phase 2 `person_profile_cache` 子表承接
 * - `PersonDto` → `ContactCacheEntity`：`uuid`→`serverId`、`profile.avatarURL`→`avatarUrl`、
 *   `profile.description`→`bio`、`profile.contactMap`→`platformsJson`（见 [toContactCacheEntity]）
 *
 * [T08 警告] 本对象只提供**单向**映射（DTO/展示 → entity、entity → 展示），不是 UI 投影
 * 的 round-trip 通道：任何把 UI 投影转回 entity 用于写路径的行为都必须经 `sync/Identity.kt`
 * 的 `rebaseCollection` / `rebaseTag`，identity 字段（serverId / personMembers / isLocalOnly /
 * createTime）以 DB existing 为准（F3）。
 *
 * 注意:Repository 内部 helper 调用,本类不对外暴露。
 */
internal object ContactMapper {

    private val gson = Gson()
    private val platformsType = object : TypeToken<Map<String, PlatformEntry>>() {}.type

    // ========== Contact ↔ ContactCacheEntity ==========

    fun ContactCacheEntity.toPersonWithFields(fields: List<PersonFieldDisplay>): PersonWithFields =
        PersonWithFields(contact = this, fieldValues = fields)

    fun ContactFieldValueCacheEntity.toFieldDisplay(
        fieldName: String,
        fieldKey: String?,
        icon: String?,
        sortOrder: Int,
    ): PersonFieldDisplay = PersonFieldDisplay(
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

    /**
     * ContactField → ContactFieldCacheEntity 转换。
     * 两个表 schema 一致，直接映射。
     */
    fun ContactField.toCacheEntity(): ContactFieldCacheEntity =
        ContactFieldCacheEntity(
            id = id,
            fieldName = fieldName,
            fieldKey = fieldKey,
            icon = icon,
            sortOrder = sortOrder,
            isSystem = isSystem,
            isEnabled = isEnabled,
            createTime = createTime,
        )

    // ========== CustomField ↔ CustomFieldCacheEntity ==========

    /**
     * CustomField → CustomFieldCacheEntity 转换。
     * 两个表 schema 一致，直接映射。
     */
    fun CustomField.toCacheEntity(): CustomFieldCacheEntity =
        CustomFieldCacheEntity(
            id = id,
            fieldName = fieldName,
            fieldType = fieldType,
            options = options,
            sortOrder = sortOrder,
            isEnabled = isEnabled,
            createTime = createTime,
        )

    /**
     * CustomFieldCacheEntity → CustomField 转换。
     * 两个表 schema 一致，直接映射。
     */
    fun CustomFieldCacheEntity.toCustomField(): CustomField =
        CustomField(
            id = id,
            fieldName = fieldName,
            fieldType = fieldType,
            options = options,
            sortOrder = sortOrder,
            isEnabled = isEnabled,
            createTime = createTime,
        )

    // ========== CardCollection ↔ CardCollectionCacheEntity ==========

    fun CardCollectionWithCount.toCacheEntity(): CardCollectionCacheEntity = CardCollectionCacheEntity(
        id = id,
        name = name,
        description = description,
        backgroundImagePath = backgroundImagePath,
        dominantColor = dominantColor,
        coverAvatarUrl = coverAvatarUrl,
        createTime = createTime,
        isLocalOnly = isLocalOnly,
    )

    // ========== UserProfile ↔ UserProfileCacheEntity ==========

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

    // ========== [Phase 3] Person/Profile ↔ cache mapping ==========

    /**
     * 服务端 `Profile.contactMap`(Map<String,String>) → `contact_platforms_cache` 行。
     * displayName / jumpLink 由 fieldKey+value 本地推导（`FIELD_DEF_MAP` + [buildPlatformLink]），
     * 服务端不返回这些展示字段。isLocalOnly=false（来自服务端权威）。
     */
    fun ProfileDto.toPlatformRows(contactId: Long): List<ContactPlatformCacheEntity> =
        contactMap.mapNotNull { (key, value) ->
            if (value.isBlank()) return@mapNotNull null
            val def = FIELD_DEF_MAP[key]
            ContactPlatformCacheEntity(
                contactId = contactId,
                platformKey = key,
                value = value,
                displayName = def?.displayName,
                jumpLink = buildPlatformLink(key, value),
                originalLink = null,
                avatarUrl = null,
                isLocalOnly = false,
            )
        }

    /**
     * [T14] Contact 行 + 平台行 → 创建/更新 person 的 `profile` 请求体。
     * ContactRepositoryImpl 与 SyncEngine.createOnPush 共用（AGENTS.md：相同模式必须抽取）。
     */
    fun buildProfileDto(
        contact: ContactCacheEntity,
        platformRows: List<ContactPlatformCacheEntity>,
    ): ProfileDto = ProfileDto(
        avatarURL = contact.avatarUrl,
        description = contact.bio,
        contactMap = platformRows
            .mapNotNull { row -> row.value?.takeIf { it.isNotBlank() }?.let { row.platformKey to it } }
            .toMap(),
    )

    /**
     * 服务端 `Profile.contactMap` → `platformsJson`（`Map<String, PlatformEntry>` UI 契约）。
     * 保持既有 UI 层的 `decodePlatformsMap` 形状不变，展示字段本地推导。
     */
    fun ProfileDto.toPlatformsJson(): String {
        val map = contactMap.mapValues { (key, value) ->
            val def = FIELD_DEF_MAP[key]
            PlatformEntry(
                displayName = def?.displayName,
                jumpLink = buildPlatformLink(key, value),
                originalLink = null,
                value = value,
                avatarUrl = null,
            )
        }
        return encodePlatformsMap(map)
    }

    /**
     * 服务端 Person 行 → 本地 `ContactCacheEntity`（sync ADD 重放用）。
     * `serverId` = 服务端 uuid；avatarURL→avatarUrl、description→bio；
     * `platformsJson` 由 profile.contactMap 生成（保持 UI 形状）。
     *
     * [Phase 2] v9 新增：`self` 持久化。
     */
    fun PersonDto.toContactCacheEntity(id: Long, avatarPath: String? = null): ContactCacheEntity {
        val now = System.currentTimeMillis()
        return ContactCacheEntity(
            id = id,
            serverId = uuid.takeIf { it.isNotBlank() },
            name = name,
            avatarUrl = profile?.avatarURL,
            avatarPath = avatarPath,
            bio = profile?.description,
            pinyinInitial = if (name.isNotBlank()) PinyinUtils.getContactPinyinInitial(name) else "",
            platformsJson = profile?.toPlatformsJson() ?: "{}",
            createTime = createTimeMillis().takeIf { it > 0 } ?: now,
            updateTime = updateTimeMillis().takeIf { it > 0 } ?: now,
            lastSyncedAt = now,
            isLocalOnly = false,
            isDeleted = false,
            self = self,
        )
    }

    /**
     * [Phase 2] `ProfileDto` → `PersonProfileCacheEntity`（sync ADD/UPDATE 写入子表）。
     * 仅在 profile 字段非空时有意义；`contactServerId` 由调用方传入。
     */
    fun ProfileDto.toPersonProfileEntity(contactServerId: String): PersonProfileCacheEntity =
        PersonProfileCacheEntity(
            contactServerId = contactServerId,
            sex = sex,
            country = country,
            region = region,
            birthday = birthday,
            backgroundURL = backgroundURL,
            extra = extra?.toString(),
        )
}