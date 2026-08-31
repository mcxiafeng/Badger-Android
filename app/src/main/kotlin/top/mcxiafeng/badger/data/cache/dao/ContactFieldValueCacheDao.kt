package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity

@Dao
interface ContactFieldValueCacheDao {

    @Query("SELECT * FROM contact_field_values_cache WHERE contactId = :contactId")
    suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValueCacheEntity>

    @Query("SELECT * FROM contact_field_values_cache WHERE contactId = :contactId")
    fun getFieldValuesByContact(contactId: Long): Flow<List<ContactFieldValueCacheEntity>>

    @Upsert
    suspend fun insertOrUpdateFieldValues(values: List<ContactFieldValueCacheEntity>)

    @Query("DELETE FROM contact_field_values_cache WHERE contactId = :contactId")
    suspend fun deleteByContact(contactId: Long)

    @Query("SELECT * FROM contact_field_values_cache WHERE contactId = :contactId AND fieldId = :fieldId LIMIT 1")
    suspend fun getFieldValueEntity(contactId: Long, fieldId: Long): ContactFieldValueCacheEntity?

    @Query("SELECT value FROM contact_field_values_cache WHERE contactId = :contactId AND fieldId = :fieldId LIMIT 1")
    suspend fun getFieldValue(contactId: Long, fieldId: Long): String?

    @Query("SELECT value FROM contact_field_values_cache WHERE contactId = :contactId AND customFieldId = :customFieldId LIMIT 1")
    suspend fun getCustomFieldValue(contactId: Long, customFieldId: Long): String?

    @Query("SELECT DISTINCT contactId FROM contact_field_values_cache WHERE customFieldId = :customFieldId AND value = :value")
    suspend fun findContactIdsByCustomFieldValue(customFieldId: Long, value: String): List<Long>
}
