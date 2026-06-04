package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.data.ContactFieldDao
import top.mcxiafeng.badger.data.ContactFieldValue
import top.mcxiafeng.badger.data.ContactFieldValueDao
import top.mcxiafeng.badger.data.CustomField
import top.mcxiafeng.badger.data.CustomFieldDao
import javax.inject.Inject

class FieldRepositoryImpl @Inject constructor(
    private val contactFieldDao: ContactFieldDao,
    private val customFieldDao: CustomFieldDao,
    private val contactFieldValueDao: ContactFieldValueDao
) : FieldRepository {

    // ========== 系统预置字段操作 ==========

    override fun getAllEnabledFields(): Flow<List<ContactField>> = contactFieldDao.getAllEnabledFields()

    override suspend fun getAllFieldsOnce(): List<ContactField> = withContext(Dispatchers.IO) {
        contactFieldDao.getAllFieldsOnce()
    }

    override suspend fun getFieldByKey(key: String): ContactField? = withContext(Dispatchers.IO) {
        contactFieldDao.getFieldByKey(key)
    }

    override suspend fun getFieldById(id: Long): ContactField? = withContext(Dispatchers.IO) {
        contactFieldDao.getFieldById(id)
    }

    override suspend fun insertField(field: ContactField): Long = withContext(Dispatchers.IO) {
        contactFieldDao.insertField(field)
    }

    override suspend fun updateField(field: ContactField) = withContext(Dispatchers.IO) {
        contactFieldDao.updateField(field)
    }

    override suspend fun deleteField(field: ContactField) = withContext(Dispatchers.IO) {
        if (!field.isSystem) {
            contactFieldDao.deleteField(field)
        }
    }

    override suspend fun setFieldEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        contactFieldDao.setFieldEnabled(id, enabled)
    }

    override suspend fun updateFieldOrder(id: Long, order: Int) = withContext(Dispatchers.IO) {
        contactFieldDao.updateFieldOrder(id, order)
    }

    // ========== 自定义字段操作 ==========

    override fun getAllEnabledCustomFields(): Flow<List<CustomField>> = customFieldDao.getAllEnabledCustomFields()

    override suspend fun getCustomFieldById(id: Long): CustomField? = withContext(Dispatchers.IO) {
        customFieldDao.getCustomFieldById(id)
    }

    override suspend fun insertCustomField(field: CustomField): Long = withContext(Dispatchers.IO) {
        customFieldDao.insertCustomField(field)
    }

    override suspend fun updateCustomField(field: CustomField) = withContext(Dispatchers.IO) {
        customFieldDao.updateCustomField(field)
    }

    override suspend fun deleteCustomField(field: CustomField) = withContext(Dispatchers.IO) {
        customFieldDao.deleteCustomField(field)
    }

    override suspend fun setCustomFieldEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        customFieldDao.setCustomFieldEnabled(id, enabled)
    }

    override suspend fun updateCustomFieldOrder(id: Long, order: Int) = withContext(Dispatchers.IO) {
        customFieldDao.updateCustomFieldOrder(id, order)
    }

    // ========== 字段值操作 ==========

    override suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValue> = withContext(Dispatchers.IO) {
        contactFieldValueDao.getFieldValuesByContactOnce(contactId)
    }

    override suspend fun insertFieldValue(value: ContactFieldValue): Long = withContext(Dispatchers.IO) {
        contactFieldValueDao.insertFieldValue(value)
    }

    override suspend fun updateFieldValue(value: ContactFieldValue) = withContext(Dispatchers.IO) {
        contactFieldValueDao.updateFieldValue(value)
    }

    override suspend fun deleteFieldValue(value: ContactFieldValue) = withContext(Dispatchers.IO) {
        contactFieldValueDao.deleteFieldValue(value)
    }

    override suspend fun saveContactFieldValues(contactId: Long, fieldValues: Map<Long, String>) = withContext(Dispatchers.IO) {
        val values = fieldValues.map { (fieldId, value) ->
            ContactFieldValue(contactId = contactId, fieldId = fieldId, value = value)
        }
        contactFieldValueDao.insertOrUpdateFieldValues(values)
    }

    override suspend fun saveContactFieldValues(contactId: Long, fieldValues: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        val values = fieldValues.map { (fieldId, value) ->
            ContactFieldValue(contactId = contactId, fieldId = fieldId, value = value)
        }
        contactFieldValueDao.insertOrUpdateFieldValues(values)
    }

    override suspend fun saveContactCustomFieldValues(contactId: Long, fieldValues: Map<Long, String>) = withContext(Dispatchers.IO) {
        val values = fieldValues.map { (customFieldId, value) ->
            ContactFieldValue(contactId = contactId, customFieldId = customFieldId, value = value)
        }
        contactFieldValueDao.insertOrUpdateFieldValues(values)
    }

    override suspend fun getFieldValueByContactAndKey(contactId: Long, fieldKey: String): String? = withContext(Dispatchers.IO) {
        val field = contactFieldDao.getFieldByKey(fieldKey) ?: return@withContext null
        contactFieldValueDao.getFieldValue(contactId, field.id)
    }

    override suspend fun getCustomFieldValueByContactAndFieldId(contactId: Long, customFieldId: Long): String? = withContext(Dispatchers.IO) {
        contactFieldValueDao.getCustomFieldValue(contactId, customFieldId)
    }

    override suspend fun getFieldValueMapByContact(contactId: Long): Map<String, String> = withContext(Dispatchers.IO) {
        val fieldValues = contactFieldValueDao.getFieldValuesByContactOnce(contactId)
        val map = mutableMapOf<String, String>()
        for (fv in fieldValues) {
            val key = when {
                fv.fieldId != null -> contactFieldDao.getFieldById(fv.fieldId)?.fieldKey
                fv.customFieldId != null -> "custom_${fv.customFieldId}"
                else -> null
            }
            if (key != null && key !in map) map[key] = fv.value
        }
        map
    }
}
