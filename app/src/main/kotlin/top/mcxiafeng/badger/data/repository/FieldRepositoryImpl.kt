package top.mcxiafeng.badger.data.repository

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.data.ContactFieldDao
import top.mcxiafeng.badger.data.ContactFieldValue
import top.mcxiafeng.badger.data.ContactFieldValueDao
import top.mcxiafeng.badger.data.ContactPlatformDao
import top.mcxiafeng.badger.data.CustomField
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.SyncStateEntity
import top.mcxiafeng.badger.data.account.AccountWriteLocks
import javax.inject.Inject

class FieldRepositoryImpl @Inject constructor(
    private val contactFieldDao: ContactFieldDao,
    private val customFieldDao: CustomFieldDao,
    private val contactFieldValueDao: ContactFieldValueDao,
    private val contactPlatformDao: ContactPlatformDao,
    private val db: AppDatabase,
) : FieldRepository {
    private val contactDao = db.contactDao()
    private val contactMutex = AccountWriteLocks.forDao(contactDao)

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

    override suspend fun updateFieldValueByKey(
        contactId: Long,
        fieldKey: String,
        newValue: String,
    ) {
        withContext(Dispatchers.IO) {
            contactMutex.withLock { writeFieldValue(contactId, fieldKey, newValue) }
        }
    }

    override suspend fun getContactRegionPath(contactId: Long): List<String> = db.withTransaction {
        val field = currentFieldValue(contactId, "region") ?: return@withTransaction emptyList()
        val saved = db.syncStateDao().get(regionPathKey(contactId)) ?: return@withTransaction emptyList()
        val stamp = "${field.id}:${field.updateTime}\n"
        if (saved.startsWith(stamp)) saved.removePrefix(stamp).lines() else emptyList()
    }

    override suspend fun updateContactRegion(contactId: Long, country: String?, name: String, path: List<String>) {
        contactMutex.withLock {
            db.withTransaction {
                val currentCountry = getFieldValueByContactAndKey(contactId, "country")?.trim()?.ifEmpty { null }
                check(country == currentCountry) { "保存期间国家已变化" }
                val field = writeFieldValue(contactId, "region", name)
                val key = regionPathKey(contactId)
                if (path.isEmpty()) {
                    db.syncStateDao().delete(key)
                } else {
                    val value = "${field.id}:${field.updateTime}\n" + path.joinToString("\n")
                    db.syncStateDao().put(SyncStateEntity(key, value))
                }
                contactDao.bumpContact(contactId)
            }
            Log.d("FieldRepositoryImplTester", "地区已保存，区划路径层级=${path.size}")
        }
    }

    // 区划路径是本机元数据，按联系人所在库保存，并随字段版本失效。
    private fun regionPathKey(contactId: Long) = "local/region-path/$contactId"

    override suspend fun updateContactCountry(contactId: Long, country: String) {
        contactMutex.withLock {
            db.withTransaction {
                val previous = getFieldValueByContactAndKey(contactId, "country")?.trim()
                if (previous == country) return@withTransaction
                writeFieldValue(contactId, "country", country)
                val clearRegion = !previous.isNullOrEmpty() &&
                    !getFieldValueByContactAndKey(contactId, "region").isNullOrEmpty()
                if (clearRegion) writeFieldValue(contactId, "region", "")
                db.syncStateDao().delete(regionPathKey(contactId))
                contactDao.bumpContact(contactId)
                Log.d("FieldRepositoryImplTester", "国家已更新，清除旧地区=$clearRegion")
            }
        }
    }

    private suspend fun currentFieldValue(contactId: Long, fieldKey: String): ContactFieldValue? {
        val fieldId = contactFieldDao.getFieldByKey(fieldKey)?.id ?: return null
        return contactFieldValueDao.getFieldValuesByContactOnce(contactId).firstOrNull { it.fieldId == fieldId }
    }

    private suspend fun writeFieldValue(contactId: Long, fieldKey: String, newValue: String): ContactFieldValue {
        val field = contactFieldDao.getFieldByKey(fieldKey)
            ?: error("ContactField with key='$fieldKey' not found. Did seedDefaults run?")
        val existing = contactFieldValueDao.getFieldValuesByContactOnce(contactId)
            .firstOrNull { it.fieldId == field.id }
        if (existing != null) {
            val updated = existing.copy(value = newValue, updateTime = System.currentTimeMillis())
            contactFieldValueDao.updateFieldValue(updated)
            return updated
        }
        val created = ContactFieldValue(contactId = contactId, fieldId = field.id, value = newValue)
        return created.copy(id = contactFieldValueDao.insertFieldValue(created))
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
        // 平台字段（qq/wechat/...）存在 contact_platforms 表里
        // 之前这里只查了 system fields，导致平台级"重复/冲突"检测漏识别
        val platforms = contactPlatformDao.getPlatformsByContact(contactId)
        for (platform in platforms) {
            val pk = platform.platformKey
            val pv = platform.value
            if (pk.isNotBlank() && pv != null && pk !in map) {
                map[pk] = pv
            }
        }
        map
    }
}
