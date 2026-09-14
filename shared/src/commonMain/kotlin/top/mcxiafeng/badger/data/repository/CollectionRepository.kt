package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.model.CardCollectionWithCount
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

interface CollectionRepository {

    

    fun getAllCollections(): Flow<List<CardCollectionCacheEntity>>

    suspend fun getAllCollectionsOnce(): List<CardCollectionCacheEntity>

    suspend fun getContactsByCollectionOnce(collectionId: Long): List<ContactCacheEntity>

    fun getCollectionsWithCount(): Flow<List<CardCollectionWithCount>>

    suspend fun getCollectionById(id: Long): CardCollectionCacheEntity?

    suspend fun insertCollection(collection: CardCollectionCacheEntity): Long

    suspend fun updateCollection(collection: CardCollectionCacheEntity)

    suspend fun deleteCollection(collection: CardCollectionCacheEntity)

    fun getContactsByCollection(collectionId: Long): Flow<List<ContactCacheEntity>>

    

    
    fun getContactCollectionIds(contactId: Long): Flow<List<Long>>

    
    suspend fun addContactToCollection(
        contactId: Long,
        collectionId: Long,
        sourceType: String = "manual",
    )

    
    suspend fun existsContactInCollection(contactId: Long, collectionId: Long): Boolean

    
    suspend fun removeContactFromCollection(contactId: Long, collectionId: Long)

    
    suspend fun removeContactsFromCollection(contactIds: List<Long>, collectionId: Long)

    
    suspend fun getMemberCountsByCollection(collectionId: Long): Map<Long, Int>
}
