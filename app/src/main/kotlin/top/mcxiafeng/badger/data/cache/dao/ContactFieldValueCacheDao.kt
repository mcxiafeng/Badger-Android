package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity

/**
 * V2 联系人字段值 DAO（对应表 `contact_field_values_cache`）。
 *
 * 与 V1 [top.mcxiafeng.badger.data.ContactFieldValueDao] 1:1 对应。
 */
@Dao
interface ContactFieldValueCacheDao {

    @Query("SELECT * FROM contact_field_values_cache WHERE contactId = :contactId")
    suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValueCacheEntity>

    @Query("SELECT * FROM contact_field_values_cache WHERE contactId = :contactId")
    fun getFieldValuesByContact(contactId: Long): Flow<List<ContactFieldValueCacheEntity>>

    @Insert
    suspend fun insertFieldValue(value: ContactFieldValueCacheEntity): Long

    @Update
    suspend fun updateFieldValue(value: ContactFieldValueCacheEntity)

    @Upsert
    suspend fun insertOrUpdateFieldValues(values: List<ContactFieldValueCacheEntity>)

    @Query("DELETE FROM contact_field_values_cache WHERE contactId = :contactId")
    suspend fun deleteByContact(contactId: Long)

    @Query("SELECT value FROM contact_field_values_cache WHERE contactId = :contactId AND fieldId = :fieldId LIMIT 1")
    suspend fun getFieldValue(contactId: Long, fieldId: Long): String?

    @Query("SELECT value FROM contact_field_values_cache WHERE contactId = :contactId AND customFieldId = :customFieldId LIMIT 1")
    suspend fun getCustomFieldValue(contactId: Long, customFieldId: Long): String?
}
