package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.model.ContactLocation
import top.mcxiafeng.badger.network.BadgerJson
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 联系人「位置」字段的读写收口（deep module：调用方只面对 ContactLocation / JsonObject）。
 *
 * 存储布局：
 * - 本地 = `contact_field_value_cache` 行，fieldKey=[FIELD_KEY]，value 为 [ContactLocation] JSON 字符串；
 *   field 定义行由 `AppDatabaseSeed.ensureDefaults`（SYSTEM_FIELDS）在开库期保证存在；
 * - 云端 = `Profile.location` JSON 对象（`ProfileDto.location`，ContactMapper/ServerApiBase 透传）。
 *
 * 消费方：
 * - ContactRepositoryImpl.push 路径（buildProfile → 附带当前 location）；
 * - SyncEngine.pull 路径（profile.location 下行 → 落 field value 行）；
 * - ContactDetailViewModel 编辑路径（选点确认 → 写行 + push）。
 */
object ContactLocationStore {

    const val FIELD_KEY = "location"
    private const val TAG = "ContactLocationStore"

    // ========== 编解码 ==========

    /** ContactLocation → 字段值 JSON 字符串（canonical）。 */
    fun encode(location: ContactLocation): String = BadgerJson.encodeToString(
        ContactLocation.serializer(),
        location,
    )

    /** 字段值 JSON 字符串 → ContactLocation（脏数据/旧格式 → null）。 */
    fun decode(value: String?): ContactLocation? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            BadgerJson.decodeFromString(ContactLocation.serializer(), value)
        }.onFailure {
            BadgerLog.w(TAG, "decode 解析失败: ${it.message}")
        }.getOrNull()?.takeIf { it.isNotBlankLocation() }
    }

    /** ContactLocation → Profile.location JSON 对象（云端契约，字段名对齐服务端 Profile.LocationInfo）。 */
    fun toJsonElement(location: ContactLocation): JsonObject = buildJsonObject {
        if (location.name.isNotBlank()) put("name", location.name)
        if (location.address.isNotBlank()) put("address", location.address)
        location.longitude?.let { put("longitude", it) }
        location.latitude?.let { put("latitude", it) }
        location.province?.let { put("province", it) }
        location.city?.let { put("city", it) }
        location.district?.let { put("district", it) }
        location.poiId?.let { put("poiId", it) }
        put("source", location.source)
    }

    /** 字段值 JSON 字符串 → Profile.location JSON 对象（push 路径一步到位；无值返回 null=云端清空）。 */
    fun fieldValueToJsonElement(value: String?): JsonObject? = decode(value)?.let { toJsonElement(it) }

    // ========== DB 读写（suspend，调用方需已在 IO 上下文） ==========

    /**
     * 读联系人当前 location 的 JSON 对象（push profile 时附带）。
     * field 定义缺失（老库未跑 ensureDefaults 的极端态）→ null 并告警。
     */
    suspend fun locationJsonValue(
        contactId: Long,
        fieldDao: ContactFieldCacheDao,
        valueDao: ContactFieldValueCacheDao,
    ): JsonObject? {
        val field = fieldDao.getFieldByKey(FIELD_KEY) ?: run {
            BadgerLog.w(TAG, "locationJsonValue: ContactField key='$FIELD_KEY' 未种子化, contactId=$contactId")
            return null
        }
        return fieldValueToJsonElement(valueDao.getFieldValue(contactId, field.id))
    }

    /**
     * 下行同步：profile.location JSON 对象 → 本地 field value 行。
     * null/空对象 = 删除本地行（服务端已清空）；解析失败按清空处理（保持与服务端权威一致）。
     */
    suspend fun writeFieldValueFromJson(
        contactId: Long,
        locationJson: JsonObject?,
        fieldDao: ContactFieldCacheDao,
        valueDao: ContactFieldValueCacheDao,
    ) {
        val field = fieldDao.getFieldByKey(FIELD_KEY) ?: run {
            BadgerLog.w(TAG, "writeFieldValueFromJson: ContactField key='$FIELD_KEY' 未种子化, contactId=$contactId")
            return
        }
        val location = runCatching {
            locationJson?.let { BadgerJson.decodeFromJsonElement(ContactLocation.serializer(), it) }
        }.onFailure {
            BadgerLog.w(TAG, "writeFieldValueFromJson: 解析失败按清空处理 contactId=$contactId: ${it.message}")
        }.getOrNull()?.takeIf { it.isNotBlankLocation() }
        writeRow(contactId, field.id, location, valueDao)
    }

    /** 编辑路径：ContactLocation 直接写行；null = 清除。 */
    suspend fun writeFieldValue(
        contactId: Long,
        location: ContactLocation?,
        fieldDao: ContactFieldCacheDao,
        valueDao: ContactFieldValueCacheDao,
    ) {
        val field = fieldDao.getFieldByKey(FIELD_KEY) ?: run {
            BadgerLog.w(TAG, "writeFieldValue: ContactField key='$FIELD_KEY' 未种子化, contactId=$contactId")
            return
        }
        writeRow(contactId, field.id, location, valueDao)
    }

    private suspend fun writeRow(
        contactId: Long,
        fieldId: Long,
        location: ContactLocation?,
        valueDao: ContactFieldValueCacheDao,
    ) {
        val now = nowMs()
        if (location == null) {
            val deleted = valueDao.deleteByContactAndField(contactId, fieldId)
            BadgerLog.d(TAG, "writeRow: 清除位置 contactId=$contactId deleted=$deleted")
            return
        }
        val existing = valueDao.getFieldValueEntity(contactId, fieldId)
        val row = (existing?.copy(value = encode(location), updateTime = now)
            ?: ContactFieldValueCacheEntity(
                contactId = contactId,
                fieldId = fieldId,
                value = encode(location),
                createTime = now,
                updateTime = now,
            ))
        valueDao.insertOrUpdateFieldValues(listOf(row))
        BadgerLog.d(TAG, "writeRow: 写入位置 contactId=$contactId name=${location.name}")
    }
}
