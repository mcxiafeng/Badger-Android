package top.mcxiafeng.badger.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.QAuxvImportProgress
import top.mcxiafeng.badger.data.QAuxvImportSummary
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

/**
 * 联系人数据仓库接口。
 *
 * [A3] 全部输出 V2 cache entity(`ContactCacheEntity` / `ContactPlatform` / `ContactWithFields`)
 * 与共享 `PlatformEntry` JSON shape。
 *
 * 管理联系人基本操作和社交平台操作。
 */
interface ContactRepository {

    // ========== 联系人基本操作 ==========

    fun getAllContacts(): Flow<List<ContactCacheEntity>>

    fun getLetterIndex(): Flow<List<LetterCount>>

    suspend fun getContactById(id: Long): ContactCacheEntity?

    fun getAllContactsWithFields(): Flow<List<ContactWithFields>>

    suspend fun getContactWithFieldsById(id: Long): ContactWithFields?

    suspend fun insertContact(contact: ContactCacheEntity): Long

    suspend fun updateContact(contact: ContactCacheEntity)

    /**
     * 仅更新个人介绍(bio)字段。保留其它字段不变,写入后自动 bumpContact
     * 触发 PagingSource/Flow 重发(详见 [[feedback_room_paging_invalidation]])。
     */
    suspend fun updateContactBio(contactId: Long, bio: String?)

    suspend fun deleteContact(contact: ContactCacheEntity)

    suspend fun deleteByIds(ids: List<Long>)

    fun searchContacts(query: String): Flow<List<ContactCacheEntity>>

    /**
     * 触发 PagingSource/Flow 重发。
     */
    suspend fun bumpContact(contactId: Long)

    // ========== 联系人社交平台操作 ==========

    suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry)

    suspend fun removeContactPlatform(contactId: Long, fieldKey: String)

    /** 批量获取所有联系人的平台数据,按 contactId 分组 */
    suspend fun getAllContactPlatformsGrouped(): Map<Long, List<ContactPlatform>>

    /** 获取指定联系人已有的平台 key 集合 */
    suspend fun getContactPlatformKeys(contactId: Long): Set<String>

    /** 获取指定联系人的所有平台数据 */
    suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform>

    // ========== 重复检测 ==========

    suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>
    ): DuplicateCheckResult

    // ========== QAuxv 导入 ==========

    suspend fun findExistingQQContacts(entries: List<QAuxvFriendEntry>): Map<Long, Long>

    suspend fun importQAuxvFriends(
        decisions: List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>,
        context: Context,
        onProgress: ((QAuxvImportProgress) -> Unit)? = null,
    ): QAuxvImportSummary
}