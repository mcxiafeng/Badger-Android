package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.data.user.dao.CollectionDao
import top.mcxiafeng.badger.data.user.dao.PersonDao
import top.mcxiafeng.badger.data.user.dao.PlatformDao
import top.mcxiafeng.badger.data.user.dao.ProfileDao
import top.mcxiafeng.badger.data.user.dao.TagsDao
import top.mcxiafeng.badger.data.user.dao.UserDao
import top.mcxiafeng.badger.data.user.database.CacheDbHolder
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.CollectionPersonRef
import top.mcxiafeng.badger.data.user.entity.Contact
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.PersonTagsRef
import top.mcxiafeng.badger.data.user.entity.Platform
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.data.user.entity.User
import top.mcxiafeng.badger.data.user.entity.UserPersonRef
import top.mcxiafeng.badger.data.user.entity.UserTagsRef
import top.mcxiafeng.badger.sync.AtomPayload
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncType
import top.mcxiafeng.badger.utils.BadgerLog
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
    private val systemRepository: SystemRepository = SystemRepository(),
) {
    suspend fun upsert(person: Person) = dao.upsertPerson(person)
    suspend fun upsertAll(persons: List<Person>) = dao.upsertAll(persons)
    suspend fun delete(person: Person) = dao.deletePerson(person)
    suspend fun deleteByUuid(uuid: Uuid) = dao.deletePersonByUuid(uuid)
    suspend fun deleteNotIn(keep: List<Uuid>) = dao.deletePersonsNotIn(keep)
    suspend fun deleteAll() = dao.deleteAllPersons()
    suspend fun get(uuid: Uuid): Person? = dao.getPerson(uuid)
    suspend fun getAll(): List<Person> = dao.getAllPersons()
    fun observeAll(): Flow<List<Person>> = dao.observeAllPersons()

    /** 用户↔联系人 关联（UserPersonRef）。 */
    suspend fun linkUserPerson(ref: UserPersonRef) = dao.upsertUserPersonRef(ref)
    suspend fun unlinkUserPerson(ref: UserPersonRef) = dao.deleteUserPersonRef(ref)
    suspend fun getPersonsByUser(userUuid: Uuid): List<Person> = dao.getPersonsByUser(userUuid)

    /** 联系人↔标签 关联（PersonTagsRef）。 */
    suspend fun linkPersonTags(ref: PersonTagsRef) = dao.upsertPersonTagsRef(ref)
    suspend fun unlinkPersonTags(ref: PersonTagsRef) = dao.deletePersonTagsRef(ref)
    suspend fun getTagsByPerson(personUuid: Uuid): List<Tags> = dao.getTagsByPerson(personUuid)

    // ---------------- 本地优先写路径（先入队后落库，离线可用） ----------------
    // 顺序契约：atom 先于本地行存在。引擎读队列建避让索引与 VM 入队并发时，
    // 先入队保证新意图必被本轮避让覆盖（反向顺序有"新行被 deleteNotIn 误删"的竞态窗口）。

    /** 离线新增：入队 INSERT 意图（服务端读 body.uuid，无重映射）→ 落库。 */
    suspend fun createLocal(person: Person) {
        systemRepository.enqueueAtom(
            EntityKind.PERSON,
            SyncType.INSERT,
            AtomPayload.insert(encodeJson(person)),
        )
        dao.upsertPerson(person)
        BadgerLog.d(TAG, "createLocal 入队并落库：uuid=${person.uuid} name=${person.name}")
    }

    /** 离线修改：写前重读基线快照（from）→ 入队 UPDATE 意图 → 落库新值（to）。 */
    suspend fun updateLocal(person: Person) {
        val from = dao.getPerson(person.uuid)
            ?: throw IllegalStateException("updateLocal 目标不存在：${person.uuid}")
        systemRepository.enqueueAtom(
            EntityKind.PERSON,
            SyncType.UPDATE,
            AtomPayload.update(encodeJson(from), encodeJson(person)),
        )
        dao.upsertPerson(person)
        BadgerLog.d(TAG, "updateLocal 入队并落库：uuid=${person.uuid}")
    }

    /** 离线删除：入队 DELETE 意图 → 删本地行（服务端级联删 profile/成员）。 */
    suspend fun deleteLocal(uuid: Uuid) {
        systemRepository.enqueueAtom(
            EntityKind.PERSON,
            SyncType.DELETE,
            AtomPayload.delete(uuid),
        )
        dao.deletePersonByUuid(uuid)
        BadgerLog.d(TAG, "deleteLocal 入队并删行：uuid=$uuid")
    }

    private fun encodeJson(person: Person): JsonObject =
        Json.encodeToJsonElement(person).jsonObject

    private companion object {
        const val TAG = "PersonRepositoryTester"
    }
}

/** 名片夹表 CRUD 仓储，薄封装 [CollectionDao]，含 名片夹↔联系人 关联。 */
class CollectionRepository(
    private val dao: CollectionDao = CacheDbHolder.get().collectionDao(),
    private val systemRepository: SystemRepository = SystemRepository(),
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

    // ---------------- 本地优先写路径（先入队后落库，离线可用） ----------------

    /** 离线新增：入队 INSERT 意图 → 落库。 */
    suspend fun createLocal(collection: Collection) {
        systemRepository.enqueueAtom(
            EntityKind.COLLECTION,
            SyncType.INSERT,
            AtomPayload.insert(encodeJson(collection)),
        )
        dao.upsertCollection(collection)
        BadgerLog.d(TAG, "createLocal 入队并落库：uuid=${collection.uuid} name=${collection.name}")
    }

    /**
     * 离线修改：写前重读基线快照（from）→ 入队 UPDATE 意图 → 落库新值（to）。
     * from 是冲突判定的依据——服务端现值 != from 即判定冲突。
     */
    suspend fun updateLocal(collection: Collection) {
        val from = dao.getCollection(collection.uuid)
            ?: throw IllegalStateException("updateLocal 目标不存在：${collection.uuid}")
        systemRepository.enqueueAtom(
            EntityKind.COLLECTION,
            SyncType.UPDATE,
            AtomPayload.update(encodeJson(from), encodeJson(collection)),
        )
        dao.upsertCollection(collection)
        BadgerLog.d(TAG, "updateLocal 入队并落库：uuid=${collection.uuid}")
    }

    /** 离线删除：入队 DELETE 意图 → 删本地行。 */
    suspend fun deleteLocal(uuid: Uuid) {
        systemRepository.enqueueAtom(
            EntityKind.COLLECTION,
            SyncType.DELETE,
            AtomPayload.delete(uuid),
        )
        dao.deleteCollectionByUuid(uuid)
        BadgerLog.d(TAG, "deleteLocal 入队并删行：uuid=$uuid")
    }

    private fun encodeJson(collection: Collection): JsonObject =
        Json.encodeToJsonElement(collection).jsonObject

    private companion object {
        const val TAG = "CollectionRepositoryTester"
    }
}

/** 档案表 CRUD 仓储，薄封装 [ProfileDao]。 */
class ProfileRepository(
    private val dao: ProfileDao = CacheDbHolder.get().profileDao(),
    private val systemRepository: SystemRepository = SystemRepository(),
) {
    suspend fun upsert(profile: Profile) = dao.upsertProfile(profile)
    suspend fun delete(profile: Profile) = dao.deleteProfile(profile)
    suspend fun get(uuid: Uuid): Profile? = dao.getProfile(uuid)

    /**
     * 离线修改自己名片：写前重读基线快照（from）→ 入队 UPDATE 意图 → 落库新值（to）。
     * Profile 无新增/删除语义（随 person 由服务端建删），只有 UPDATE。
     */
    suspend fun updateLocal(profile: Profile) {
        val from = dao.getProfile(profile.uuid)
            ?: throw IllegalStateException("updateLocal 目标不存在：${profile.uuid}")
        systemRepository.enqueueAtom(
            EntityKind.PROFILE,
            SyncType.UPDATE,
            AtomPayload.update(encodeJson(from), encodeJson(profile)),
        )
        dao.upsertProfile(profile)
        BadgerLog.d(TAG, "updateLocal 入队并落库：uuid=${profile.uuid}")
    }

    private fun encodeJson(profile: Profile): JsonObject =
        Json.encodeToJsonElement(profile).jsonObject

    private companion object {
        const val TAG = "ProfileRepositoryTester"
    }
}

/** 标签表 CRUD 仓储，薄封装 [TagsDao]，含 用户↔标签 关联。 */
class TagsRepository(
    private val dao: TagsDao = CacheDbHolder.get().tagsDao(),
    private val systemRepository: SystemRepository = SystemRepository(),
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

    // ---------------- 本地优先写路径（先入队后落库，离线可用） ----------------

    /** 离线新增：入队 INSERT 意图；uuid 为客户端生成。 */
    suspend fun createLocal(tags: Tags) {
        systemRepository.enqueueAtom(
            EntityKind.TAG,
            SyncType.INSERT,
            AtomPayload.insert(encodeJson(tags)),
        )
        dao.upsertTags(tags)
        BadgerLog.d(TAG, "createLocal 入队并落库：uuid=${tags.uuid} name=${tags.name}")
    }

    /** 离线修改：写前重读基线快照（from）→ 入队 UPDATE 意图 → 落库新值（to）。 */
    suspend fun updateLocal(tags: Tags) {
        val from = dao.getTags(tags.uuid)
            ?: throw IllegalStateException("updateLocal 目标不存在：${tags.uuid}")
        systemRepository.enqueueAtom(
            EntityKind.TAG,
            SyncType.UPDATE,
            AtomPayload.update(encodeJson(from), encodeJson(tags)),
        )
        dao.upsertTags(tags)
        BadgerLog.d(TAG, "updateLocal 入队并落库：uuid=${tags.uuid}")
    }

    /** 离线删除：入队 DELETE 意图 → 删本地行。 */
    suspend fun deleteLocal(uuid: Uuid) {
        systemRepository.enqueueAtom(
            EntityKind.TAG,
            SyncType.DELETE,
            AtomPayload.delete(uuid),
        )
        dao.deleteTagsByUuid(uuid)
        BadgerLog.d(TAG, "deleteLocal 入队并删行：uuid=$uuid")
    }

    private fun encodeJson(tags: Tags): JsonObject =
        Json.encodeToJsonElement(tags).jsonObject

    private companion object {
        const val TAG = "TagsRepositoryTester"
    }
}

/** 档案表 CRUD 仓储，薄封装 [PlatformDao]。 */
class PlatformRepository(
    private val dao: PlatformDao = CacheDbHolder.get().platformDao(),
) {
    fun observeAll(): Flow<List<Platform>> = dao.observeAllPlatform()
    suspend fun upsert(platform: Platform) = dao.upsertPlatform(platform)
    suspend fun delete(platform: Platform) = dao.deletePlatform(platform)
    suspend fun get(name: String?): Platform? = dao.getPlatform(name)
}
