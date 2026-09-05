package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.model.ContactField
import top.mcxiafeng.badger.data.model.ContactFieldValue
import top.mcxiafeng.badger.data.model.CustomField
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.CustomFieldCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toContactField
import top.mcxiafeng.badger.data.repository.ContactMapper.toCustomField
import top.mcxiafeng.badger.data.repository.ContactMapper.toFieldValue
import top.mcxiafeng.badger.shared.util.nowMs

/** V2 字段数据仓库。 */
class FieldRepositoryImpl(
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val contactFieldCacheDao: ContactFieldCacheDao,
    private val contactFieldValueCacheDao: ContactFieldValueCacheDao,
    private val customFieldCacheDao: CustomFieldCacheDao,
) : FieldRepository {
    override fun getAllEnabledFields(): Flow<List<ContactField>> = contactFieldCacheDao.getAllEnabledFields().map { fields -> fields.map { it.toContactField() } }
    override suspend fun getAllFieldsOnce(): List<ContactField> = withContext(BadgerDispatchers.io) { contactFieldCacheDao.getAllFieldsOnce().map { it.toContactField() } }
    override suspend fun getFieldByKey(key: String): ContactField? = withContext(BadgerDispatchers.io) { contactFieldCacheDao.getFieldByKey(key)?.toContactField() }
    override suspend fun getFieldById(id: Long): ContactField? = withContext(BadgerDispatchers.io) { contactFieldCacheDao.getFieldById(id)?.toContactField() }
    override suspend fun insertField(field: ContactField): Long = withContext(BadgerDispatchers.io) { contactFieldCacheDao.insertField(field.toCacheEntity()) }
    override suspend fun updateField(field: ContactField) = withContext(BadgerDispatchers.io) { contactFieldCacheDao.updateField(field.toCacheEntity()) }
    override suspend fun deleteField(field: ContactField) = withContext(BadgerDispatchers.io) { if (!field.isSystem) contactFieldCacheDao.setFieldEnabled(field.id, false) }
    override suspend fun setFieldEnabled(id: Long, enabled: Boolean) = withContext(BadgerDispatchers.io) { contactFieldCacheDao.setFieldEnabled(id, enabled) }
    override suspend fun updateFieldOrder(id: Long, order: Int) = withContext(BadgerDispatchers.io) { contactFieldCacheDao.updateFieldOrder(id, order) }
    override fun getAllEnabledCustomFields(): Flow<List<CustomField>> = customFieldCacheDao.getAllEnabledCustomFields().map { fields -> fields.map { it.toCustomField() } }
    override suspend fun getCustomFieldById(id: Long): CustomField? = withContext(BadgerDispatchers.io) { customFieldCacheDao.getCustomFieldById(id)?.toCustomField() }
    override suspend fun insertCustomField(field: CustomField): Long = withContext(BadgerDispatchers.io) { customFieldCacheDao.insertCustomField(field.toCacheEntity()) }
    override suspend fun updateCustomField(field: CustomField) = withContext(BadgerDispatchers.io) { customFieldCacheDao.updateCustomField(field.toCacheEntity()) }
    override suspend fun deleteCustomField(field: CustomField) = withContext(BadgerDispatchers.io) { customFieldCacheDao.setCustomFieldEnabled(field.id, false) }
    override suspend fun setCustomFieldEnabled(id: Long, enabled: Boolean) = withContext(BadgerDispatchers.io) { customFieldCacheDao.setCustomFieldEnabled(id, enabled) }
    override suspend fun updateCustomFieldOrder(id: Long, order: Int) = withContext(BadgerDispatchers.io) { customFieldCacheDao.updateCustomFieldOrder(id, order) }
    override suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValue> = withContext(BadgerDispatchers.io) { contactFieldValueCacheDao.getFieldValuesByContactOnce(contactId).map { it.toFieldValue() } }
    override suspend fun insertFieldValue(value: ContactFieldValue): Long = withContext(BadgerDispatchers.io) { contactFieldValueCacheDao.insertFieldValue(value.toCacheEntity()) }
    override suspend fun updateFieldValue(value: ContactFieldValue) = withContext(BadgerDispatchers.io) { contactFieldValueCacheDao.updateFieldValue(value.toCacheEntity()) }
    override suspend fun deleteFieldValue(value: ContactFieldValue) = withContext(BadgerDispatchers.io) {
        val fieldId = value.fieldId
        val customFieldId = value.customFieldId
        when {
            fieldId != null -> contactFieldValueCacheDao.deleteByContactAndField(value.contactId, fieldId)
            customFieldId != null -> contactFieldValueCacheDao.deleteByContactAndCustomField(value.contactId, customFieldId)
            else -> BadgerLog.w(TAG, "deleteFieldValue: fieldId/customFieldId missing, skip")
        }
        Unit
    }
    override suspend fun saveContactFieldValues(contactId: Long, fieldValues: Map<Long, String>) = withContext(BadgerDispatchers.io) { saveContactFieldValues(contactId, fieldValues.toList()) }
    override suspend fun saveContactFieldValues(contactId: Long, fieldValues: List<Pair<Long, String>>) = withContext(BadgerDispatchers.io) {
        val now = nowMs()
        contactFieldValueCacheDao.insertOrUpdateFieldValues(fieldValues.map { (fieldId, value) -> ContactFieldValueCacheEntity(contactId = contactId, fieldId = fieldId, value = value, createTime = now, updateTime = now) })
    }
    override suspend fun saveContactCustomFieldValues(contactId: Long, fieldValues: Map<Long, String>) = withContext(BadgerDispatchers.io) {
        val now = nowMs()
        contactFieldValueCacheDao.insertOrUpdateFieldValues(fieldValues.map { (customFieldId, value) -> ContactFieldValueCacheEntity(contactId = contactId, customFieldId = customFieldId, value = value, createTime = now, updateTime = now) })
    }
    override suspend fun getFieldValueByContactAndKey(contactId: Long, fieldKey: String): String? = withContext(BadgerDispatchers.io) {
        val field = contactFieldCacheDao.getFieldByKey(fieldKey) ?: return@withContext null
        contactFieldValueCacheDao.getFieldValue(contactId, field.id)
    }
    override suspend fun updateFieldValueByKey(contactId: Long, fieldKey: String, newValue: String) = withContext(BadgerDispatchers.io) {
        val field = contactFieldCacheDao.getFieldByKey(fieldKey) ?: run { BadgerLog.w(TAG, "updateFieldValueByKey: ContactField key='$fieldKey' not found, skip"); return@withContext }
        val now = nowMs()
        val existing = contactFieldValueCacheDao.getFieldValueEntity(contactId, field.id)
        val updated = existing?.copy(value = newValue, updateTime = now) ?: ContactFieldValueCacheEntity(contactId = contactId, fieldId = field.id, value = newValue, createTime = now, updateTime = now)
        contactFieldValueCacheDao.insertOrUpdateFieldValues(listOf(updated))
    }
    override suspend fun getCustomFieldValueByContactAndFieldId(contactId: Long, customFieldId: Long): String? = withContext(BadgerDispatchers.io) { contactFieldValueCacheDao.getCustomFieldValue(contactId, customFieldId) }
    override suspend fun getFieldValueMapByContact(contactId: Long): Map<String, String> = withContext(BadgerDispatchers.io) {
        buildMap {
            val fieldValues = contactFieldValueCacheDao.getFieldValuesByContactOnce(contactId)
            val fieldMap = contactFieldCacheDao.getFieldsByIds(fieldValues.mapNotNull { it.fieldId }.distinct()).associateBy { it.id }
            for (fv in fieldValues) {
                val key = when {
                    fv.fieldId != null -> fieldMap[fv.fieldId]?.fieldKey
                    fv.customFieldId != null -> "custom_${fv.customFieldId}"
                    else -> null
                }
                if (key != null && key !in this) put(key, fv.value)
            }
            for (platform in contactPlatformCacheDao.getPlatformsByContact(contactId)) {
                val value = platform.value
                if (platform.platformKey.isNotBlank() && value != null && platform.platformKey !in this) put(platform.platformKey, value)
            }
        }
    }
    companion object { private const val TAG = "FieldRepository" }
}
