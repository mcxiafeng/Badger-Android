package top.mcxiafeng.badger.data.repository

import android.util.Log
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

/** V2 字段数据仓库。 */
class FieldRepositoryImpl(
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val contactFieldCacheDao: ContactFieldCacheDao,
    private val contactFieldValueCacheDao: ContactFieldValueCacheDao,
    private val customFieldCacheDao: CustomFieldCacheDao,
) : FieldRepository {
    override fun getAllEnabledFields(): Flow<List<ContactField>> = contactFieldCacheDao.getAllEnabledFields().map { fields -> fields.map { it.toContactField() } }
    override suspend fun getAllFieldsOnce(): List<ContactField> = withContext(Dispatchers.IO) { contactFieldCacheDao.getAllFieldsOnce().map { it.toContactField() } }
    override suspend fun getFieldByKey(key: String): ContactField? = withContext(Dispatchers.IO) { contactFieldCacheDao.getFieldByKey(key)?.toContactField() }
    override suspend fun getFieldById(id: Long): ContactField? = withContext(Dispatchers.IO) { contactFieldCacheDao.getFieldById(id)?.toContactField() }
    override suspend fun insertField(field: ContactField): Long = withContext(Dispatchers.IO) { contactFieldCacheDao.insertField(field.toCacheEntity()) }
    override suspend fun updateField(field: ContactField) = withContext(Dispatchers.IO) { contactFieldCacheDao.updateField(field.toCacheEntity()) }
    override suspend fun deleteField(field: ContactField) = withContext(Dispatchers.IO) { if (!field.isSystem) contactFieldCacheDao.setFieldEnabled(field.id, false) }
    override suspend fun setFieldEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) { contactFieldCacheDao.setFieldEnabled(id, enabled) }
    override suspend fun updateFieldOrder(id: Long, order: Int) = withContext(Dispatchers.IO) { contactFieldCacheDao.updateFieldOrder(id, order) }
    override fun getAllEnabledCustomFields(): Flow<List<CustomField>> = customFieldCacheDao.getAllEnabledCustomFields().map { fields -> fields.map { it.toCustomField() } }
    override suspend fun getCustomFieldById(id: Long): CustomField? = withContext(Dispatchers.IO) { customFieldCacheDao.getCustomFieldById(id)?.toCustomField() }
    override suspend fun insertCustomField(field: CustomField): Long = withContext(Dispatchers.IO) { customFieldCacheDao.insertCustomField(field.toCacheEntity()) }
    override suspend fun updateCustomField(field: CustomField) = withContext(Dispatchers.IO) { customFieldCacheDao.updateCustomField(field.toCacheEntity()) }
    override suspend fun deleteCustomField(field: CustomField) = withContext(Dispatchers.IO) { customFieldCacheDao.setCustomFieldEnabled(field.id, false) }
    override suspend fun setCustomFieldEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) { customFieldCacheDao.setCustomFieldEnabled(id, enabled) }
    override suspend fun updateCustomFieldOrder(id: Long, order: Int) = withContext(Dispatchers.IO) { customFieldCacheDao.updateCustomFieldOrder(id, order) }
    override suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValue> = withContext(Dispatchers.IO) { contactFieldValueCacheDao.getFieldValuesByContactOnce(contactId).map { it.toFieldValue() } }
    override suspend fun insertFieldValue(value: ContactFieldValue): Long = withContext(Dispatchers.IO) { contactFieldValueCacheDao.insertFieldValue(value.toCacheEntity()) }
    override suspend fun updateFieldValue(value: ContactFieldValue) = withContext(Dispatchers.IO) { contactFieldValueCacheDao.updateFieldValue(value.toCacheEntity()) }
    override suspend fun deleteFieldValue(value: ContactFieldValue) = withContext(Dispatchers.IO) {
        when {
            value.fieldId != null -> contactFieldValueCacheDao.deleteByContactAndField(value.contactId, value.fieldId)
            value.customFieldId != null -> contactFieldValueCacheDao.deleteByContactAndCustomField(value.contactId, value.customFieldId)
            else -> Log.w(TAG, "deleteFieldValue: fieldId/customFieldId missing, skip")
        }
        Unit
    }
    override suspend fun saveContactFieldValues(contactId: Long, fieldValues: Map<Long, String>) = withContext(Dispatchers.IO) { saveContactFieldValues(contactId, fieldValues.toList()) }
    override suspend fun saveContactFieldValues(contactId: Long, fieldValues: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        contactFieldValueCacheDao.insertOrUpdateFieldValues(fieldValues.map { (fieldId, value) -> ContactFieldValueCacheEntity(contactId = contactId, fieldId = fieldId, value = value, createTime = now, updateTime = now) })
    }
    override suspend fun saveContactCustomFieldValues(contactId: Long, fieldValues: Map<Long, String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        contactFieldValueCacheDao.insertOrUpdateFieldValues(fieldValues.map { (customFieldId, value) -> ContactFieldValueCacheEntity(contactId = contactId, customFieldId = customFieldId, value = value, createTime = now, updateTime = now) })
    }
    override suspend fun getFieldValueByContactAndKey(contactId: Long, fieldKey: String): String? = withContext(Dispatchers.IO) {
        val field = contactFieldCacheDao.getFieldByKey(fieldKey) ?: return@withContext null
        contactFieldValueCacheDao.getFieldValue(contactId, field.id)
    }
    override suspend fun updateFieldValueByKey(contactId: Long, fieldKey: String, newValue: String) = withContext(Dispatchers.IO) {
        val field = contactFieldCacheDao.getFieldByKey(fieldKey) ?: run { Log.w(TAG, "updateFieldValueByKey: ContactField key='$fieldKey' not found, skip"); return@withContext }
        val now = System.currentTimeMillis()
        val existing = contactFieldValueCacheDao.getFieldValueEntity(contactId, field.id)
        val updated = existing?.copy(value = newValue, updateTime = now) ?: ContactFieldValueCacheEntity(contactId = contactId, fieldId = field.id, value = newValue, createTime = now, updateTime = now)
        contactFieldValueCacheDao.insertOrUpdateFieldValues(listOf(updated))
    }
    override suspend fun getCustomFieldValueByContactAndFieldId(contactId: Long, customFieldId: Long): String? = withContext(Dispatchers.IO) { contactFieldValueCacheDao.getCustomFieldValue(contactId, customFieldId) }
    override suspend fun getFieldValueMapByContact(contactId: Long): Map<String, String> = withContext(Dispatchers.IO) {
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
