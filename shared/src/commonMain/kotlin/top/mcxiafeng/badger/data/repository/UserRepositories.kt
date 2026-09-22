package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.user.dao.CollectionDao
import top.mcxiafeng.badger.data.user.dao.PersonDao
import top.mcxiafeng.badger.data.user.dao.ProfileDao
import top.mcxiafeng.badger.data.user.dao.TagsDao
import top.mcxiafeng.badger.data.user.dao.UserDao
import top.mcxiafeng.badger.data.user.database.CacheDbHolder
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.CollectionPersonRef
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.PersonTagsRef
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.data.user.entity.User
import top.mcxiafeng.badger.data.user.entity.UserPersonRef
import top.mcxiafeng.badger.data.user.entity.UserTagsRef
import kotlin.uuid.Uuid

/** 用户表 CRUD 仓储，薄封装 [UserDao]。 */
class UserRepository(
    private val dao: UserDao = CacheDbHolder.get().userDao(),
) {
    suspend fun upsert(user: User) = dao.upsertUser(user)
    suspend fun delete(user: User) = dao.deleteUser(user)
    suspend fun get(uuid: Uuid): User? = dao.getUser(uuid)
}

/** 联系人表 CRUD 仓储，薄封装 [PersonDao]，含 用户↔联系人、联系人↔标签 两张关联表。 */
class PersonRepository(
    private val dao: PersonDao = CacheDbHolder.get().personDao(),
) {
    suspend fun upsert(person: Person) = dao.upsertPerson(person)
    suspend fun upsertAll(persons: List<Person>) = dao.upsertAll(persons)
    suspend fun delete(person: Person) = dao.deletePerson(person)
    suspend fun deleteByUuid(uuid: Uuid) = dao.deletePersonByUuid(uuid)
    suspend fun deleteNotIn(keep: List<Uuid>) = dao.deletePersonsNotIn(keep)
    suspend fun deleteAll() = dao.deleteAllPersons()
    suspend fun get(uuid: Uuid): Person? = dao.getPerson(uuid)
    fun observeAll(): Flow<List<Person>> = dao.observeAllPersons()

    /** 用户↔联系人 关联（UserPersonRef）。 */
    suspend fun linkUserPerson(ref: UserPersonRef) = dao.upsertUserPersonRef(ref)
    suspend fun unlinkUserPerson(ref: UserPersonRef) = dao.deleteUserPersonRef(ref)
    suspend fun getPersonsByUser(userUuid: Uuid): List<Person> = dao.getPersonsByUser(userUuid)

    /** 联系人↔标签 关联（PersonTagsRef）。 */
    suspend fun linkPersonTags(ref: PersonTagsRef) = dao.upsertPersonTagsRef(ref)
    suspend fun unlinkPersonTags(ref: PersonTagsRef) = dao.deletePersonTagsRef(ref)
    suspend fun getTagsByPerson(personUuid: Uuid): List<Tags> = dao.getTagsByPerson(personUuid)
}

/** 名片夹表 CRUD 仓储，薄封装 [CollectionDao]，含 名片夹↔联系人 关联。 */
class CollectionRepository(
    private val dao: CollectionDao = CacheDbHolder.get().collectionDao(),
) {
    suspend fun upsert(collection: Collection) = dao.upsertCollection(collection)
    suspend fun upsertAll(collections: List<Collection>) = dao.upsertAll(collections)
    suspend fun delete(collection: Collection) = dao.deleteCollection(collection)
    suspend fun deleteByUuid(uuid: Uuid) = dao.deleteCollectionByUuid(uuid)
    suspend fun deleteNotIn(keep: List<Uuid>) = dao.deleteCollectionsNotIn(keep)
    suspend fun deleteAll() = dao.deleteAllCollections()
    suspend fun get(uuid: Uuid): Collection? = dao.getCollection(uuid)
    suspend fun getAll(): List<Collection> = dao.getCollections()
    suspend fun getByUser(ownerUuid: Uuid): List<Collection> = dao.getCollectionsByUser(ownerUuid)
    fun observeAll(): Flow<List<Collection>> = dao.observeAllCollections()

    /** 名片夹↔联系人 关联（CollectionPersonRef）。 */
    suspend fun linkCollectionPerson(ref: CollectionPersonRef) = dao.upsertCollectionPersonRef(ref)
    suspend fun unlinkCollectionPerson(ref: CollectionPersonRef) = dao.deleteCollectionPersonRef(ref)
    suspend fun getPersonsByCollection(collectionUuid: Uuid): List<Person> =
        dao.getPersonsByCollection(collectionUuid)
}

/** 档案表 CRUD 仓储，薄封装 [ProfileDao]。 */
class ProfileRepository(
    private val dao: ProfileDao = CacheDbHolder.get().profileDao(),
) {
    suspend fun upsert(profile: Profile) = dao.upsertProfile(profile)
    suspend fun delete(profile: Profile) = dao.deleteProfile(profile)
    suspend fun get(uuid: Uuid): Profile? = dao.getProfile(uuid)
}

/** 标签表 CRUD 仓储，薄封装 [TagsDao]，含 用户↔标签 关联。 */
class TagsRepository(
    private val dao: TagsDao = CacheDbHolder.get().tagsDao(),
) {
    suspend fun upsert(tags: Tags) = dao.upsertTags(tags)
    suspend fun upsertAll(tags: List<Tags>) = dao.upsertAll(tags)
    suspend fun delete(tags: Tags) = dao.deleteTags(tags)
    suspend fun deleteByUuid(uuid: Uuid) = dao.deleteTagsByUuid(uuid)
    suspend fun deleteNotIn(keep: List<Uuid>) = dao.deleteTagsNotIn(keep)
    suspend fun deleteAll() = dao.deleteAllTags()
    suspend fun get(uuid: Uuid): Tags? = dao.getTags(uuid)
    fun observeAll(): Flow<List<Tags>> = dao.observeAllTags()

    /** 用户↔标签 关联（UserTagsRef）。 */
    suspend fun linkUserTags(ref: UserTagsRef) = dao.upsertUserTagsRef(ref)
    suspend fun unlinkUserTags(ref: UserTagsRef) = dao.deleteUserTagsRef(ref)
    suspend fun getTagsByUser(userUuid: Uuid): List<Tags> = dao.getTagsByUser(userUuid)
}
