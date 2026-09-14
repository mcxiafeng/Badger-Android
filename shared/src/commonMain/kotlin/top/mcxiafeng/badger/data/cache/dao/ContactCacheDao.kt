package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.model.LetterCount

@Dao
interface ContactCacheDao {

    @Query("SELECT * FROM contacts_cache WHERE isDeleted = 0 ORDER BY pinyinInitial ASC, name ASC")
    fun getAllContacts(): Flow<List<ContactCacheEntity>>

    @Query("SELECT COUNT(*) FROM contacts_cache WHERE isDeleted = 0")
    fun observeRowCount(): Flow<Int>

    
    @Query("UPDATE contacts_cache SET updateTime = updateTime WHERE id = :id")
    suspend fun bumpContact(id: Long)

    @Query("SELECT * FROM contacts_cache WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: Long): ContactCacheEntity?

    
    @Query("SELECT * FROM contacts_cache WHERE serverId = :serverId LIMIT 1")
    suspend fun getContactByServerId(serverId: String): ContactCacheEntity?

    
    @Query("SELECT * FROM contacts_cache WHERE serverId IN (:serverIds)")
    suspend fun getContactsByServerIds(serverIds: List<String>): List<ContactCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactCacheEntity): Long

    @Update
    suspend fun updateContact(contact: ContactCacheEntity)

    

    @Query("UPDATE contacts_cache SET isDeleted = :deleted, updateTime = :now WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean, now: Long)

    @Query("DELETE FROM contacts_cache WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    

    @Query("DELETE FROM contacts_cache WHERE id = :id")
    suspend fun deleteById(id: Long)

    
    @Query("""
        SELECT DISTINCT cc.* FROM contacts_cache cc
        INNER JOIN contact_tag_cache ct ON cc.id = ct.contactId
        WHERE ct.tagId = :tagId AND cc.isDeleted = 0
        ORDER BY cc.pinyinInitial ASC, cc.name ASC
    """)
    suspend fun getContactsByTag(tagId: Long): List<ContactCacheEntity>

    

    @Query("""
        SELECT * FROM contacts_cache
        WHERE isDeleted = 0 AND name LIKE '%' || :query || '%'
        ORDER BY pinyinInitial ASC, name ASC
    """)
    fun searchContacts(query: String): Flow<List<ContactCacheEntity>>

    
    @Query("SELECT * FROM contacts_cache WHERE isDeleted = 0 AND LOWER(name) = LOWER(:name)")
    suspend fun getContactsByName(name: String): List<ContactCacheEntity>

    @Query("SELECT * FROM contacts_cache WHERE isDeleted = 0 AND LOWER(name) LIKE LOWER(:prefix) || '%' ORDER BY name ASC LIMIT 20")
    fun searchContactsByName(prefix: String): Flow<List<ContactCacheEntity>>

    @Query("""
        SELECT pinyinInitial AS letter, COUNT(*) AS count
        FROM contacts_cache
        WHERE isDeleted = 0 AND pinyinInitial != ''
        GROUP BY pinyinInitial
        ORDER BY pinyinInitial ASC
    """)
    fun getLetterIndex(): Flow<List<LetterCount>>

    

    @Query("""
        SELECT DISTINCT cc.* FROM contacts_cache cc
        INNER JOIN collection_member_cache cm ON cc.id = cm.contactId
        WHERE cm.collectionId = :collectionId AND cc.isDeleted = 0
        ORDER BY cc.pinyinInitial ASC, cc.name ASC
    """)
    fun getContactsByCollection(collectionId: Long): Flow<List<ContactCacheEntity>>

    

    @Query("""
        SELECT DISTINCT cc.* FROM contacts_cache cc
        INNER JOIN collection_member_cache cm ON cc.id = cm.contactId
        WHERE cm.collectionId = :collectionId AND cc.isDeleted = 0
        ORDER BY cc.pinyinInitial ASC, cc.name ASC
    """)
    suspend fun getContactsByCollectionOnce(collectionId: Long): List<ContactCacheEntity>

    

    @Query("""
        SELECT DISTINCT cc.* FROM contacts_cache cc
        WHERE cc.isDeleted = 0 AND cc.id != COALESCE(:excludeId, -1)
          AND (
            cc.name LIKE '%' || :keyword || '%'
            OR cc.id IN (SELECT contactId FROM contact_field_values_cache WHERE value LIKE '%' || :keyword || '%')
            OR cc.id IN (SELECT contactId FROM contact_platforms_cache WHERE value LIKE '%' || :keyword || '%')
          )
        ORDER BY cc.pinyinInitial ASC, cc.name ASC
        LIMIT 5
    """)
    suspend fun findPotentialDuplicates(keyword: String, excludeId: Long?): List<ContactCacheEntity>

    

    @Query("SELECT * FROM contacts_cache WHERE isLocalOnly = 1 AND isDeleted = 0 ORDER BY id ASC")
    suspend fun getLocalOnlyContactsOnce(): List<ContactCacheEntity>

    
    @Query("SELECT COUNT(*) FROM contacts_cache WHERE isLocalOnly = 1 AND isDeleted = 0")
    suspend fun countLocalOnly(): Int

    
    @Query("SELECT * FROM contacts_cache WHERE isDeleted = 0 ORDER BY createTime DESC LIMIT :limit")
    suspend fun getRecentContacts(limit: Int = 10): List<ContactCacheEntity>

    
    @Query("DELETE FROM contacts_cache")
    suspend fun clearAll()
}