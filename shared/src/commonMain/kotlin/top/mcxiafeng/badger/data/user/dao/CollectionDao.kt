package top.mcxiafeng.badger.data.user.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.CollectionPersonRef
import top.mcxiafeng.badger.data.user.entity.Person

@Dao
interface CollectionDao {

    @Upsert
    suspend fun upsertCollection(collection: Collection)

    @Delete
    suspend fun deleteCollection(collection: Collection)

    @Query("SELECT * FROM Collection WHERE uuid = :uuid")
    suspend fun getCollection(uuid: Uuid): Collection?

    @Query("SELECT * FROM Collection")
    fun observeAllCollections(): Flow<List<Collection>>

    @Upsert
    suspend fun upsertAll(collections: List<Collection>)

    @Query("DELETE FROM Collection WHERE uuid NOT IN (:keep)")
    suspend fun deleteCollectionsNotIn(keep: List<Uuid>)

    @Query("DELETE FROM Collection")
    suspend fun deleteAllCollections()

    @Query("DELETE FROM Collection WHERE uuid = :uuid")
    suspend fun deleteCollectionByUuid(uuid: Uuid)

    @Query("SELECT * FROM Collection")
    suspend fun getCollections(): List<Collection>

    @Query("SELECT * FROM Collection WHERE ownerUuid = :ownerUuid")
    suspend fun getCollectionsByUser(ownerUuid: Uuid): List<Collection>

    @Upsert
    suspend fun upsertCollectionPersonRef(ref: CollectionPersonRef)

    @Delete
    suspend fun deleteCollectionPersonRef(ref: CollectionPersonRef)

    @Query("SELECT Person.* FROM Person INNER JOIN CollectionPersonRef ON Person.uuid = CollectionPersonRef.personUuid WHERE CollectionPersonRef.collectionUuid = :collectionUuid")
    suspend fun getPersonsByCollection(collectionUuid: Uuid): List<Person>
}
