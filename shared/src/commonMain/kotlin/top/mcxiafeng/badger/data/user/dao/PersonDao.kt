package top.mcxiafeng.badger.data.user.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.PersonTagsRef
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.data.user.entity.UserPersonRef

@Dao
interface PersonDao {

    @Upsert
    suspend fun upsertPerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    @Query("SELECT * FROM Person")
    fun observeAllPersons(): Flow<List<Person>>

    @Query("SELECT * FROM Person")
    suspend fun getAllPersons(): List<Person>

    @Upsert
    suspend fun upsertAll(persons: List<Person>)

    @Query("DELETE FROM Person WHERE uuid NOT IN (:keep)")
    suspend fun deletePersonsNotIn(keep: List<Uuid>)

    @Query("DELETE FROM Person")
    suspend fun deleteAllPersons()

    @Query("DELETE FROM Person WHERE uuid = :uuid")
    suspend fun deletePersonByUuid(uuid: Uuid)

    @Query("SELECT * FROM Person WHERE uuid = :uuid")
    suspend fun getPerson(uuid: Uuid): Person?

    @Upsert
    suspend fun upsertUserPersonRef(ref: UserPersonRef)

    @Delete
    suspend fun deleteUserPersonRef(ref: UserPersonRef)

    @Query("SELECT Person.* FROM Person INNER JOIN UserPersonRef ON Person.uuid = UserPersonRef.personUuid WHERE UserPersonRef.userUuid = :userUuid")
    suspend fun getPersonsByUser(userUuid: Uuid): List<Person>

    @Upsert
    suspend fun upsertPersonTagsRef(ref: PersonTagsRef)

    @Delete
    suspend fun deletePersonTagsRef(ref: PersonTagsRef)

    @Query("SELECT Tags.* FROM Tags INNER JOIN PersonTagsRef ON Tags.uuid = PersonTagsRef.tagsUuid WHERE PersonTagsRef.personUuid = :personUuid")
    suspend fun getTagsByPerson(personUuid: Uuid): List<Tags>
}
