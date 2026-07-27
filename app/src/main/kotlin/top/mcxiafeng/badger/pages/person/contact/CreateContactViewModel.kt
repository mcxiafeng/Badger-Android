package top.mcxiafeng.badger.pages.person.contact

import androidx.lifecycle.ViewModel
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.ensureCollectionId
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository

/** [§14.2] Koin `inject()` 字段注入,移除 `@HiltViewModel`。 */
class CreateContactViewModel : ViewModel() {

    val contactRepository: ContactRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val collectionRepository: CollectionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    suspend fun createMinimalContact(name: String, collectionId: Long?): Long {
        val now = System.currentTimeMillis()
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
}
