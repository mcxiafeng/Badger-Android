package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.model.DuplicateCheckResult
import top.mcxiafeng.badger.data.model.LetterCount
import top.mcxiafeng.badger.data.model.PersonWithFields
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.model.QAuxvConflictAction
import top.mcxiafeng.badger.data.importer.QAuxvFriendEntry
import top.mcxiafeng.badger.data.model.QAuxvImportProgress
import top.mcxiafeng.badger.data.model.QAuxvImportSummary
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity

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

    
    suspend fun commitDelete(contactId: Long): CommitResult

    
    suspend fun commitMerge(targetId: Long, mergedIds: List<Long>): CommitResult

    fun searchContacts(query: String): Flow<List<ContactCacheEntity>>
    suspend fun bumpContact(contactId: Long)

    suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry)
    suspend fun removeContactPlatform(contactId: Long, fieldKey: String)

    

    suspend fun pushBasicInfoEdit(contactId: Long)

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
        onProgress: ((QAuxvImportProgress) -> Unit)? = null,
    ): QAuxvImportSummary
}
