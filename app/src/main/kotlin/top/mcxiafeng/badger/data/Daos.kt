package top.mcxiafeng.badger.data

import androidx.room.*
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

/**
 * 联系人数据访问对象
 *
 * 提供联系人的增删改查操作，支持按姓名和字段值搜索。
 */
@Dao
interface ContactDao {
    /** 获取所有联系人，按姓名升序排列（Flow 实现响应式更新） */
    @Query("SELECT * FROM contacts ORDER BY pinyinInitial ASC, name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    /** 获取所有联系人，返回 PagingSource（用于 Paging 3 分页加载） */
    @Query("SELECT * FROM contacts ORDER BY pinyinInitial ASC, name ASC")
    fun getAllContactsPagingSource(): PagingSource<Int, Contact>

    /**
     * 强制 PagingSource 失效：清除内存缓存并触发 Room 重新查询。
     * 当外部修改了 contacts 表（不在 @Dao 内）后，可调用本方法让分页源感知变更。
     */
    @Query("SELECT COUNT(*) FROM contacts")
    fun observeRowCount(): Flow<Int>

    /** 触发 PagingSource invalidation：写入一行后再删掉，绕开 Room 的「同事务无效」限制。 */
    @Query("UPDATE contacts SET updateTime = updateTime WHERE id = :id")
    suspend fun bumpContact(id: Long)

    /** 根据 ID 获取单个联系人 */
    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: Long): Contact?

    /** 插入联系人，若主键冲突则覆盖 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    /** 更新联系人信息 */
    @Update
    suspend fun updateContact(contact: Contact)

    /** 删除联系人（级联删除关联的字段值和扫描记录） */
    @Delete
    suspend fun deleteContact(contact: Contact)

    /** 批量删除联系人，由 SQLite 外键 ON DELETE CASCADE 级联清理关联数据 */
    @Query("DELETE FROM contacts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * 模糊搜索联系人
     *
     * 同时搜索联系人姓名和关联的字段值，任一匹配即返回。
     * 使用 DISTINCT 去重（一个联系人可能匹配多个字段值）。
     *
     * 注意：LIKE '%...%' 会导致全表扫描，数据量大时性能下降。
     * 未来可考虑使用 Room FTS (Full-Text Search) 替代。
     *
     * @param query 搜索关键词
     */
    // FALLBACK: FTS4 全文检索无结果时的降级查询路径
    @Query("""
        SELECT DISTINCT c.* FROM contacts c
        LEFT JOIN contact_field_values cfv ON c.id = cfv.contactId
        WHERE c.name LIKE '%' || :query || '%'
        OR cfv.value LIKE '%' || :query || '%'
        ORDER BY c.name ASC
    """)
    fun searchContacts(query: String): Flow<List<Contact>>

    /** LIKE 分页搜索（FTS 查询不可用时的退化路径） */
    // FALLBACK: FTS4 全文检索无结果时的降级查询路径
    @Query("""
        SELECT * FROM contacts
        WHERE name LIKE '%' || :query || '%'
        ORDER BY pinyinInitial ASC, name ASC
    """)
    fun searchContactsByNameLikePagingSource(query: String): PagingSource<Int, Contact>

    /** Exact name match (case-insensitive) */
    @Query("SELECT * FROM contacts WHERE LOWER(name) = LOWER(:name)")
    suspend fun getContactsByName(name: String): List<Contact>

    /** Name starts with prefix (for fuzzy matching) */
    @Query("SELECT * FROM contacts WHERE LOWER(name) LIKE LOWER(:prefix) || '%' ORDER BY name ASC LIMIT 20")
    fun searchContactsByName(prefix: String): Flow<List<Contact>>

    /** 获取指定名片夹下的所有联系人 */
    @Query("""
        SELECT DISTINCT c.* FROM contacts c
        INNER JOIN scan_results sr ON c.id = sr.contactId
        WHERE sr.collectionId = :collectionId
        ORDER BY c.pinyinInitial ASC, c.name ASC
    """)
    fun getContactsByCollection(collectionId: Long): Flow<List<Contact>>

    @Query("""
        SELECT DISTINCT c.* FROM contacts c
        INNER JOIN scan_results sr ON c.id = sr.contactId
        WHERE sr.collectionId = :collectionId
        ORDER BY c.pinyinInitial ASC, c.name ASC
    """)
    fun getContactsByCollectionPagingSource(collectionId: Long): PagingSource<Int, Contact>

    @Query("""
        SELECT DISTINCT c.* FROM contacts c
        INNER JOIN scan_results sr ON c.id = sr.contactId
        WHERE sr.collectionId = :collectionId
        ORDER BY c.pinyinInitial ASC, c.name ASC
    """)
    suspend fun getContactsByCollectionOnce(collectionId: Long): List<Contact>

    @Query("""
        SELECT pinyinInitial AS letter, COUNT(*) AS count
        FROM contacts
        WHERE pinyinInitial != ''
        GROUP BY pinyinInitial
        ORDER BY pinyinInitial ASC
    """)
    fun getLetterIndex(): Flow<List<LetterCount>>
}

/**
 * 系统预置字段的数据访问对象
 *
 * 管理联系人的预置联系方式字段（手机、邮箱、微信等）。
 */
@Dao
interface ContactFieldDao {
    /** 获取所有已启用的字段，按排序权重升序 */
    @Query("SELECT * FROM contact_fields WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getAllEnabledFields(): Flow<List<ContactField>>

    /** 获取所有字段（包括已禁用的） */
    @Query("SELECT * FROM contact_fields ORDER BY sortOrder ASC, id ASC")
    fun getAllFields(): Flow<List<ContactField>>

    /** 获取所有字段（一次性，用于导出） */
    @Query("SELECT * FROM contact_fields ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllFieldsOnce(): List<ContactField>

    /** 根据字段标识键获取字段定义 */
    @Query("SELECT * FROM contact_fields WHERE fieldKey = :key")
    suspend fun getFieldByKey(key: String): ContactField?

    /** 根据 ID 获取字段定义 */
    @Query("SELECT * FROM contact_fields WHERE id = :id")
    suspend fun getFieldById(id: Long): ContactField?

    /** 批量获取字段定义 */
    @Query("SELECT * FROM contact_fields WHERE id IN (:ids)")
    suspend fun getFieldsByIds(ids: List<Long>): List<ContactField>

    /** 插入字段，若主键冲突则覆盖 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: ContactField): Long

    /** 更新字段定义 */
    @Update
    suspend fun updateField(field: ContactField)

    /** 删除字段 */
    @Delete
    suspend fun deleteField(field: ContactField)

    /** 设置字段的启用/禁用状态 */
    @Query("UPDATE contact_fields SET isEnabled = :enabled WHERE id = :id")
    suspend fun setFieldEnabled(id: Long, enabled: Boolean)

    /** 更新字段的排序权重 */
    @Query("UPDATE contact_fields SET sortOrder = :order WHERE id = :id")
    suspend fun updateFieldOrder(id: Long, order: Int)
}

/**
 * 自定义字段的数据访问对象
 *
 * 管理用户自定义的联系方式字段，接口与 [ContactFieldDao] 对称。
 */
@Dao
interface CustomFieldDao {
    /** 获取所有已启用的自定义字段 */
    @Query("SELECT * FROM custom_fields WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getAllEnabledCustomFields(): Flow<List<CustomField>>

    /** 获取所有自定义字段（包括已禁用的） */
    @Query("SELECT * FROM custom_fields ORDER BY sortOrder ASC, id ASC")
    fun getAllCustomFields(): Flow<List<CustomField>>

    /** 根据 ID 获取自定义字段 */
    @Query("SELECT * FROM custom_fields WHERE id = :id")
    suspend fun getCustomFieldById(id: Long): CustomField?

    /** 批量获取自定义字段 */
    @Query("SELECT * FROM custom_fields WHERE id IN (:ids)")
    suspend fun getCustomFieldsByIds(ids: List<Long>): List<CustomField>

    /** 插入自定义字段 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomField(field: CustomField): Long

    /** 更新自定义字段 */
    @Update
    suspend fun updateCustomField(field: CustomField)

    /** 删除自定义字段 */
    @Delete
    suspend fun deleteCustomField(field: CustomField)

    /** 设置自定义字段的启用/禁用状态 */
    @Query("UPDATE custom_fields SET isEnabled = :enabled WHERE id = :id")
    suspend fun setCustomFieldEnabled(id: Long, enabled: Boolean)

    /** 更新自定义字段的排序权重 */
    @Query("UPDATE custom_fields SET sortOrder = :order WHERE id = :id")
    suspend fun updateCustomFieldOrder(id: Long, order: Int)
}

/**
 * 联系人字段值的数据访问对象
 *
 * 管理每个联系人的具体字段值。
 */
@Dao
interface ContactFieldValueDao {
    /** 获取指定联系人的所有字段值（suspend 版本，不返回 Flow，性能更好） */
    @Query("SELECT * FROM contact_field_values WHERE contactId = :contactId")
    suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValue>

    /** 获取指定联系人的所有字段值 */
    @Query("SELECT * FROM contact_field_values WHERE contactId = :contactId")
    fun getFieldValuesByContact(contactId: Long): Flow<List<ContactFieldValue>>

    /** 插入字段值（不覆盖，允许同字段多条记录） */
    @Insert
    suspend fun insertFieldValue(value: ContactFieldValue): Long

    /** 更新字段值 */
    @Update
    suspend fun updateFieldValue(value: ContactFieldValue)

    /** 删除字段值 */
    @Delete
    suspend fun deleteFieldValue(value: ContactFieldValue)

    /** 批量插入字段值（不覆盖，允许同字段多条记录） */
    @Insert
    suspend fun insertOrUpdateFieldValues(values: List<ContactFieldValue>)

    /** 获取指定联系人某个系统字段的值 */
    @Query("SELECT value FROM contact_field_values WHERE contactId = :contactId AND fieldId = :fieldId LIMIT 1")
    suspend fun getFieldValue(contactId: Long, fieldId: Long): String?
    
    /** 获取指定联系人某个自定义字段的值 */
    @Query("SELECT value FROM contact_field_values WHERE contactId = :contactId AND customFieldId = :customFieldId LIMIT 1")
    suspend fun getCustomFieldValue(contactId: Long, customFieldId: Long): String?
}

/**
 * 名片夹及其联系人数量
 *
 * @property collection 名片夹实体
 * @property contactCount 该名片夹下的去重联系人数量
 */
data class CollectionWithCount(
    @Embedded val collection: CardCollection,
    val contactCount: Int
)

/**
 * 名片夹的数据访问对象
 */
@Dao
interface CardCollectionDao {
    /** 获取所有名片夹，按名称升序 */
    @Query("SELECT * FROM card_collections ORDER BY name ASC")
    fun getAllCollections(): Flow<List<CardCollection>>

    @Query("SELECT * FROM card_collections ORDER BY name ASC")
    suspend fun getAllCollectionsOnce(): List<CardCollection>

    /** 获取所有名片夹及其联系人数量 */
    @Query("""
        SELECT cc.*, COUNT(DISTINCT sr.contactId) as contactCount 
        FROM card_collections cc 
        LEFT JOIN scan_results sr ON cc.id = sr.collectionId 
        GROUP BY cc.id 
        ORDER BY cc.name ASC
    """)
    fun getCollectionsWithCount(): Flow<List<CollectionWithCount>>

    /** 根据 ID 获取名片夹 */
    @Query("SELECT * FROM card_collections WHERE id = :id")
    suspend fun getCollectionById(id: Long): CardCollection?

    /** 插入名片夹 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CardCollection): Long

    /** 更新名片夹 */
    @Update
    suspend fun updateCollection(collection: CardCollection)

    /** 删除名片夹（级联删除关联的扫描记录） */
    @Delete
    suspend fun deleteCollection(collection: CardCollection)
}

/**
 * 扫描结果的数据访问对象
 */
@Dao
interface ScanResultDao {
    /** 获取所有扫描记录，按扫描时间降序（最新的在前） */
    @Query("SELECT * FROM scan_results ORDER BY scannedTime DESC")
    fun getAllScanResults(): Flow<List<ScanResult>>

    /** 获取指定联系人的所有扫描记录 */
    @Query("SELECT * FROM scan_results WHERE contactId = :contactId")
    fun getScanResultsByContact(contactId: Long): Flow<List<ScanResult>>

    /**
     * 获取指定联系人所属的所有名片夹 ID（Flow）。
     *
     * 与 [getScanResultsByContact] 的区别：只返回 collectionId 集合，不下载完整 ScanResult 列表。
     * 用于详情页"联系人属于哪些名片夹"场景，减少数据传输。
     */
    @Query("SELECT DISTINCT collectionId FROM scan_results WHERE contactId = :contactId")
    fun getContactCollectionIds(contactId: Long): Flow<List<Long>>

    /** 插入扫描记录 */
    @Insert
    suspend fun insertScanResult(result: ScanResult)

    /** 获取指定联系人在指定名片夹的所有记录（用于多样式展示） */
    @Query("SELECT * FROM scan_results WHERE contactId = :contactId AND collectionId = :collectionId")
    fun getScanResultsByContactAndCollection(contactId: Long, collectionId: Long): Flow<List<ScanResult>>

    /** 检查指定联系人是否已存在于指定名片夹 */
    @Query("SELECT EXISTS(SELECT 1 FROM scan_results WHERE contactId = :contactId AND collectionId = :collectionId)")
    suspend fun existsContactInCollection(contactId: Long, collectionId: Long): Boolean

    /** 删除指定联系人在指定名片夹的所有记录 */
    @Query("DELETE FROM scan_results WHERE contactId = :contactId AND collectionId = :collectionId")
    suspend fun deleteScanResultsByContactAndCollection(contactId: Long, collectionId: Long)

    /** 批量删除指定联系人在指定名片夹的所有记录 */
    @Query("DELETE FROM scan_results WHERE contactId IN (:contactIds) AND collectionId = :collectionId")
    suspend fun deleteScanResultsByContactsAndCollection(contactIds: List<Long>, collectionId: Long)

    /** 更新扫描记录 */
    @Update
    suspend fun updateScanResult(result: ScanResult)

    /**
     * 查找潜在的重复联系人
     *
     * 根据关键词在二维码内容、OCR文本和字段值中进行搜索，
     * 排除指定ID的联系人，最多返回5条结果。
     *
     * @param keyword 搜索关键词（通常是刚扫描到的手机号/邮箱等）
     * @param excludeId 要排除的联系人ID（新增时为 null）
     */
    // 跨3表JOIN，FTS4无法覆盖
    @Query("""
        SELECT c.* FROM contacts c
        INNER JOIN scan_results sr ON c.id = sr.contactId
        INNER JOIN contact_field_values cfv ON c.id = cfv.contactId
        WHERE (sr.qrCodeContent LIKE '%' || :keyword || '%' OR sr.ocrText LIKE '%' || :keyword || '%' OR cfv.value LIKE '%' || :keyword || '%')
        AND c.id != COALESCE(:excludeId, -1)
        GROUP BY c.id
        LIMIT 5
    """)
    suspend fun findPotentialDuplicates(keyword: String, excludeId: Long? = null): List<Contact>

    /**
     * 获取指定名片夹下每个联系人的扫描记录条数。
     *
     * 注：v5 schema 移除了 `ScanResult.styleColor` 后，此方法语义已退化为
     * "该联系人在该名片夹下的扫描记录总数"，UI 仅用于显示重复扫描的徽章数字（>1）。
     */
    @Query("SELECT contactId, COUNT(*) AS scanRecordCount FROM scan_results WHERE collectionId = :collectionId GROUP BY contactId")
    suspend fun getScanRecordCountsByCollection(collectionId: Long): Map<@MapColumn(columnName = "contactId") Long, @MapColumn(columnName = "scanRecordCount") Int>

    }

/**
 * 用户个人资料数据访问对象
 *
 * 管理当前用户的"我的名片"信息，整个应用只有一条记录（id=1）。
 */
@Dao
interface UserProfileDao {
    /** 获取用户资料（单条记录） */
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfile(): Flow<UserProfile?>

    /** 一次性获取用户资料 */
    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileOnce(): UserProfile?

    /** 插入或更新用户资料 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfile)

    /**
     * 触发 user_profile 表变更通知（即便主键与值都没变）。
     * 用于让订阅 user_profile 的 Flow 重新发射，解决"同值覆盖不触发下游"问题。
     */
    @Query("UPDATE user_profile SET updateTime = updateTime WHERE id = 1")
    suspend fun bumpProfile()
}

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

    @Query("""
        SELECT DISTINCT c.* FROM contacts c
        INNER JOIN contact_platforms cp ON c.id = cp.contactId
        WHERE cp.value = :value AND cp.platformKey = :platformKey AND c.id != :excludeId
        LIMIT 5
    """)
    suspend fun findDuplicatesByPlatform(platformKey: String, value: String, excludeId: Long): List<Contact>

    /**
     * 批量查重（QAuxv 导入）：返回 platformKey 指定的所有匹配 value 的平台条目。
     * 调用方自行按 (value → contactId) 聚合。
     */
    @Query("SELECT * FROM contact_platforms WHERE platformKey = :platformKey AND value IN (:values)")
    suspend fun getPlatformsByKeyAndValues(platformKey: String, values: List<String>): List<ContactPlatform>
}

@Dao
interface ContactFtsDao {
    @Query("SELECT c.* FROM contacts c JOIN contacts_fts fts ON c.id = fts.rowid WHERE contacts_fts MATCH :query ORDER BY c.pinyinInitial ASC, c.name ASC")
    fun searchContactsFts(query: String): Flow<List<Contact>>

    @Query("SELECT c.* FROM contacts c JOIN contacts_fts fts ON c.id = fts.rowid WHERE contacts_fts MATCH :query ORDER BY c.pinyinInitial ASC, c.name ASC")
    fun searchContactsFtsPagingSource(query: String): PagingSource<Int, Contact>

    /** FTS 一次性查询，用于去重检测等场景 */
    @Query("SELECT c.* FROM contacts c JOIN contacts_fts fts ON c.id = fts.rowid WHERE contacts_fts MATCH :query ORDER BY c.pinyinInitial ASC, c.name ASC LIMIT :limit")
    suspend fun searchContactsFtsOnce(query: String, limit: Int): List<Contact>

    /** 组合搜索：FTS 前缀匹配 name/note + LIKE 搜索字段值/平台值
     *
     * 使用子查询隔离 FTS 上下文，避免 `MATCH` 在多表 JOIN 上下文中失效
     */
    // FALLBACK: FTS4 全文检索无结果时的降级查询路径
    @Query("""
        SELECT * FROM (
            SELECT c.* FROM contacts c
            INNER JOIN (SELECT rowid FROM contacts_fts WHERE contacts_fts MATCH :ftsQuery) fts_hits ON c.id = fts_hits.rowid
            UNION
            SELECT DISTINCT c.* FROM contacts c
            INNER JOIN contact_field_values cfv ON c.id = cfv.contactId
            WHERE cfv.value LIKE '%' || :likeQuery || '%'
            UNION
            SELECT DISTINCT c.* FROM contacts c
            INNER JOIN contact_platforms cp ON c.id = cp.contactId
            WHERE cp.value LIKE '%' || :likeQuery || '%' OR cp.displayName LIKE '%' || :likeQuery || '%'
        ) ORDER BY pinyinInitial ASC, name ASC
    """)
    fun searchContactsCombinedPagingSource(ftsQuery: String, likeQuery: String): PagingSource<Int, Contact>

    /** 组合搜索 Flow 版本 */
    // FALLBACK: FTS4 全文检索无结果时的降级查询路径
    @Query("""
        SELECT * FROM (
            SELECT c.* FROM contacts c
            INNER JOIN (SELECT rowid FROM contacts_fts WHERE contacts_fts MATCH :ftsQuery) fts_hits ON c.id = fts_hits.rowid
            UNION
            SELECT DISTINCT c.* FROM contacts c
            INNER JOIN contact_field_values cfv ON c.id = cfv.contactId
            WHERE cfv.value LIKE '%' || :likeQuery || '%'
            UNION
            SELECT DISTINCT c.* FROM contacts c
            INNER JOIN contact_platforms cp ON c.id = cp.contactId
            WHERE cp.value LIKE '%' || :likeQuery || '%' OR cp.displayName LIKE '%' || :likeQuery || '%'
        ) ORDER BY pinyinInitial ASC, name ASC
    """)
    fun searchContactsCombined(ftsQuery: String, likeQuery: String): Flow<List<Contact>>
}

/**
 * 标签实体的数据访问对象
 *
 * 标签名通过 unique 索引去重；upsert 行为由 Repository 层负责（先 getTagByName 查再建）。
 */
@Dao
interface TagDao {
    /** 观察所有标签，按 pinyinInitial + 名字排序 */
    @Query("SELECT * FROM tags ORDER BY pinyinInitial ASC, name ASC")
    fun observeAllTags(): Flow<List<Tag>>

    /** 获取所有标签（一次性） */
    @Query("SELECT * FROM tags ORDER BY pinyinInitial ASC, name ASC")
    suspend fun getAllTagsOnce(): List<Tag>

    /** 根据 ID 获取标签 */
    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: Long): Tag?

    /** 根据名字精确获取标签（upsert 时复用查询） */
    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): Tag?

    /** 插入标签（同 name 由 unique 索引拦截，调用方负责先查再建） */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: Tag): Long

    /** 更新标签 */
    @Update
    suspend fun updateTag(tag: Tag)

    /** 重命名标签 */
    @Query("UPDATE tags SET name = :newName, pinyinInitial = :newPinyinInitial WHERE id = :id")
    suspend fun renameTag(id: Long, newName: String, newPinyinInitial: String)

    /**
     * 仅更新 pinyinInitial（name 不变）。
     *
     * 用于 [LegacyTagFixup.runOnce] 一次性补齐 v4→v5 迁移遗留 tag 的拼音首字母。
     */
    @Query("UPDATE tags SET pinyinInitial = :pinyinInitial WHERE id = :id")
    suspend fun updatePinyinInitial(id: Long, pinyinInitial: String)

    /** 设置标签列表项右侧色点显示开关 */
    @Query("UPDATE tags SET showDot = :show WHERE id = :id")
    suspend fun setTagDotVisible(id: Long, show: Boolean)

    /** 按名字模糊搜索标签(LIKE '%...%') */
    @Query("SELECT * FROM tags WHERE name LIKE '%' || :query || '%' ORDER BY pinyinInitial ASC, name ASC LIMIT 30")
    suspend fun searchTagsByName(query: String): List<Tag>

    /** 删除标签（关联行由 FK CASCADE 自动清理） */
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTagById(id: Long)
}

/**
 * 联系人 ↔ 标签 多对多关联的数据访问对象。
 *
 * 真正的「标签-联系人」组合信息通过 [ContactDao.getTagsByContact] 等 JOIN 查询返回 Tag 投影，
 * 见 Repository 层。
 */
@Dao
interface ContactTagDao {
    /** 把指定标签关联到指定联系人（REPLACE 用于幂等：重复关联不报错） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(ref: ContactTagCrossRef)

    /** 批量插入/替换 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<ContactTagCrossRef>)

    /** 移除单个 (contactId, tagId) 关联 */
    @Query("DELETE FROM contact_tag WHERE contactId = :contactId AND tagId = :tagId")
    suspend fun removeCrossRef(contactId: Long, tagId: Long)

    /** 清空某个联系人的所有标签 */
    @Query("DELETE FROM contact_tag WHERE contactId = :contactId")
    suspend fun clearContactTags(contactId: Long)

    /** 获取某联系人的所有标签 ID */
    @Query("SELECT tagId FROM contact_tag WHERE contactId = :contactId")
    suspend fun getTagIdsByContact(contactId: Long): List<Long>

    /** 获取某标签下的所有联系人 ID */
    @Query("SELECT contactId FROM contact_tag WHERE tagId = :tagId")
    suspend fun getContactIdsByTag(tagId: Long): List<Long>

    /** 获取某联系人的所有标签（投影完整 Tag 行） */
    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN contact_tag ct ON ct.tagId = t.id
        WHERE ct.contactId = :contactId
        ORDER BY t.pinyinInitial ASC, t.name ASC
    """)
    fun observeTagsByContact(contactId: Long): Flow<List<Tag>>

    /** 一次性获取某联系人的所有标签 */
    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN contact_tag ct ON ct.tagId = t.id
        WHERE ct.contactId = :contactId
        ORDER BY t.pinyinInitial ASC, t.name ASC
    """)
    suspend fun getTagsByContactOnce(contactId: Long): List<Tag>

    /** Flow 批量查询：传入 contactIds 列表，返回扁平 join 行列表。
     *  用于 PersonPage 列表一次性拿所有联系人的标签（避免 N+1）。
     *  投影列严格按 [ContactTagJoin] 字段顺序对齐，方便 Room 按列名匹配。
     */
    @Query("""
        SELECT ct.contactId AS contactId, t.id AS id, t.name AS name, t.color AS color,
               t.pinyinInitial AS pinyinInitial, t.source AS source, t.showDot AS showDot, t.createTime AS createTime
        FROM tags t
        INNER JOIN contact_tag ct ON ct.tagId = t.id
        WHERE ct.contactId IN (:contactIds)
        ORDER BY t.pinyinInitial ASC, t.name ASC
    """)
    fun observeTagsForContacts(contactIds: List<Long>): Flow<List<ContactTagJoin>>

    /** 一次性批量拉取：同样按 [ContactTagJoin] 投影 */
    @Query("""
        SELECT ct.contactId AS contactId, t.id AS id, t.name AS name, t.color AS color,
               t.pinyinInitial AS pinyinInitial, t.source AS source, t.showDot AS showDot, t.createTime AS createTime
        FROM tags t
        INNER JOIN contact_tag ct ON ct.tagId = t.id
        WHERE ct.contactId IN (:contactIds)
        ORDER BY t.pinyinInitial ASC, t.name ASC
    """)
    suspend fun getTagsForContactsOnce(contactIds: List<Long>): List<ContactTagJoin>

    /** 批量拉取关联行(含 source/confidence/createTime),用于导出 / AI 事务组装 */
    @Query("SELECT * FROM contact_tag WHERE contactId IN (:contactIds)")
    suspend fun getCrossRefsForContacts(contactIds: List<Long>): List<ContactTagCrossRef>

    /** 按联系人清空某种来源的关联（如"清空某联系人的所有 AI 标签"） */
    @Query("DELETE FROM contact_tag WHERE contactId = :contactId AND source = :source")
    suspend fun clearCrossRefsBySource(contactId: Long, source: String)
}

/**
 * 标签全文索引 DAO（FTS4 via [TagFts]）。
 *
 * 搜索路径:UI 层先调 [searchTagsFtsLike](LIKE 兜底),FTS 命中为空时退化。
 * 生产环境中 tag 表通常 < 200 行,是否走 FTS 由调用方决定。
 */
@Dao
interface TagFtsDao {
    /** FTS4 MATCH 子句前缀匹配 name/pinyinInitial */
    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN tags_fts fts ON t.id = fts.rowid
        WHERE tags_fts MATCH :ftsQuery
        ORDER BY t.pinyinInitial ASC, t.name ASC
        LIMIT :limit
    """)
    suspend fun searchTagsFts(ftsQuery: String, limit: Int = 30): List<Tag>

    /** 单 tag 投影,FTS JOIN 不能直接 SELECT t.* 后再用——此处显式指定每列 */
    @Query("""
        SELECT t.id AS id, t.name AS name, t.color AS color, t.pinyinInitial AS pinyinInitial,
               t.source AS source, t.showDot AS showDot, t.createTime AS createTime
        FROM tags t
        INNER JOIN tags_fts fts ON t.id = fts.rowid
        WHERE tags_fts MATCH :ftsQuery
        ORDER BY t.pinyinInitial ASC, t.name ASC
        LIMIT :limit
    """)
    suspend fun searchTagsFtsProjected(ftsQuery: String, limit: Int = 30): List<TagRow>
}

/** FTS JOIN 投影行,字段顺序与 [Tag] 一一对应,方便手动映射 */
data class TagRow(
    val id: Long,
    val name: String,
    val color: Long,
    val pinyinInitial: String,
    val source: String,
    val showDot: Boolean,
    val createTime: Long
)
