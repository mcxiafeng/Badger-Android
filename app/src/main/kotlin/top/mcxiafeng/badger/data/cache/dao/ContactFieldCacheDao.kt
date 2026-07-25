package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactFieldCacheEntity

/**
 * V2 系统字段定义 DAO（对应表 `contact_fields_cache`）。
 *
 * 与 V1 [top.mcxiafeng.badger.data.ContactFieldDao] 1:1 对应。
 */
@Dao
interface ContactFieldCacheDao {

    @Query("SELECT * FROM contact_fields_cache WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getAllEnabledFields(): Flow<List<ContactFieldCacheEntity>>

    @Query("SELECT * FROM contact_fields_cache ORDER BY sortOrder ASC, id ASC")
    fun getAllFields(): Flow<List<ContactFieldCacheEntity>>

    @Query("SELECT * FROM contact_fields_cache ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllFieldsOnce(): List<ContactFieldCacheEntity>

    @Query("SELECT * FROM contact_fields_cache WHERE fieldKey = :key LIMIT 1")
    suspend fun getFieldByKey(key: String): ContactFieldCacheEntity?

    @Query("SELECT * FROM contact_fields_cache WHERE id = :id LIMIT 1")
    suspend fun getFieldById(id: Long): ContactFieldCacheEntity?

    @Query("SELECT * FROM contact_fields_cache WHERE id IN (:ids)")
    suspend fun getFieldsByIds(ids: List<Long>): List<ContactFieldCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: ContactFieldCacheEntity): Long

    @Update
    suspend fun updateField(field: ContactFieldCacheEntity)

    @Query("UPDATE contact_fields_cache SET isEnabled = :enabled WHERE id = :id")
    suspend fun setFieldEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE contact_fields_cache SET sortOrder = :order WHERE id = :id")
    suspend fun updateFieldOrder(id: Long, order: Int)
}
