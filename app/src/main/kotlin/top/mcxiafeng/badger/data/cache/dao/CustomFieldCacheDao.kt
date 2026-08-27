package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.CustomFieldCacheEntity

/**
 * V2 自定义字段定义 DAO（对应表 `custom_fields_cache`）。
 *
 * 与 V1 [top.mcxiafeng.badger.data.CustomFieldDao] 1:1 对应。
 */
@Dao
interface CustomFieldCacheDao {

    @Query("SELECT * FROM custom_fields_cache WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getAllEnabledCustomFields(): Flow<List<CustomFieldCacheEntity>>

    @Query("SELECT * FROM custom_fields_cache ORDER BY sortOrder ASC, id ASC")
    fun getAllCustomFields(): Flow<List<CustomFieldCacheEntity>>

    @Query("SELECT * FROM custom_fields_cache WHERE id = :id")
    suspend fun getCustomFieldById(id: Long): CustomFieldCacheEntity?

    @Query("SELECT * FROM custom_fields_cache WHERE id IN (:ids)")
    suspend fun getCustomFieldsByIds(ids: List<Long>): List<CustomFieldCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomField(field: CustomFieldCacheEntity): Long

    @Update
    suspend fun updateCustomField(field: CustomFieldCacheEntity)

    @Query("UPDATE custom_fields_cache SET isEnabled = :enabled WHERE id = :id")
    suspend fun setCustomFieldEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE custom_fields_cache SET sortOrder = :order WHERE id = :id")
    suspend fun updateCustomFieldOrder(id: Long, order: Int)
}
