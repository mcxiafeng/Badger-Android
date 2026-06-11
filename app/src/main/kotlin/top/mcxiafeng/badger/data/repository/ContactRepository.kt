package top.mcxiafeng.badger.data.repository

import androidx.paging.PagingData
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PlatformEntry

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
}
