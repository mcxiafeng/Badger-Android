package top.mcxiafeng.badger.data.repository

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import top.mcxiafeng.badger.network.BadgerJson
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.data.model.CardCollectionWithCount
import top.mcxiafeng.badger.data.model.ContactField
import top.mcxiafeng.badger.data.model.CustomField
import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.mcxiafeng.badger.data.model.ContactFieldValue
import top.mcxiafeng.badger.data.model.PersonWithFields
import top.mcxiafeng.badger.data.model.PlatformEntry
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
import top.mcxiafeng.badger.shared.util.PinyinUtils

object ContactMapper {

    private val platformsSerializer = MapSerializer(String.serializer(), PlatformEntry.serializer())

    

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

    

    fun UserProfileCacheEntity.toPlatformsMap(): Map<String, PlatformEntry>? = decodePlatformsMap(platformsJson)

    fun encodePlatformsMap(map: Map<String, PlatformEntry>?): String {
        if (map.isNullOrEmpty()) return "{}"
        return Json.encodeToString(platformsSerializer, map)
    }

    fun decodePlatformsMap(json: String?): Map<String, PlatformEntry>? {
        if (json.isNullOrBlank() || json == "{}") return null
        return runCatching { Json.decodeFromString(platformsSerializer, json) }
            .onFailure { BadgerLog.w("ContactMapper", "decodePlatformsMap 解析失败: ${it.message}") }
            .getOrNull()
    }

    

    

    fun ProfileDto.toPlatformRows(contactId: Long): List<ContactPlatformCacheEntity> =
        contactMap.mapNotNull { (key, value) ->
            if (value.isBlank()) return@mapNotNull null
            ContactPlatformCacheEntity(
                contactId = contactId,
                platformKey = key,
                value = value,
                displayName = extraString(extra, key, "platformName"),
                jumpLink = buildPlatformLink(key, value),
                originalLink = null,
                avatarUrl = extraString(extra, key, "avatar"),
                isLocalOnly = false,
            )
        }

    

    fun buildProfileDto(
        contact: ContactCacheEntity,
        platformRows: List<ContactPlatformCacheEntity>,
        basicInfo: Map<String, String> = emptyMap(),
        profileExtra: String? = null,
        backgroundURL: String? = null,
    ): ProfileDto = ProfileDto(
        sex = basicInfo[KEY_GENDER],
        birthday = basicInfo[KEY_BIRTHDAY],
        country = basicInfo[KEY_COUNTRY],
        region = basicInfo[KEY_REGION],
        avatarURL = contact.avatarUrl,
        backgroundURL = backgroundURL,
        description = contact.bio,
        contactMap = platformRows
            .mapNotNull { row -> row.value?.takeIf { it.isNotBlank() }?.let { row.platformKey to it } }
            .toMap(),
        extra = mergePlatformMetaIntoExtra(
            parseExtraToJson(profileExtra),
            platformRows.associate {
                it.platformKey to PlatformEntry(displayName = it.displayName, avatarUrl = it.avatarUrl)
            },
        ),
    )

    

    private fun parseExtraToJson(raw: String?): JsonObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { BadgerJson.parseToJsonElement(raw) as JsonObject }
            .onFailure { BadgerLog.w(TAG, "parseExtraToJson: JSON 解析失败,丢弃", it) }
            .getOrNull()
    }

    
    private fun extraString(extra: JsonObject?, platformKey: String, field: String): String? {
        val bucket = extra?.get(platformKey) as? JsonObject ?: return null
        val element = bucket[field] ?: return null
        return runCatching { element.jsonPrimitive.content }.getOrNull()
    }

    

    fun mergePlatformMetaIntoExtra(
        extra: JsonObject?,
        entries: Map<String, PlatformEntry>?,
    ): JsonObject? {
        val meaningful = entries
            ?.filterValues { !it.avatarUrl.isNullOrBlank() || !it.displayName.isNullOrBlank() }
            ?: return extra
        if (meaningful.isEmpty()) return extra
        val mutable = extra?.toMutableMap() ?: mutableMapOf()
        meaningful.forEach { (key, entry) ->
            val defLabel = FIELD_DEF_MAP[key]?.displayName
            val bucket = (mutable[key] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            entry.displayName?.takeIf { it.isNotBlank() && it != defLabel }
                ?.let { bucket["platformName"] = JsonPrimitive(it) }
            entry.avatarUrl?.takeIf { it.isNotBlank() }
                ?.let { bucket["avatar"] = JsonPrimitive(it) }
            if (bucket.isNotEmpty()) mutable[key] = JsonObject(bucket)
        }
        return JsonObject(mutable)
    }

    
    private const val TAG = "ContactMapper"
    private const val KEY_GENDER = "gender"
    private const val KEY_BIRTHDAY = "birthday"
    private const val KEY_COUNTRY = "country"
    private const val KEY_REGION = "region"

    

    suspend fun loadBasicFieldValues(
        fieldDao: top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao,
        fieldValueDao: top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao,
        contactId: Long,
    ): Map<String, String> {
        val keys = setOf(KEY_GENDER, KEY_BIRTHDAY, KEY_COUNTRY, KEY_REGION)
        val fields = fieldDao.getAllFieldsOnce().filter { it.fieldKey in keys }.associateBy { it.fieldKey }
        if (fields.isEmpty()) return emptyMap()
        val values = fieldValueDao.getFieldValuesByContactOnce(contactId)
        val byFieldId = values.associateBy { it.fieldId }
        return buildMap {
            fields.forEach { (key, field) ->
                byFieldId[field.id]?.let { put(key, it.value) }
            }
        }
    }

    

    fun ProfileDto.toPlatformsJson(): String {
        val map = contactMap.mapValues { (key, value) ->
            PlatformEntry(
                displayName = extraString(extra, key, "platformName"),
                jumpLink = buildPlatformLink(key, value),
                originalLink = null,
                value = value,
                avatarUrl = extraString(extra, key, "avatar"),
            )
        }
        return encodePlatformsMap(map)
    }

    

    

    fun splitRemoteAndLocalAvatar(avatarURL: String?): Pair<String?, String?> {
        val localShaped = avatarURL
            ?.takeIf { it.isNotBlank() && !it.startsWith("http://") && !it.startsWith("https://") }
        return (if (localShaped != null) null else avatarURL) to localShaped
    }

    fun PersonDto.toContactCacheEntity(id: Long, avatarPath: String? = null): ContactCacheEntity {
        val now = nowMs()
        
        
        
        val (remoteAvatarUrl, localShapedAvatar) = splitRemoteAndLocalAvatar(profile?.avatarURL)
        return ContactCacheEntity(
            id = id,
            serverId = uuid.takeIf { it.isNotBlank() },
            name = name,
            avatarUrl = remoteAvatarUrl,
            avatarPath = avatarPath ?: localShapedAvatar,
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