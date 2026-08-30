package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.data.ContactFieldValue
import top.mcxiafeng.badger.data.CustomField
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.CustomFieldCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactFieldCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.CustomFieldCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toContactField
import top.mcxiafeng.badger.data.repository.ContactMapper.toCustomField
import top.mcxiafeng.badger.data.repository.ContactMapper.toFieldValue

/**
 * [§14.2] Hilt `@Inject constructor` → Koin `singleOf(::FieldRepositoryImpl) { bind<FieldRepository>() }`。
 *
 * ## Phase 3 完成状态
 *
 * **当前状态：V1 表已退役（Task #17）**
 * - V1 表（contact_fields / custom_fields / contact_field_values）已删除
 * - 所有读写操作走 V2 cache 表
 * - V1 entity 类保留作为数据传输对象（DTO）
 *
 * **V2 cache 表：**
 * - `contact_fields_cache`（ContactFieldCacheEntity）
 * - `contact_field_values_cache`（ContactFieldValueCacheEntity）
 * - `custom_fields_cache`（CustomFieldCacheEntity）
 *
 * @see deprecation-and-migration skill: expand/contract 四步走
 */
class FieldRepositoryImpl(
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    // V2 cache DAO（主路径）
    private val contactFieldCacheDao: ContactFieldCacheDao,
    private val contactFieldValueCacheDao: ContactFieldValueCacheDao,
    private val customFieldCacheDao: CustomFieldCacheDao,
) : FieldRepository {

    // ========== 系统预置字段操作 ==========

    override fun getAllEnabledFields(): Flow<List<ContactField>> {
        return contactFieldCacheDao.getAllEnabledFields().map { list ->
            list.map { it.toContactField() }
        }
    }

    override suspend fun getAllFieldsOnce(): List<ContactField> = withContext(Dispatchers.IO) {
        contactFieldCacheDao.getAllFieldsOnce().map { it.toContactField() }
    }

    override suspend fun getFieldByKey(key: String): ContactField? = withContext(Dispatchers.IO) {
        contactFieldCacheDao.getFieldByKey(key)?.toContactField()
    }

    override suspend fun getFieldById(id: Long): ContactField? = withContext(Dispatchers.IO) {
        contactFieldCacheDao.getFieldById(id)?.toContactField()
    }

    override suspend fun insertField(field: ContactField): Long = withContext(Dispatchers.IO) {
        contactFieldCacheDao.insertField(field.toCacheEntity())
    }

    override suspend fun updateField(field: ContactField) = withContext(Dispatchers.IO) {
        contactFieldCacheDao.updateField(field.toCacheEntity())
    }

    override suspend fun deleteField(field: ContactField) = withContext(Dispatchers.IO) {
        if (!field.isSystem) {
            contactFieldCacheDao.setFieldEnabled(field.id, false)
        }
    }

    override suspend fun setFieldEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        contactFieldCacheDao.setFieldEnabled(id, enabled)
    }

    override suspend fun updateFieldOrder(id: Long, order: Int) = withContext(Dispatchers.IO) {
        contactFieldCacheDao.updateFieldOrder(id, order)
    }

    // ========== 自定义字段操作 ==========

    override fun getAllEnabledCustomFields(): Flow<List<CustomField>> {
        return customFieldCacheDao.getAllEnabledCustomFields().map { list ->
            list.map { it.toCustomField() }
        }
    }

    override suspend fun getCustomFieldById(id: Long): CustomField? = withContext(Dispatchers.IO) {
        customFieldCacheDao.getCustomFieldById(id)?.toCustomField()
    }

    override suspend fun insertCustomField(field: CustomField): Long = withContext(Dispatchers.IO) {
        customFieldCacheDao.insertCustomField(field.toCacheEntity())
    }

    override suspend fun updateCustomField(field: CustomField) = withContext(Dispatchers.IO) {
        customFieldCacheDao.updateCustomField(field.toCacheEntity())
    }

    override suspend fun deleteCustomField(field: CustomField) = withContext(Dispatchers.IO) {
        customFieldCacheDao.setCustomFieldEnabled(field.id, false)
    }

    override suspend fun setCustomFieldEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        customFieldCacheDao.setCustomFieldEnabled(id, enabled)
    }

    override suspend fun updateCustomFieldOrder(id: Long, order: Int) = withContext(Dispatchers.IO) {
        customFieldCacheDao.updateCustomFieldOrder(id, order)
    }

    // ========== 字段值操作 ==========

    override suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValue> = withContext(Dispatchers.IO) {
        contactFieldValueCacheDao.getFieldValuesByContactOnce(contactId).map { it.toFieldValue() }
    }

    override suspend fun insertFieldValue(value: ContactFieldValue): Long = withContext(Dispatchers.IO) {
        contactFieldValueCacheDao.insertFieldValue(value.toCacheEntity())
    }

    override suspend fun updateFieldValue(value: ContactFieldValue) = withContext(Dispatchers.IO) {
        contactFieldValueCacheDao.updateFieldValue(value.toCacheEntity())
    }

    override suspend fun deleteFieldValue(value: ContactFieldValue) = withContext(Dispatchers.IO) {
        contactFieldValueCacheDao.deleteByContact(value.contactId)
    }

    override suspend fun saveContactFieldValues(contactId: Long, fieldValues: Map<Long, String>) = withContext(Dispatchers.IO) {
        // [修复防御]: 委托给 List 版本，消除重复实现
        saveContactFieldValues(contactId, fieldValues.toList())
    }

    override suspend fun saveContactFieldValues(contactId: Long, fieldValues: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        val values = fieldValues.map { (fieldId, value) ->
            ContactFieldValue(contactId = contactId, fieldId = fieldId, value = value)
        }
        val cacheValues = values.map { it.toCacheEntity() }
        contactFieldValueCacheDao.insertOrUpdateFieldValues(cacheValues)
    }

    override suspend fun saveContactCustomFieldValues(contactId: Long, fieldValues: Map<Long, String>) = withContext(Dispatchers.IO) {
        val values = fieldValues.map { (customFieldId, value) ->
            ContactFieldValue(contactId = contactId, customFieldId = customFieldId, value = value)
        }
        val cacheValues = values.map { it.toCacheEntity() }
        contactFieldValueCacheDao.insertOrUpdateFieldValues(cacheValues)
    }

    override suspend fun getFieldValueByContactAndKey(contactId: Long, fieldKey: String): String? = withContext(Dispatchers.IO) {
        val field = contactFieldCacheDao.getFieldByKey(fieldKey) ?: return@withContext null
        contactFieldValueCacheDao.getFieldValue(contactId, field.id)
    }

    override suspend fun updateFieldValueByKey(
        contactId: Long,
        fieldKey: String,
        newValue: String,
    ) = withContext(Dispatchers.IO) {
        val field = contactFieldCacheDao.getFieldByKey(fieldKey)
        if (field == null) {
            Log.w(TAG, "updateFieldValueByKey: ContactField key='$fieldKey' not found, skip")
            return@withContext
        }

        // 单值字段:用 INSERT,主键冲突的旧值会被覆盖(Room @Insert 默认 ABORT,因此改用先查再写)
        val existing = contactFieldValueCacheDao.getFieldValue(contactId, field.id)
        if (existing != null) {
            val allValues = contactFieldValueCacheDao.getFieldValuesByContactOnce(contactId)
            val target = allValues.firstOrNull { it.fieldId == field.id } ?: return@withContext
            contactFieldValueCacheDao.updateFieldValue(
                target.copy(value = newValue, updateTime = System.currentTimeMillis())
            )
        } else {
            contactFieldValueCacheDao.insertFieldValue(
                ContactFieldValueCacheEntity(
                    contactId = contactId,
                    fieldId = field.id,
                    value = newValue,
                    createTime = System.currentTimeMillis(),
                    updateTime = System.currentTimeMillis(),
                )
            )
        }
    }

    override suspend fun getCustomFieldValueByContactAndFieldId(contactId: Long, customFieldId: Long): String? = withContext(Dispatchers.IO) {
        contactFieldValueCacheDao.getCustomFieldValue(contactId, customFieldId)
    }

    override suspend fun getFieldValueMapByContact(contactId: Long): Map<String, String> = withContext(Dispatchers.IO) {
        buildMap {
            val fieldValues = contactFieldValueCacheDao.getFieldValuesByContactOnce(contactId)
            for (fv in fieldValues) {
                val key = when {
                    fv.fieldId != null -> contactFieldCacheDao.getFieldById(fv.fieldId)?.fieldKey
                    fv.customFieldId != null -> "custom_${fv.customFieldId}"
                    else -> null
                }
                if (key != null && key !in this) put(key, fv.value)
            }

            // 平台字段（qq/wechat/...）存在 contact_platforms_cache 表里(V2 主路径)
            val platforms = contactPlatformCacheDao.getPlatformsByContact(contactId)
            for (platform in platforms) {
                val pk = platform.platformKey
                val pv = platform.value
                if (pk.isNotBlank() && pv != null && pk !in this) {
                    put(pk, pv)
                }
            }
        }
    }

    companion object {
        private const val TAG = "FieldRepository"
    }
}
