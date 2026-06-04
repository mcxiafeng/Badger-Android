package top.mcxiafeng.badger.pages.person.contact

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ensureCollectionId
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import javax.inject.Inject

@HiltViewModel
class CreateContactViewModel @Inject constructor(
    val contactRepository: ContactRepository,
    val collectionRepository: CollectionRepository
) : ViewModel() {

    suspend fun createMinimalContact(name: String, collectionId: Long?): Long {
        val contact = Contact(name = name)
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
