package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.PlatformEntry

/**
 * 联系人数据仓库接口
 *
 * 管理联系人基本操作和社交平台操作。
 */
interface ContactRepository {

    // ========== 联系人基本操作 ==========

    fun getAllContacts(): Flow<List<Contact>>

    suspend fun getContactById(id: Long): Contact?

    fun getAllContactsWithFields(): Flow<List<ContactWithFields>>

    suspend fun getContactWithFieldsById(id: Long): ContactWithFields?

    suspend fun insertContact(contact: Contact): Long

    suspend fun updateContact(contact: Contact)

    suspend fun deleteContact(contact: Contact)

    fun searchContacts(query: String): Flow<List<Contact>>

    // ========== 联系人社交平台操作 ==========

    suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry)

    suspend fun removeContactPlatform(contactId: Long, fieldKey: String)

    // ========== 重复检测 ==========

    suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>
    ): DuplicateCheckResult
}
