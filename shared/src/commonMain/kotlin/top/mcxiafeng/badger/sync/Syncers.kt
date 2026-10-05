package top.mcxiafeng.badger.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.PersonRepository
import top.mcxiafeng.badger.data.repository.ProfileRepository
import top.mcxiafeng.badger.data.repository.TagsRepository
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.network.core.CollectionApi
import top.mcxiafeng.badger.network.core.PersonApi
import top.mcxiafeng.badger.network.core.ProfileApi
import top.mcxiafeng.badger.network.core.TagsApi
import kotlin.uuid.Uuid

/**
 * 四个实体的同步适配器：本地走各自 Repository，服务端直连 network/core 的 Api 单例。
 * 只提供 [Syncable] 原语；判定表/落库/裁决由 Syncable 默认实现承担。
 * open 仅为单测假适配器（继承真实类、只覆写服务端原语）服务。
 */
open class PersonSyncer(
    private val repository: PersonRepository = PersonRepository(),
) : Syncable<Person> {

    override val kind = EntityKind.PERSON
    override fun uuidOf(entity: Person): Uuid = entity.uuid
    override fun labelOf(entity: Person): String = entity.name ?: "(无名称)"
    override fun encode(entity: Person): JsonObject = Json.encodeToJsonElement(entity).jsonObject
    override fun decode(data: JsonObject): Person = Json.decodeFromJsonElement(data)

    override suspend fun fetchAll(profileUuid: Uuid): List<Person> =
        requireBody("getPersons", PersonApi.getPersons())

    override suspend fun localUpsertAll(rows: List<Person>) = repository.upsertAll(rows)
    override suspend fun localDelete(uuid: Uuid) = repository.deleteByUuid(uuid)
    override suspend fun localDeleteAll() = repository.deleteAll()
    override suspend fun localDeleteNotIn(keep: List<Uuid>) = repository.deleteNotIn(keep)

    override suspend fun pushCreate(entity: Person) =
        requireSuccess("createPerson", PersonApi.createPerson(entity))

    override suspend fun pushUpdate(entity: Person) =
        requireSuccess("updatePerson", PersonApi.updatePerson(entity))

    override suspend fun pushDelete(uuid: Uuid) =
        requireSuccess("deletePerson", PersonApi.deletePerson(uuid))

    override suspend fun serverGet(uuid: Uuid): Person? {
        // 用户裁决：查重走单查接口 GET /persons/{uuid}，不依赖全量清单。
        // decodeExisting 三态：200→实体 / 404→null（确无） / 其余失败→SGX 留队——
        // 网络抖动绝不能折成"不存在"，否则 UPDATE/DELETE 分支会误删本地行（丢数据）。
        return decodeExisting("getPerson", PersonApi.getPerson(uuid), ::decode)
    }
}

open class ProfileSyncer(
    private val repository: ProfileRepository = ProfileRepository(),
) : Syncable<Profile> {

    override val kind = EntityKind.PROFILE
    override fun uuidOf(entity: Profile): Uuid = entity.uuid
    override fun labelOf(entity: Profile): String = "我的名片"
    override fun encode(entity: Profile): JsonObject = Json.encodeToJsonElement(entity).jsonObject
    override fun decode(data: JsonObject): Profile = Json.decodeFromJsonElement(data)

    override suspend fun fetchAll(profileUuid: Uuid): List<Profile> {
        val profile = decodeExisting("getOwnProfile", ProfileApi.getProfile(profileUuid)) { decode(it) }
            ?: throw SyncGatewayException("getOwnProfile", errorType = null, statusCode = null)
        return listOf(profile)
    }

    override suspend fun localUpsertAll(rows: List<Profile>) {
        rows.forEach { repository.upsert(it) }
    }

    override suspend fun localDelete(uuid: Uuid) {
        repository.get(uuid)?.let { repository.delete(it) }
    }

    // profile 表还承载他人名片的按需缓存，全量清理一律 no-op，绝不误删
    override suspend fun localDeleteAll() {}
    override suspend fun localDeleteNotIn(keep: List<Uuid>) {}

    override suspend fun pushCreate(entity: Profile) =
        throw IllegalStateException("PROFILE 无 create 语义")

    override suspend fun pushUpdate(entity: Profile) =
        requireSuccess("updateProfile", ProfileApi.updateProfile(entity))

    override suspend fun pushDelete(uuid: Uuid) =
        throw IllegalStateException("PROFILE 无 delete 语义")

    override suspend fun serverGet(uuid: Uuid): Profile? =
        decodeExisting("getOwnProfile", ProfileApi.getProfile(uuid)) { decode(it) }
}

open class CollectionSyncer(
    private val repository: CollectionRepository = CollectionRepository(),
) : Syncable<Collection> {

    override val kind = EntityKind.COLLECTION
    override fun uuidOf(entity: Collection): Uuid = entity.uuid
    override fun labelOf(entity: Collection): String = entity.name
    override fun encode(entity: Collection): JsonObject = Json.encodeToJsonElement(entity).jsonObject
    override fun decode(data: JsonObject): Collection = Json.decodeFromJsonElement(data)

    override suspend fun fetchAll(profileUuid: Uuid): List<Collection> =
        requireBody("getCollections", CollectionApi.getCollections())

    override suspend fun localUpsertAll(rows: List<Collection>) = repository.upsertAll(rows)
    override suspend fun localDelete(uuid: Uuid) = repository.deleteByUuid(uuid)
    override suspend fun localDeleteAll() = repository.deleteAll()
    override suspend fun localDeleteNotIn(keep: List<Uuid>) = repository.deleteNotIn(keep)

    override suspend fun pushCreate(entity: Collection) =
        requireSuccess("createCollection", CollectionApi.createCollection(entity))

    override suspend fun pushUpdate(entity: Collection) =
        requireSuccess("updateCollection", CollectionApi.updateCollection(entity))

    override suspend fun pushDelete(uuid: Uuid) =
        requireSuccess("deleteCollection", CollectionApi.deleteCollection(uuid))

    override suspend fun serverGet(uuid: Uuid): Collection? {
        val rows = requireBody("getCollections", CollectionApi.getCollections())
        return rows.firstOrNull { it.uuid == uuid }
    }
}

open class TagsSyncer(
    private val repository: TagsRepository = TagsRepository(),
) : Syncable<Tags> {

    override val kind = EntityKind.TAG
    override fun uuidOf(entity: Tags): Uuid = entity.uuid
    override fun labelOf(entity: Tags): String = entity.name
    override fun encode(entity: Tags): JsonObject = Json.encodeToJsonElement(entity).jsonObject
    override fun decode(data: JsonObject): Tags = Json.decodeFromJsonElement(data)

    override suspend fun fetchAll(profileUuid: Uuid): List<Tags> =
        requireBody("getTags", TagsApi.getTags())

    override suspend fun localUpsertAll(rows: List<Tags>) = repository.upsertAll(rows)
    override suspend fun localDelete(uuid: Uuid) = repository.deleteByUuid(uuid)
    override suspend fun localDeleteAll() = repository.deleteAll()
    override suspend fun localDeleteNotIn(keep: List<Uuid>) = repository.deleteNotIn(keep)

    override suspend fun pushCreate(entity: Tags) =
        requireSuccess("createTag", TagsApi.createTag(entity))

    override suspend fun pushUpdate(entity: Tags) =
        requireSuccess("updateTag", TagsApi.updateTag(entity))

    override suspend fun pushDelete(uuid: Uuid) =
        requireSuccess("deleteTag", TagsApi.deleteTag(uuid))

    override suspend fun serverGet(uuid: Uuid): Tags? {
        val rows = requireBody("getTags", TagsApi.getTags())
        return rows.firstOrNull { it.uuid == uuid }
    }
}
