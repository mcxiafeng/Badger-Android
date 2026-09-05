package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.model.ContactField
import top.mcxiafeng.badger.data.model.ContactFieldValue
import top.mcxiafeng.badger.data.model.CustomField

/**
 * 字段数据仓库接口
 *
 * 管理系统预置字段、自定义字段及其值的操作。
 */
interface FieldRepository {

    // ========== 系统预置字段操作 ==========

    fun getAllEnabledFields(): Flow<List<ContactField>>

    suspend fun getAllFieldsOnce(): List<ContactField>

    suspend fun getFieldByKey(key: String): ContactField?

    suspend fun getFieldById(id: Long): ContactField?

    suspend fun insertField(field: ContactField): Long

    suspend fun updateField(field: ContactField)

    suspend fun deleteField(field: ContactField)

    suspend fun setFieldEnabled(id: Long, enabled: Boolean)

    suspend fun updateFieldOrder(id: Long, order: Int)

    // ========== 自定义字段操作 ==========

    fun getAllEnabledCustomFields(): Flow<List<CustomField>>

    suspend fun getCustomFieldById(id: Long): CustomField?

    suspend fun insertCustomField(field: CustomField): Long

    suspend fun updateCustomField(field: CustomField)

    suspend fun deleteCustomField(field: CustomField)

    suspend fun setCustomFieldEnabled(id: Long, enabled: Boolean)

    suspend fun updateCustomFieldOrder(id: Long, order: Int)

    // ========== 字段值操作 ==========

    suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValue>

    suspend fun insertFieldValue(value: ContactFieldValue): Long

    suspend fun updateFieldValue(value: ContactFieldValue)

    suspend fun deleteFieldValue(value: ContactFieldValue)

    suspend fun saveContactFieldValues(contactId: Long, fieldValues: Map<Long, String>)

    suspend fun saveContactFieldValues(contactId: Long, fieldValues: List<Pair<Long, String>>)

    suspend fun saveContactCustomFieldValues(contactId: Long, fieldValues: Map<Long, String>)

    suspend fun getFieldValueByContactAndKey(contactId: Long, fieldKey: String): String?

    /**
     * 按 fieldKey 写入/更新某联系人的字段值。
     *
     * 用于基础信息(性别/生日/国家/地区)这类只有一个值的字段:
     * - 已有值 → update
     * - 没有值 → insert(insertOrUpdate by primary key)
     *
     * 注意:不直接触发 ContactDao.bumpContact。调用方负责调 `repository.bumpContact(id)`
     * 让列表失效(参见 TagRepositoryImpl.addTagToContact 中的模式)。
     */
    suspend fun updateFieldValueByKey(contactId: Long, fieldKey: String, newValue: String)

    suspend fun getCustomFieldValueByContactAndFieldId(contactId: Long, customFieldId: Long): String?

    suspend fun getFieldValueMapByContact(contactId: Long): Map<String, String>
}
