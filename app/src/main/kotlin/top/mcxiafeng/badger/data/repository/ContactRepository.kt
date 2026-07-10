package top.mcxiafeng.badger.data.repository

import android.content.Context
import androidx.paging.PagingData
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.QAuxvImportProgress
import top.mcxiafeng.badger.data.QAuxvImportSummary

/**
 * 联系人数据仓库接口
 *
 * 管理联系人基本操作和社交平台操作。
 */
interface ContactRepository {

    // ========== 联系人基本操作 ==========

    fun getAllContacts(): Flow<List<Contact>>

    fun getAllContactsPagingSource(): PagingSource<Int, Contact>

    fun searchContactsPagingSource(query: String): Flow<PagingData<Contact>>

    fun getLetterIndex(): Flow<List<LetterCount>>

    suspend fun getContactById(id: Long): Contact?

    fun getAllContactsWithFields(): Flow<List<ContactWithFields>>

    suspend fun getContactWithFieldsById(id: Long): ContactWithFields?

    suspend fun insertContact(contact: Contact): Long

    suspend fun updateContact(contact: Contact)

    suspend fun deleteContact(contact: Contact)

    suspend fun deleteByIds(ids: List<Long>)

    fun searchContacts(query: String): Flow<List<Contact>>

    /**
     * 触发 PagingSource/Flow 重发(参见 ContactDao.bumpContact 注释)。
     * 用于外部模块(如 TagRepository、FieldRepository)写库后让 PersonPage 列表刷新。
     */
    suspend fun bumpContact(contactId: Long)

    // ========== 联系人社交平台操作 ==========

    suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry)

    suspend fun removeContactPlatform(contactId: Long, fieldKey: String)

    /** 批量获取所有联系人的平台数据，按 contactId 分组 */
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

    /**
     * 批量预查重：返回 entries 中哪些 uin 在 Badger 已存在 QQ 平台条目。
     *
     * @return Map<uin, contactId>，仅包含 uin 已存在于 contact_platforms (platformKey='qq') 的项
     */
    suspend fun findExistingQQContacts(entries: List<QAuxvFriendEntry>): Map<Long, Long>

    /**
     * 写入选中的 entries（用户在预览/冲突 Dialog 中已决定的 actions）。
     *
     * 内部流程：
     * 1. mutex 外并发下载每个非 Skip 项的 QQ 头像（写入 filesDir，限流 6 并发）
     * 2. mutex 内逐条写库（Contact.avatarPath + Contact.avatarUrl + ContactPlatform.avatarUrl）
     *
     * 每个三元组 (entry, existingContactId?, action)：
     * - [QAuxvConflictAction.Skip] → 不写库也不下载头像，skipped++
     * - [QAuxvConflictAction.Replace] → existingContactId 有效则更新其 name + 头像 + QQ 平台条目，
     *   无效则降级为新增；replaced++
     * - [QAuxvConflictAction.InsertAnyway] → 新增 Contact + 新增 QQ 平台条目（即便 QQ 号已存在另一联系人，仍独立新增）；inserted++
     *
     * @param context ApplicationContext，用于 filesDir 存头像
     * @param onProgress 进度回调（下载 x/N、写库 y/M），null 表示不关心
     */
    suspend fun importQAuxvFriends(
        decisions: List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>,
        context: Context,
        onProgress: ((QAuxvImportProgress) -> Unit)? = null,
    ): QAuxvImportSummary
}

/** 与文件解析结果相关的类型别名，复用以避免到处 import。 */
