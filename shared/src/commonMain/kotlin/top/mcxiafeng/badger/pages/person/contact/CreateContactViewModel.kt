package top.mcxiafeng.badger.pages.person.contact

import androidx.lifecycle.ViewModel
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.ensureCollectionId
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.downloadAndStoreAvatar

private const val TAG = "CreateContactVM"

class CreateContactViewModel : ViewModel() {

    val contactRepository: ContactRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val collectionRepository: CollectionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    suspend fun createMinimalContact(name: String, collectionId: Long?): Long {
        val now = nowMs()
        val contact = Contact(
            id = 0L,
            name = name,
            createTime = now,
            updateTime = now,
        )
        val contactId = contactRepository.insertContact(contact)
        val effectiveCollectionId = ensureCollectionId(collectionRepository, collectionId)
        collectionRepository.addContactToCollection(
            contactId = contactId,
            collectionId = effectiveCollectionId,
            sourceType = "manual"
        )
        return contactId
    }

    

    suspend fun createContactFromResolve(
        name: String,
        bio: String?,
        avatarUrl: String?,
        platformKey: String?,
        platformValue: String?,
        collectionId: Long?,
    ): Long {
        val now = nowMs()
        
        var avatarPath: String? = null
        if (!avatarUrl.isNullOrBlank()) {
            try {
                avatarPath = downloadAndStoreAvatar(avatarUrl, "contact_avatar_${now}.webp")
                if (avatarPath == null) {
                    BadgerLog.w(TAG, "createContactFromResolve: 头像下载失败,保留 avatarUrl")
                }
            } catch (e: Exception) {
                BadgerLog.w(TAG, "createContactFromResolve: 头像下载异常", e)
            }
        }

        val contact = Contact(
            id = 0L,
            name = name,
            bio = bio?.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
            avatarPath = avatarPath,
            createTime = now,
            updateTime = now,
        )
        val contactId = contactRepository.insertContact(contact)

        
        if (!platformKey.isNullOrBlank() && !platformValue.isNullOrBlank()) {
            contactRepository.updateContactPlatform(
                contactId = contactId,
                fieldKey = platformKey,
                entry = PlatformEntry(
                    displayName = null,
                    jumpLink = "",
                    originalLink = null,
                    value = platformValue,
                )
            )
        }

        val effectiveCollectionId = ensureCollectionId(collectionRepository, collectionId)
        collectionRepository.addContactToCollection(
            contactId = contactId,
            collectionId = effectiveCollectionId,
            sourceType = "auto_resolve"
        )
        return contactId
    }
}
