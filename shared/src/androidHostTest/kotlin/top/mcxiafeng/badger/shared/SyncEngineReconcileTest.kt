package top.mcxiafeng.badger.shared

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.PersonRepository
import top.mcxiafeng.badger.data.repository.ProfileRepository
import top.mcxiafeng.badger.data.repository.SystemRepository
import top.mcxiafeng.badger.data.repository.TagsRepository
import top.mcxiafeng.badger.data.repository.UserRepository
import top.mcxiafeng.badger.data.system.database.SystemDatabase
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.data.system.entity.UserInfo
import top.mcxiafeng.badger.data.user.database.CacheDatabase
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.data.user.entity.User
import top.mcxiafeng.badger.sync.AtomPayload
import top.mcxiafeng.badger.sync.CollectionSyncer
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.PersonSyncer
import top.mcxiafeng.badger.sync.ProfileSyncer
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.sync.SyncGatewayException
import top.mcxiafeng.badger.sync.SyncOutcome
import top.mcxiafeng.badger.sync.SyncType
import top.mcxiafeng.badger.sync.TagsSyncer
import top.mcxiafeng.badger.utils.HttpResult
import kotlin.uuid.Uuid

/**
 * SyncEngine 和解判定表单测（FakeSyncer + 内存双库，不依赖真实后端）。
 * 假适配器继承真实 Syncer、只覆写服务端原语——本地链路（避让/生产者）走真实代码。
 * 覆盖用户裁定语义：以服务端为主；先拉取查存在性；改了看 from 基线；
 * 无冲突直接推、有冲突弹提示二选一；失败记账留队重试；userUuid 门控。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineReconcileTest {

    private val userUuid = Uuid.random()
    private val personX = Uuid.random()
    private val personY = Uuid.random()

    private lateinit var cacheDb: CacheDatabase
    private lateinit var systemDb: SystemDatabase
    private lateinit var systemRepository: SystemRepository
    private lateinit var personRepository: PersonRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var tagsRepository: TagsRepository
    private lateinit var personSyncer: FakePersonSyncer
    private lateinit var profileSyncer: FakeProfileSyncer
    private lateinit var collectionSyncer: FakeCollectionSyncer
    private lateinit var tagSyncer: FakeTagsSyncer
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        cacheDb = TestSession.cacheDb(context).build()
        systemDb = TestSession.systemDb(context).build()
        systemRepository = SystemRepository(systemDb.syncAtomDao(), systemDb.userInfoDao())
        personRepository = PersonRepository(cacheDb.personDao(), systemRepository)
        profileRepository = ProfileRepository(cacheDb.profileDao(), systemRepository)
        tagsRepository = TagsRepository(cacheDb.tagsDao(), systemRepository)
        personSyncer = FakePersonSyncer(personRepository)
        profileSyncer = FakeProfileSyncer(profileRepository)
        collectionSyncer = FakeCollectionSyncer(CollectionRepository(cacheDb.collectionDao(), systemRepository))
        tagSyncer = FakeTagsSyncer(tagsRepository)
        engine = SyncEngine(
            systemRepository = systemRepository,
            userRepository = UserRepository(cacheDb.userDao()),
            personSyncer = personSyncer,
            profileSyncer = profileSyncer,
            collectionSyncer = collectionSyncer,
            tagSyncer = tagSyncer,
        )
    }

    @After
    fun tearDown() {
        cacheDb.close()
        systemDb.close()
    }

    @Test
    fun `未登录 - 跳过同步不发请求`() = runTest {
        val outcome = engine.syncNow(EntityKind.PERSON)
        assertEquals(SyncOutcome.SKIPPED, outcome)
        assertEquals(0, personSyncer.pullCalls)
    }

    @Test
    fun `拉取失败 - 类型化为 pullFailed 不崩不和解`() = runTest {
        seedSession()
        val x = person(personX, "离线新增")
        cacheDb.personDao().upsertPerson(x)
        enqueueInsert(x)
        personSyncer.failOp = "getPersons"

        val outcome = engine.syncNow(EntityKind.PERSON)

        assertTrue(outcome.pullFailed)
        assertTrue(personSyncer.created.isEmpty())
        assertTrue(systemRepository.getAllAtoms().isNotEmpty())
        assertEquals(x, cacheDb.personDao().getPerson(x.uuid))
    }

    @Test
    fun `离线新增 - 服务端没有 - 推送 create 并销账`() = runTest {
        seedSession()
        val x = person(personX, "离线新增")
        cacheDb.personDao().upsertPerson(x)
        enqueueInsert(x)

        engine.syncNow(EntityKind.PERSON)

        assertEquals(listOf(x), personSyncer.created)
        assertTrue(systemRepository.getAllAtoms().isEmpty())
        assertEquals(x, cacheDb.personDao().getPerson(x.uuid))
    }

    @Test
    fun `离线新增 - 服务端已有同uuid - 采纳服务端销账不重复建`() = runTest {
        seedSession()
        val localDraft = person(personX, "本地草稿")
        val serverVersion = person(personX, "服务端版本")
        cacheDb.personDao().upsertPerson(localDraft)
        personSyncer.serverPersons[personX] = serverVersion
        enqueueInsert(localDraft)

        engine.syncNow(EntityKind.PERSON)

        assertTrue(personSyncer.created.isEmpty())
        assertEquals("服务端版本", cacheDb.personDao().getPerson(personX)?.name)
        assertTrue(systemRepository.getAllAtoms().isEmpty())
    }

    @Test
    fun `离线删除 - 服务端还有 - 推送 delete 并销账`() = runTest {
        seedSession()
        personSyncer.serverPersons[personX] = person(personX, "服务端还有")
        systemRepository.enqueueAtom(
            EntityKind.PERSON, SyncType.DELETE, AtomPayload.delete(personX),
        )

        engine.syncNow(EntityKind.PERSON)

        assertEquals(listOf(personX), personSyncer.deleted)
        assertTrue(systemRepository.getAllAtoms().isEmpty())
    }

    @Test
    fun `离线删除 - 服务端已没有 - 幂等销账不推送`() = runTest {
        seedSession()
        systemRepository.enqueueAtom(
            EntityKind.PERSON, SyncType.DELETE, AtomPayload.delete(personX),
        )

        engine.syncNow(EntityKind.PERSON)

        assertTrue(personSyncer.deleted.isEmpty())
        assertTrue(systemRepository.getAllAtoms().isEmpty())
    }

    @Test
    fun `离线修改 - 服务端等于基线 - 无冲突推送 to`() = runTest {
        seedSession()
        val base = person(personX, "基线名")
        val edited = base.copy(name = "新名")
        cacheDb.personDao().upsertPerson(edited)
        personSyncer.serverPersons[personX] = base
        enqueueUpdate(from = base, to = edited)

        engine.syncNow(EntityKind.PERSON)

        assertEquals(listOf(edited), personSyncer.updated)
        assertTrue(systemRepository.getAllAtoms().isEmpty())
        assertTrue(engine.conflicts.value.isEmpty())
    }

    @Test
    fun `离线修改 - 服务端已变 - 挂冲突不推送`() = runTest {
        seedSession()
        val base = person(personX, "基线名")
        val edited = base.copy(name = "我的修改")
        val serverChanged = base.copy(name = "别人的修改")
        cacheDb.personDao().upsertPerson(edited)
        personSyncer.serverPersons[personX] = serverChanged
        enqueueUpdate(from = base, to = edited)

        engine.syncNow(EntityKind.PERSON)

        assertTrue(personSyncer.updated.isEmpty())
        assertTrue(systemRepository.getAllAtoms().isNotEmpty())
        val conflict = engine.conflicts.value.single()
        assertEquals(personX, conflict.objectId)
        // 本地修改在裁决前保持原样
        assertEquals("我的修改", cacheDb.personDao().getPerson(personX)?.name)
    }

    @Test
    fun `冲突裁决 - 保留我的 - 重推本地值并销账`() = runTest {
        seedSession()
        val base = person(personX, "基线名")
        val edited = base.copy(name = "我的修改")
        personSyncer.serverPersons[personX] = base.copy(name = "别人的修改")
        cacheDb.personDao().upsertPerson(edited)
        enqueueUpdate(from = base, to = edited)
        engine.syncNow(EntityKind.PERSON)
        val atomId = systemRepository.getAllAtoms().single().id

        engine.resolveConflict(atomId, keepLocal = true)

        assertEquals(listOf(edited), personSyncer.updated)
        assertTrue(systemRepository.getAllAtoms().isEmpty())
        assertTrue(engine.conflicts.value.isEmpty())
    }

    @Test
    fun `冲突裁决 - 用服务端的 - 覆盖本地并销账`() = runTest {
        seedSession()
        val base = person(personX, "基线名")
        val serverChanged = base.copy(name = "别人的修改")
        cacheDb.personDao().upsertPerson(base.copy(name = "我的修改"))
        personSyncer.serverPersons[personX] = serverChanged
        enqueueUpdate(from = base, to = base.copy(name = "我的修改"))
        engine.syncNow(EntityKind.PERSON)
        val atomId = systemRepository.getAllAtoms().single().id

        engine.resolveConflict(atomId, keepLocal = false)

        assertTrue(personSyncer.updated.isEmpty())
        assertEquals("别人的修改", cacheDb.personDao().getPerson(personX)?.name)
        assertTrue(systemRepository.getAllAtoms().isEmpty())
        assertTrue(engine.conflicts.value.isEmpty())
    }

    @Test
    fun `冲突裁决期间服务端删除 - 用服务端的落实为删除本地行`() = runTest {
        seedSession()
        val base = person(personX, "基线名")
        cacheDb.personDao().upsertPerson(base.copy(name = "我的修改"))
        personSyncer.serverPersons[personX] = base.copy(name = "别人的修改")
        enqueueUpdate(from = base, to = base.copy(name = "我的修改"))
        engine.syncNow(EntityKind.PERSON)
        val atomId = systemRepository.getAllAtoms().single().id

        // 弹窗挂起期间他端删除该实体（分布式竞态）
        personSyncer.serverPersons.remove(personX)

        engine.resolveConflict(atomId, keepLocal = false)

        // 采纳"删除"语义：本地行移除、销账，不崩
        assertTrue(personSyncer.updated.isEmpty())
        assertEquals(null, cacheDb.personDao().getPerson(personX))
        assertTrue(systemRepository.getAllAtoms().isEmpty())
        assertTrue(engine.conflicts.value.isEmpty())
    }

    @Test
    fun `同轮建了又删 - INSERT推送后DELETE落实删除不复活`() = runTest {
        seedSession()
        val x = person(personX, "建了又删")
        personRepository.createLocal(x)
        personRepository.deleteLocal(x.uuid)

        engine.syncNow(EntityKind.PERSON)

        // 本轮 INSERT 推送创建服务端行后，DELETE 落实删除意图：服务端不残留、本地不复活
        assertEquals(listOf(x), personSyncer.created)
        assertEquals(listOf(personX), personSyncer.deleted)
        assertTrue(systemRepository.getAllAtoms().isEmpty())
        assertEquals(null, cacheDb.personDao().getPerson(x.uuid))
    }

    @Test
    fun `范围同步 - 只拉取指定实体且范围外 atom 留队`() = runTest {
        seedSession()
        val x = person(personX, "离线新增")
        cacheDb.personDao().upsertPerson(x)
        enqueueInsert(x)
        val collectionAtomId = systemRepository.enqueueAtom(
            EntityKind.COLLECTION, SyncType.DELETE, AtomPayload.delete(Uuid.random()),
        )

        // 联系人页刷新：只碰 PERSON
        engine.syncNow(EntityKind.PERSON)

        assertEquals(1, personSyncer.pullCalls)
        assertEquals(0, collectionSyncer.pullCalls)
        assertEquals(0, profileSyncer.profileSyncerPullCalls)
        // PERSON atom 已和解销账，范围外的 COLLECTION atom 原样留队
        assertTrue(systemRepository.getAllAtoms().any { it.id == collectionAtomId })
        assertTrue(systemRepository.getAllAtoms().none { it.entityKind == EntityKind.PERSON })
    }

    // ---------------- pushQueue（全局时刻只发队列） ----------------

    @Test
    fun `pushQueue - 队列空 - 零网络`() = runTest {
        seedSession()

        val outcome = engine.pushQueue()

        assertEquals(SyncOutcome(), outcome)
        assertEquals(0, personSyncer.pullCalls)
        assertEquals(0, profileSyncer.profileSyncerPullCalls)
    }

    @Test
    fun `pushQueue - 零清单拉取 - 逐条单查定案且不刷新缓存`() = runTest {
        seedSession()
        val x = person(personX, "离线新增")
        cacheDb.personDao().upsertPerson(x)
        enqueueInsert(x)
        personSyncer.serverPersons[personY] = person(personY, "服务端其他联系人")

        val outcome = engine.pushQueue()

        // 队列意图被推送（serverGet 单查判无 → pushCreate）
        assertEquals(listOf(x), personSyncer.created)
        assertTrue(systemRepository.getAllAtoms().isEmpty())
        // 零清单拉取（查重走单查接口）；不为展示刷新缓存（personY 不落库）
        assertEquals(0, personSyncer.pullCalls)
        assertEquals(0, collectionSyncer.pullCalls)
        assertEquals(0, profileSyncer.profileSyncerPullCalls)
        assertEquals(null, cacheDb.personDao().getPerson(personY))
    }

    // ---------------- 失败语义回归（P0-2：网络失败绝不折成"服务端已删除"） ----------------

    @Test
    fun `UPDATE 单查网络失败 - 留队记账不销账不删本地行`() = runTest {
        seedSession()
        val baseline = person(personX, "基线名")
        val edited = baseline.copy(name = "我的修改")
        cacheDb.personDao().upsertPerson(edited)
        personSyncer.serverPersons[personX] = baseline
        enqueueUpdate(from = baseline, to = edited)
        personSyncer.failOp = "getPerson"

        engine.syncNow(EntityKind.PERSON)

        // 单查失败 = SGE 留队，绝不按"服务端已删除"处理
        val atom = systemRepository.getAllAtoms().single()
        assertEquals(1, atom.attempts)
        // 本地修改原样保留
        assertEquals("我的修改", cacheDb.personDao().getPerson(personX)?.name)
    }

    @Test
    fun `DELETE 单查网络失败 - 留队记账不销账`() = runTest {
        seedSession()
        personSyncer.serverPersons[personX] = person(personX, "服务端还有")
        systemRepository.enqueueAtom(
            EntityKind.PERSON, SyncType.DELETE, AtomPayload.delete(personX),
        )
        personSyncer.failOp = "getPerson"

        engine.syncNow(EntityKind.PERSON)

        assertTrue(systemRepository.getAllAtoms().isNotEmpty())
        assertEquals(1, systemRepository.getAllAtoms().single().attempts)
    }

    @Test
    fun `UPDATE 服务端已是目标值 - 补销账不重推`() = runTest {
        seedSession()
        val baseline = person(personX, "基线名")
        val edited = baseline.copy(name = "我的修改")
        cacheDb.personDao().upsertPerson(edited)
        // 服务端已是 to：上轮推送成功但销账前中断
        personSyncer.serverPersons[personX] = edited
        enqueueUpdate(from = baseline, to = edited)

        engine.syncNow(EntityKind.PERSON)

        assertTrue(systemRepository.getAllAtoms().isEmpty())
        assertTrue(personSyncer.updated.isEmpty())
        assertTrue(engine.conflicts.value.isEmpty())
    }

    @Test
    fun `冲突裁决 - 其他用户的 atom - 拒绝裁决不推送`() = runTest {
        seedSession()
        systemDb.syncAtomDao().insertAtom(
            SyncAtom(
                userUuid = Uuid.random(), // 他账号的意图
                entityKind = EntityKind.PERSON,
                syncType = SyncType.DELETE,
                data = AtomPayload.delete(personX),
                createdAt = 1L,
            )
        )
        val foreignAtomId = systemRepository.getAllAtoms().single().id

        engine.resolveConflict(foreignAtomId, keepLocal = true)

        // 保险丝：atom 原样留队，本地零动作
        assertTrue(systemRepository.getAllAtoms().isNotEmpty())
        assertTrue(personSyncer.deleted.isEmpty())
        assertTrue(personSyncer.updated.isEmpty())
    }

    @Test
    fun `落库避让 - pending 实体不被服务端覆盖且不被清理`() = runTest {
        seedSession()
        val localEdit = person(personX, "本地未推送修改")
        val serverVersion = person(personX, "服务端版本")
        val other = person(personY, "服务端其他联系人")
        cacheDb.personDao().upsertPerson(localEdit)
        personSyncer.serverPersons[personX] = serverVersion
        personSyncer.serverPersons[personY] = other
        enqueueUpdate(from = localEdit.copy(name = "基线名"), to = localEdit)

        engine.syncNow(EntityKind.PERSON)

        // 服务端行不覆盖本地 pending 修改，也不被 deleteNotIn 清掉
        assertEquals("本地未推送修改", cacheDb.personDao().getPerson(personX)?.name)
        // 其他服务端实体照常入库
        assertEquals(other, cacheDb.personDao().getPerson(personY))
        // 该实体按判定表挂冲突
        assertEquals(1, engine.conflicts.value.size)
    }

    @Test
    fun `落库避让 - 离线删除行不被服务端行复活`() = runTest {
        seedSession()
        personSyncer.serverPersons[personX] = person(personX, "服务端还有")
        systemRepository.enqueueAtom(
            EntityKind.PERSON, SyncType.DELETE, AtomPayload.delete(personX),
        )

        engine.syncNow(EntityKind.PERSON)

        assertEquals(null, cacheDb.personDao().getPerson(personX))
        assertTrue(systemRepository.getAllAtoms().isEmpty())
    }

    @Test
    fun `落库避让 - 离线新增不被 deleteNotIn 清掉`() = runTest {
        seedSession()
        val offlineAdd = person(personX, "离线新增")
        cacheDb.personDao().upsertPerson(offlineAdd)
        personSyncer.serverPersons[personY] = person(personY, "服务端其他联系人")
        enqueueInsert(offlineAdd)

        engine.syncNow(EntityKind.PERSON)

        assertEquals(offlineAdd, cacheDb.personDao().getPerson(personX))
        assertEquals("服务端其他联系人", cacheDb.personDao().getPerson(personY)?.name)
    }

    @Test
    fun `推送失败 - 记 attempts 留队下轮重试`() = runTest {
        seedSession()
        val x = person(personX, "离线新增")
        cacheDb.personDao().upsertPerson(x)
        enqueueInsert(x)
        personSyncer.failOp = "createPerson"

        engine.syncNow(EntityKind.PERSON)
        val atom = systemRepository.getAllAtoms().single()
        assertEquals(1, atom.attempts)
        assertTrue(atom.lastError?.contains("createPerson") == true)
        assertEquals(x, cacheDb.personDao().getPerson(x.uuid))

        // 下轮恢复后成功销账
        personSyncer.failOp = null
        engine.syncNow(EntityKind.PERSON)
        assertTrue(systemRepository.getAllAtoms().isEmpty())
    }

    @Test
    fun `生产者 - person createLocal 入 INSERT updateLocal 带 from-to 基线`() = runTest {
        seedSession()
        val x = person(personX, "新联系人")
        personRepository.createLocal(x)
        val insertAtom = systemRepository.getAllAtoms().single()
        assertEquals(EntityKind.PERSON, insertAtom.entityKind)
        assertEquals(SyncType.INSERT, insertAtom.syncType)
        // 原子携带用户 UUID：引擎按会话门控，掉登录不影响队列
        assertEquals(userUuid, insertAtom.userUuid)

        personRepository.updateLocal(x.copy(name = "改名"))
        val updateAtom = systemRepository.getAllAtoms().last()
        assertEquals(SyncType.UPDATE, updateAtom.syncType)
        assertEquals("新联系人", Json.decodeFromJsonElement<Person>(AtomPayload.fromOf(updateAtom.data)).name)
        assertEquals("改名", Json.decodeFromJsonElement<Person>(AtomPayload.toOf(updateAtom.data)).name)
    }

    @Test
    fun `生产者 - profile updateLocal 入队 UPDATE 意图`() = runTest {
        seedSession()
        val profile = Profile(uuid = userUuid, description = "旧简介")
        profileRepository.upsert(profile)
        profileRepository.updateLocal(profile.copy(description = "新简介"))
        val atom = systemRepository.getAllAtoms().single()
        assertEquals(EntityKind.PROFILE, atom.entityKind)
        assertEquals(SyncType.UPDATE, atom.syncType)
        assertEquals("新简介", Json.decodeFromJsonElement<Profile>(AtomPayload.toOf(atom.data)).description)
    }

    // ---------------- 种子与假适配器 ----------------

    private suspend fun seedSession() {
        systemDb.userInfoDao().clearAllUserInfos()
        systemDb.userInfoDao().upsertUserInfo(UserInfo(userUuid = userUuid, token = "test-token"))
        // 引擎拉取前要经 UserRepository 查 User 行拿 profileUuid
        cacheDb.userDao().upsertUser(
            User(
                uuid = userUuid,
                profileUuid = userUuid,
                name = "tester",
                displayName = "tester",
                token = "test-token",
                avatar = "",
                email = "",
                isAdmin = false,
                userSettings = JsonObject(emptyMap()),
                syncVersion = 0L,
                lastLogin = 0L,
                createTime = 0L,
            )
        )
        profileSyncer.ownProfile = Profile(uuid = userUuid)
    }

    private suspend fun enqueueInsert(person: Person) {
        systemRepository.enqueueAtom(
            EntityKind.PERSON, SyncType.INSERT, AtomPayload.insert(personJson(person)),
        )
    }

    private suspend fun enqueueUpdate(from: Person, to: Person) {
        systemRepository.enqueueAtom(
            EntityKind.PERSON, SyncType.UPDATE, AtomPayload.update(personJson(from), personJson(to)),
        )
    }

    private fun person(uuid: Uuid, name: String): Person = Person(
        uuid = uuid,
        ownerId = userUuid,
        profileId = Uuid.random(),
        name = name,
        avatarURL = null,
        createTime = 1_000L,
        updateTime = 1_000L,
    )

    private fun personJson(person: Person) = Json.encodeToJsonElement(person).jsonObject

    /** 继承真实 PersonSyncer：本地原语走真实仓储，只覆写服务端原语。 */
    private class FakePersonSyncer(repository: PersonRepository) : PersonSyncer(repository) {

        val serverPersons = mutableMapOf<Uuid, Person>()
        val created = mutableListOf<Person>()
        val updated = mutableListOf<Person>()
        val deleted = mutableListOf<Uuid>()
        var failOp: String? = null
        var pullCalls = 0

        private fun check(op: String) {
            if (failOp == op) throw SyncGatewayException(op, HttpResult.ErrorType.NETWORK, 0)
        }

        override suspend fun fetchAll(profileUuid: Uuid): List<Person> {
            check("getPersons")
            pullCalls++
            return serverPersons.values.toList()
        }

        override suspend fun pushCreate(entity: Person) {
            check("createPerson")
            serverPersons[entity.uuid] = entity
            created += entity
        }

        override suspend fun pushUpdate(entity: Person) {
            check("updatePerson")
            serverPersons[entity.uuid] = entity
            updated += entity
        }

        override suspend fun pushDelete(uuid: Uuid) {
            check("deletePerson")
            serverPersons.remove(uuid)
            deleted += uuid
        }

        override suspend fun serverGet(uuid: Uuid): Person? {
            check("getPerson")
            return serverPersons[uuid]
        }
    }

    private class FakeProfileSyncer(repository: ProfileRepository) : ProfileSyncer(repository) {

        var ownProfile: Profile? = null
        var profileSyncerPullCalls = 0

        override suspend fun fetchAll(profileUuid: Uuid): List<Profile> {
            profileSyncerPullCalls++
            return listOf(ownProfile ?: error("FakeProfileSyncer: ownProfile 未设置"))
        }

        override suspend fun pushUpdate(entity: Profile) {
            ownProfile = entity
        }

        override suspend fun serverGet(uuid: Uuid): Profile? = ownProfile
    }

    private class FakeCollectionSyncer(repository: CollectionRepository) : CollectionSyncer(repository) {

        var pullCalls = 0

        override suspend fun fetchAll(profileUuid: Uuid): List<Collection> {
            pullCalls++
            return emptyList()
        }
    }

    private class FakeTagsSyncer(repository: TagsRepository) : TagsSyncer(repository) {

        val serverTags = mutableMapOf<Uuid, Tags>()

        override suspend fun fetchAll(profileUuid: Uuid): List<Tags> = serverTags.values.toList()

        override suspend fun pushCreate(entity: Tags) {
            serverTags[entity.uuid] = entity
        }

        override suspend fun pushUpdate(entity: Tags) {
            serverTags[entity.uuid] = entity
        }

        override suspend fun pushDelete(uuid: Uuid) {
            serverTags.remove(uuid)
        }

        override suspend fun serverGet(uuid: Uuid): Tags? = serverTags[uuid]
    }
}
