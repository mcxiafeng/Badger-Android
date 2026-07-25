package top.mcxiafeng.badger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 系统预置字段的数据访问对象。
 *
 * V2 协议未涉及系统字段,V1 表保留,DAO 接口稳定不变。
 */
@Dao
interface ContactFieldDao {
    @Query("SELECT * FROM contact_fields WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getAllEnabledFields(): Flow<List<ContactField>>

    @Query("SELECT * FROM contact_fields ORDER BY sortOrder ASC, id ASC")
    fun getAllFields(): Flow<List<ContactField>>

    @Query("SELECT * FROM contact_fields ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllFieldsOnce(): List<ContactField>

    @Query("SELECT * FROM contact_fields WHERE fieldKey = :key")
    suspend fun getFieldByKey(key: String): ContactField?

    @Query("SELECT * FROM contact_fields WHERE id = :id")
    suspend fun getFieldById(id: Long): ContactField?

    @Query("SELECT * FROM contact_fields WHERE id IN (:ids)")
    suspend fun getFieldsByIds(ids: List<Long>): List<ContactField>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: ContactField): Long

    @Update
    suspend fun updateField(field: ContactField)

    @Delete
    suspend fun deleteField(field: ContactField)

    @Query("UPDATE contact_fields SET isEnabled = :enabled WHERE id = :id")
    suspend fun setFieldEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE contact_fields SET sortOrder = :order WHERE id = :id")
    suspend fun updateFieldOrder(id: Long, order: Int)
}

/**
 * 自定义字段的数据访问对象。
 *
 * V1 表保留,DAO 接口稳定不变。
 */
@Dao
interface CustomFieldDao {
    @Query("SELECT * FROM custom_fields WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getAllEnabledCustomFields(): Flow<List<CustomField>>

    @Query("SELECT * FROM custom_fields ORDER BY sortOrder ASC, id ASC")
    fun getAllCustomFields(): Flow<List<CustomField>>

    @Query("SELECT * FROM custom_fields WHERE id = :id")
    suspend fun getCustomFieldById(id: Long): CustomField?

    @Query("SELECT * FROM custom_fields WHERE id IN (:ids)")
    suspend fun getCustomFieldsByIds(ids: List<Long>): List<CustomField>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomField(field: CustomField): Long

    @Update
    suspend fun updateCustomField(field: CustomField)

    @Delete
    suspend fun deleteCustomField(field: CustomField)

    @Query("UPDATE custom_fields SET isEnabled = :enabled WHERE id = :id")
    suspend fun setCustomFieldEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE custom_fields SET sortOrder = :order WHERE id = :id")
    suspend fun updateCustomFieldOrder(id: Long, order: Int)
}

/**
 * 联系人字段值的数据访问对象。
 *
 * V1 表保留,DAO 接口稳定不变。
 */
@Dao
interface ContactFieldValueDao {
    @Query("SELECT * FROM contact_field_values WHERE contactId = :contactId")
    suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValue>

    @Query("SELECT * FROM contact_field_values WHERE contactId = :contactId")
    fun getFieldValuesByContact(contactId: Long): Flow<List<ContactFieldValue>>

    @Insert
    suspend fun insertFieldValue(value: ContactFieldValue): Long

    @Update
    suspend fun updateFieldValue(value: ContactFieldValue)

    @Delete
    suspend fun deleteFieldValue(value: ContactFieldValue)

    @Insert
    suspend fun insertOrUpdateFieldValues(values: List<ContactFieldValue>)

    @Query("SELECT value FROM contact_field_values WHERE contactId = :contactId AND fieldId = :fieldId LIMIT 1")
    suspend fun getFieldValue(contactId: Long, fieldId: Long): String?

    @Query("SELECT value FROM contact_field_values WHERE contactId = :contactId AND customFieldId = :customFieldId LIMIT 1")
    suspend fun getCustomFieldValue(contactId: Long, customFieldId: Long): String?
}

/**
 * 扫描结果的数据访问对象(V2 协议保留作"扫码历史")。
 *
 * V1 表 + DAO 接口稳定不变。
 */
@Dao
interface ScanResultDao {
    @Query("SELECT * FROM scan_results ORDER BY scannedTime DESC")
    fun getAllScanResults(): Flow<List<ScanResult>>

    @Query("SELECT * FROM scan_results WHERE contactId = :contactId")
    fun getScanResultsByContact(contactId: Long): Flow<List<ScanResult>>

    @Query("SELECT DISTINCT collectionId FROM scan_results WHERE contactId = :contactId")
    fun getContactCollectionIds(contactId: Long): Flow<List<Long>>

    @Insert
    suspend fun insertScanResult(result: ScanResult)

    @Query("SELECT * FROM scan_results WHERE contactId = :contactId AND collectionId = :collectionId")
    fun getScanResultsByContactAndCollection(contactId: Long, collectionId: Long): Flow<List<ScanResult>>

    @Query("SELECT EXISTS(SELECT 1 FROM scan_results WHERE contactId = :contactId AND collectionId = :collectionId)")
    suspend fun existsContactInCollection(contactId: Long, collectionId: Long): Boolean

    @Query("DELETE FROM scan_results WHERE contactId = :contactId AND collectionId = :collectionId")
    suspend fun deleteScanResultsByContactAndCollection(contactId: Long, collectionId: Long)

    @Query("DELETE FROM scan_results WHERE contactId IN (:contactIds) AND collectionId = :collectionId")
    suspend fun deleteScanResultsByContactsAndCollection(contactIds: List<Long>, collectionId: Long)

    @Update
    suspend fun updateScanResult(result: ScanResult)

    /**
     * 按名片夹统计每个联系人的扫码次数(用于"同一联系人多次扫描"徽章)。
     */
    @Query("SELECT contactId, COUNT(*) AS scanRecordCount FROM scan_results WHERE collectionId = :collectionId GROUP BY contactId")
    suspend fun getScanRecordCountsByCollection(collectionId: Long): Map<@MapColumn(columnName = "contactId") Long, @MapColumn(columnName = "scanRecordCount") Int>
}

/**
 * V1 `contact_platforms` 表 DAO(平台兼容垫)。
 *
 * A3 决策:V1 表保留,作兼容垫;主路径走 V2 `contact_platforms_cache`。
 * 此 DAO 仅供 CollectionRepository 跨表查询(联系名片夹关联)使用。
 */
@Dao
interface ContactPlatformDao {
    @Query("SELECT * FROM contact_platforms WHERE contactId = :contactId")
    suspend fun getPlatformsByContact(contactId: Long): List<ContactPlatform>

    @Query("SELECT * FROM contact_platforms WHERE contactId = :contactId")
    fun observePlatformsByContact(contactId: Long): Flow<List<ContactPlatform>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlatform(platform: ContactPlatform): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlatforms(platforms: List<ContactPlatform>)

    @Delete
    suspend fun deletePlatform(platform: ContactPlatform)

    @Query("DELETE FROM contact_platforms WHERE contactId = :contactId AND platformKey = :platformKey")
    suspend fun deleteByContactAndKey(contactId: Long, platformKey: String)

    @Query("SELECT * FROM contact_platforms WHERE contactId IN (:contactIds)")
    suspend fun getPlatformsByContacts(contactIds: List<Long>): List<ContactPlatform>

    @Query("SELECT * FROM contact_platforms")
    suspend fun getAllPlatforms(): List<ContactPlatform>

    @Query("SELECT * FROM contact_platforms WHERE platformKey = :platformKey AND value IN (:values)")
    suspend fun getPlatformsByKeyAndValues(platformKey: String, values: List<String>): List<ContactPlatform>
}