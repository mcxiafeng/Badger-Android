package top.mcxiafeng.badger.pages.person.contact

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactRepository
import top.mcxiafeng.badger.data.ensureCollectionId
import javax.inject.Inject

@HiltViewModel
class CreateContactViewModel @Inject constructor(
    val repository: ContactRepository
) : ViewModel() {

    suspend fun createMinimalContact(name: String, collectionId: Long?): Long {
        val contact = Contact(name = name)
        val contactId = repository.insertContact(contact)
        val effectiveCollectionId = ensureCollectionId(repository, collectionId)
        repository.addContactToCollection(
            contactId = contactId,
            collectionId = effectiveCollectionId,
            sourceType = "manual"
        )
        return contactId
    }
}
