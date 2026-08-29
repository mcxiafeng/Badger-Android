package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.LetterCount

/**
 * V2 联系人缓存表 DAO(对应表 `contacts_cache`)。
 *
 * 对应规约:[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2
 */
@Dao
interface ContactCacheDao {

    @Query("SELECT * FROM contacts_cache WHERE isDeleted = 0 ORDER BY pinyinInitial ASC, name ASC")
    fun getAllContacts(): Flow<List<ContactCacheEntity>>

    @Query("SELECT COUNT(*) FROM contacts_cache WHERE isDeleted = 0")
    fun observeRowCount(): Flow<Int>

    /** 触发 PagingSource/Flow 重发(同值覆盖也重发) */
    @Query("UPDATE contacts_cache SET updateTime = updateTime WHERE id = :id")
    suspend fun bumpContact(id: Long)

    @Query("SELECT * FROM contacts_cache WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: Long): ContactCacheEntity?

    /** [Phase 3] 按服务端 uuid 查本地行（sync 重放定位）。 */
    @Query("SELECT * FROM contacts_cache WHERE serverId = :serverId LIMIT 1")
    suspend fun getContactByServerId(serverId: String): ContactCacheEntity?

    /** [Phase 3] 批量按服务端 uuid 查本地行（tag/collection personMembers 映射）。 */
    @Query("SELECT * FROM contacts_cache WHERE serverId IN (:serverIds)")
    suspend fun getContactsByServerIds(serverIds: List<String>): List<ContactCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactCacheEntity): Long

    @Update
    suspend fun updateContact(contact: ContactCacheEntity)

    /**
     * [V2-P6] 软删除标记(关键操作 commitDelete 双通道使用)。
     * [fix]:deleted = true 时所有 getAllContacts / searchContacts / getLetterIndex / getContactsByCollection
     * 都会过滤掉该行(见各 query 的 WHERE isDeleted = 0),UI 立即"消失"。
     * 物理删除留待 Worker / RevertStuckOpWorker / 服务端 200 后做。
     */
    @Query("UPDATE contacts_cache SET isDeleted = :deleted, updateTime = :now WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean, now: Long)

    @Query("DELETE FROM contacts_cache WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * [V2-P6] 物理删除单条(commitDelete 直发 HTTP 200 后 / 30s revert 后已恢复 → 不再需)。
     */
    @Query("DELETE FROM contacts_cache WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 模糊搜索联系人(LIKE 路径，V1 FTS 已在 v15 迁移中删除)。
     */
    @Query("""
        SELECT * FROM contacts_cache
        WHERE isDeleted = 0 AND name LIKE '%' || :query || '%'
        ORDER BY pinyinInitial ASC, name ASC
    """)
    fun searchContacts(query: String): Flow<List<ContactCacheEntity>>

    @Query("SELECT * FROM contacts_cache WHERE LOWER(name) = LOWER(:name)")
    suspend fun getContactsByName(name: String): List<ContactCacheEntity>

    @Query("SELECT * FROM contacts_cache WHERE LOWER(name) LIKE LOWER(:prefix) || '%' ORDER BY name ASC LIMIT 20")
    fun searchContactsByName(prefix: String): Flow<List<ContactCacheEntity>>

    @Query("""
        SELECT pinyinInitial AS letter, COUNT(*) AS count
        FROM contacts_cache
        WHERE isDeleted = 0 AND pinyinInitial != ''
        GROUP BY pinyinInitial
        ORDER BY pinyinInitial ASC
    """)
    fun getLetterIndex(): Flow<List<LetterCount>>

    /**
     * 按名片夹获取联系人 Flow 版(通过 collection_member_cache 中介关联)。
     *
     * [Phase 4 Task #20] 从 `scan_results` 迁移到 `collection_member_cache`。
     */
    @Query("""
        SELECT DISTINCT cc.* FROM contacts_cache cc
        INNER JOIN collection_member_cache cm ON cc.id = cm.contactId
        WHERE cm.collectionId = :collectionId AND cc.isDeleted = 0
        ORDER BY cc.pinyinInitial ASC, cc.name ASC
    """)
    fun getContactsByCollection(collectionId: Long): Flow<List<ContactCacheEntity>>

    /**
     * 按名片夹获取联系人(suspend 版,供 Repository 一次性调用)。
     */
    @Query("""
        SELECT DISTINCT cc.* FROM contacts_cache cc
        INNER JOIN collection_member_cache cm ON cc.id = cm.contactId
        WHERE cm.collectionId = :collectionId AND cc.isDeleted = 0
        ORDER BY cc.pinyinInitial ASC, cc.name ASC
    """)
    suspend fun getContactsByCollectionOnce(collectionId: Long): List<ContactCacheEntity>

    /**
     * V2 跨表查重(对应 V1 ScanResultDao.findPotentialDuplicates)。
     *
     * [A3] V1 跨 `contacts` + `contact_field_values` + `contact_platforms` 三表 JOIN 退化到
     * V2 cache name LIKE + 值 LIKE(走 contacts_cache + contact_field_values_cache + contact_platforms_cache)。
     * 未来 P10 阶段考虑为 contacts_cache 建 FTS5 索引。
     */
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

    /**
     * [V2-P11] 老数据 `isLocalOnly=true` 启动主动 sync 用入口:
     * 一次性扫本地"未与服务端同步"的 cache 行。
     *
     * [修复防御]:加 `isDeleted = 0` 过滤,避免软删除行被误同步:
     * - 软删除已入 PENDING PendingUpload,30s 内 RevertStuckOpWorker 可能恢复 → 走 commitDelete 收尾;
     * - 但已删除的本地行不该触发 P11 覆盖。
     */
    @Query("SELECT * FROM contacts_cache WHERE isLocalOnly = 1 AND isDeleted = 0 ORDER BY id ASC")
    suspend fun getLocalOnlyContactsOnce(): List<ContactCacheEntity>

    /** [Phase 4 Task #21] 统计未同步的本地联系人数（SyncStatusRepository.snapshot 用）。 */
    @Query("SELECT COUNT(*) FROM contacts_cache WHERE isLocalOnly = 1 AND isDeleted = 0")
    suspend fun countLocalOnly(): Int
}