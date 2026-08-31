package top.mcxiafeng.badger.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PersonWithFields
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.QAuxvImportProgress
import top.mcxiafeng.badger.data.QAuxvImportSummary
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity

/** 联系人领域的数据访问契约。 */
interface ContactRepository {
    fun getAllContacts(): Flow<List<ContactCacheEntity>>
    fun getLetterIndex(): Flow<List<LetterCount>>
    suspend fun getContactById(id: Long): ContactCacheEntity?
    suspend fun getContactByServerId(serverId: String): ContactCacheEntity?
    fun getAllContactsWithFields(): Flow<List<PersonWithFields>>
    suspend fun getPersonWithFieldsById(id: Long): PersonWithFields?

    suspend fun insertContact(contact: ContactCacheEntity): Long
    suspend fun updateContact(contact: ContactCacheEntity)
    suspend fun updateContactBio(contactId: Long, bio: String?)
    suspend fun deleteContact(contact: ContactCacheEntity)
    suspend fun deleteByIds(ids: List<Long>)

    /** 直推删除：软删隐藏 → 服务端 DELETE → 成功后物理删除，失败恢复可见。 */
    suspend fun commitDelete(contactId: Long): CommitResult

    /** 服务端原子合并联系人；客户端成功后清理被合并的本地行。 */
    suspend fun commitMerge(targetId: Long, mergedIds: List<Long>): CommitResult

    fun searchContacts(query: String): Flow<List<ContactCacheEntity>>
    suspend fun bumpContact(contactId: Long)

    suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry)
    suspend fun removeContactPlatform(contactId: Long, fieldKey: String)
    suspend fun getAllContactPlatformsGrouped(): Map<Long, List<ContactPlatformCacheEntity>>
    suspend fun getContactPlatformKeys(contactId: Long): Set<String>
    suspend fun getContactPlatforms(contactId: Long): List<ContactPlatformCacheEntity>

    suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>,
    ): DuplicateCheckResult

    suspend fun findExistingQQContacts(entries: List<QAuxvFriendEntry>): Map<Long, Long>

    suspend fun importQAuxvFriends(
        decisions: List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>,
        context: Context,
        onProgress: ((QAuxvImportProgress) -> Unit)? = null,
    ): QAuxvImportSummary
}
