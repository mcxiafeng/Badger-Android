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

/**
 * V2 cache entity ↔ UI 消费类型映射器。
 *
 * [A3] 集中所有 V2 cache entity 与 UI 包装类之间的互转逻辑;Repository 调用本类,
 * 不允许 Repository 内联手写 toX/toCacheEntity。
 *
 * 关键映射:
 * - `ContactCacheEntity.platformsJson` ↔ `PlatformEntry`(kotlinx.serialization)
 * - `UserProfileCacheEntity.platformsJson` ↔ `PlatformEntry`(kotlinx.serialization)
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
 * [KMP K08-B] 原 internal（app 模块内 helper）→ public：跨模块后 FieldRepositoryImpl
 * 等 app 侧 Repository 仍需引用。函数语义不变。
 */
object ContactMapper {

    private val platformsSerializer = MapSerializer(String.serializer(), PlatformEntry.serializer())

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
        return Json.encodeToString(platformsSerializer, map)
    }

    fun decodePlatformsMap(json: String?): Map<String, PlatformEntry>? {
        if (json.isNullOrBlank() || json == "{}") return null
        return runCatching { Json.decodeFromString(platformsSerializer, json) }
            .onFailure { BadgerLog.w("ContactMapper", "decodePlatformsMap 解析失败: ${it.message}") }
            .getOrNull()
    }

    // ========== [Phase 3] Person/Profile ↔ cache mapping ==========

    /**
     * 服务端 `Profile.contactMap`(Map<String,String>) → `contact_platforms_cache` 行。
     * jumpLink 由 fieldKey+value 本地推导（`buildPlatformLink`）；displayName/avatarUrl
     * 只从 `extra[platform]` 读取（resolver 落点的真实昵称/头像）。**禁止回退到
     * `FIELD_DEF_MAP[key].displayName`（平台标签）播种**——UI 把条目 displayName 当昵称
     * 渲染，同步信息失败时也拿它当昵称回退，播种"QQ"会导致联系人被改名为"QQ"。
     */
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

    /**
     * [T14] Contact 行 + 平台行 → 创建/更新 person 的 `profile` 请求体。
     * ContactRepositoryImpl 与 SyncEngine.createOnPush 共用（AGENTS.md：相同模式必须抽取）。
     *
     * [basicInfo] = 基础信息字段行（fieldKey → 值，见 [loadBasicFieldValues]）。
     * 服务端 PUT persons 是 profile **整段替换**——漏带 sex/birthday/country/region 会在
     * 任何一次平台/资料推送时静默抹掉 Web 端已填的这些字段，绝不能省。
     *
     * [profileExtra] / [backgroundURL] 来自 `PersonProfileCacheEntity`，防止整段替换时清空。
     */
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

    /**
     * 将 PersonProfileCacheEntity.extra（JSON String）解析为 JsonObject，供 ProfileDto 使用。
     * 解析失败时返回 null（不阻塞推送，但记日志——与服务端"null = 不更新"语义一致）。
     */
    private fun parseExtraToJson(raw: String?): JsonObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { BadgerJson.parseToJsonElement(raw) as JsonObject }
            .onFailure { BadgerLog.w(TAG, "parseExtraToJson: JSON 解析失败,丢弃", it) }
            .getOrNull()
    }

    /** 从 ProfileDto.extra 读取平台级字符串字段（avatar / platformName 等），非字符串或缺失返回 null。 */
    private fun extraString(extra: JsonObject?, platformKey: String, field: String): String? {
        val bucket = extra?.get(platformKey) as? JsonObject ?: return null
        val element = bucket[field] ?: return null
        return runCatching { element.jsonPrimitive.content }.getOrNull()
    }

    /**
     * 将各平台的展示元数据（昵称/头像）合并进 extra JsonObject（推送前调用）。
     * 服务端 contactMap 只存 value，昵称/头像唯一的跨端落点是 extra[platform] 的
     * "platformName"/"avatar"（键名与服务端 FetchEngine.mirrorProfileIntoExtra 对齐）——
     * 不合并则推送后头像丢失、本地手改昵称被其他端 echo 回滚。
     * [防御] displayName 若等于 FIELD_DEF_MAP 平台标签（历史播种脏值），不写入 extra，
     * 避免把"QQ"这类标签固化成昵称。
     */
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

    /** 基础信息 fieldKey 常量（contact_field_cache 种子键，同步链路共用）。 */
    private const val TAG = "ContactMapper"
    private const val KEY_GENDER = "gender"
    private const val KEY_BIRTHDAY = "birthday"
    private const val KEY_COUNTRY = "country"
    private const val KEY_REGION = "region"

    /**
     * 读取联系人的基础信息字段值（gender/birthday/country/region），push 侧组装 profile 用。
     * 有字段行才进 map（值可为空串=已清空，也要传给服务端抹掉旧值）；无行 = 从未设置 = 省略。
     */
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

    /**
     * 服务端 `Profile.contactMap` → `platformsJson`（`Map<String, PlatformEntry>` UI 契约）。
     * jumpLink 保持本地推导；displayName/avatarUrl 只从 `extra[platform]` 回填真实值，
     * 不播种平台标签（理由见 [toPlatformRows] 注释）。
     */
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

    /**
     * 服务端 Person 行 → 本地 `ContactCacheEntity`（sync ADD 重放用）。
     * `serverId` = 服务端 uuid；avatarURL→avatarUrl、description→bio；
     * `platformsJson` 由 profile.contactMap 生成（保持 UI 形状）。
     *
     * [Phase 2] v9 新增：`self` 持久化。
     */
    /**
     * profile.avatarURL 的本地形状防御：非 http(s) 形状的"URL"是历史 bug 上传的设备本地路径，
     * pull 时归位到 avatarPath 列（避免详情页把它当远程地址走 HTTP 下载必然失败）。
     * 返回 (远程 URL 或 null, 本地路径或 null)。
     */
    fun splitRemoteAndLocalAvatar(avatarURL: String?): Pair<String?, String?> {
        val localShaped = avatarURL
            ?.takeIf { it.isNotBlank() && !it.startsWith("http://") && !it.startsWith("https://") }
        return (if (localShaped != null) null else avatarURL) to localShaped
    }

    fun PersonDto.toContactCacheEntity(id: Long, avatarPath: String? = null): ContactCacheEntity {
        val now = nowMs()
        // [修复防御] 历史 push 把来源设备的本地路径当 avatarURL 上传过（见 UserProfileRepositoryImpl
        // resolveAvatarUrl 修复），pull 到非 http(s) 形状的"URL"时归位到 avatarPath 列，
        // 避免详情页把它当远程地址走 HTTP 下载必然失败。
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