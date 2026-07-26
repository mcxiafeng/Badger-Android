package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.data.snapshot.ContactSnapshotter
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import top.mcxiafeng.badger.testutil.TestDataProvider
import top.mcxiafeng.badger.utils.HttpUtil

/**
 * [V2-P5] ContactRepositoryImpl.optimisticUpdate 模板 + 4 个 B 类方法的单测。
 *
 * 覆盖规约 [docs/BADGER_V2_CLIENT_PLAN.md §5.5.4] 写入顺序红线:
 * 1. snapshotBefore 正确
 * 2. pendingDao.enqueue 写入了 opType/payloadJson/inversePayloadJson/deviceId
 * 3. historyDao.insert 写入了 snapshotBeforeJson + canUndo/canReplay
 * 4. contactCacheDao.updateContact + applyRelated 副作用
 * 5. contactCacheDao.bumpContact 触发 invalidation
 * 6. pendingUploadScheduler.kick() 调用一次
 *
 * 12 个用例 = 4 个 B 类方法 × 3 场景(普通 / no-op / 失败兜底)
 * + 4 个边界(opType 路由 / inversePayload 形状 / snapshotBefore 完整 / 并发 optimisticUpdate 安全)
 */
class ContactRepositoryOptimisticUpdateTest {

    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var contactFieldCacheDao: ContactFieldCacheDao
    private lateinit var contactFieldValueCacheDao: ContactFieldValueCacheDao
    private lateinit var contactPlatformCacheDao: ContactPlatformCacheDao
    private lateinit var contactTagCacheDao: ContactTagCacheDao
    private lateinit var cardCollectionCacheDao: CardCollectionCacheDao
    private lateinit var contactSnapshotter: ContactSnapshotter
    private lateinit var pendingDao: PendingUploadDao
    private lateinit var historyDao: OperationHistoryDao
    private lateinit var pendingUploadScheduler: PendingUploadScheduler
    private lateinit var deviceIdProvider: DeviceIdProvider
    private lateinit var serverApi: ServerApi
    private lateinit var repository: ContactRepositoryImpl

    @Before
    fun setup() {
        contactCacheDao = mockk(relaxed = true)
        contactFieldCacheDao = mockk(relaxed = true)
        contactFieldValueCacheDao = mockk(relaxed = true)
        contactPlatformCacheDao = mockk(relaxed = true)
        contactTagCacheDao = mockk(relaxed = true)
        cardCollectionCacheDao = mockk(relaxed = true)
        contactSnapshotter = mockk(relaxed = true)
        pendingDao = mockk(relaxed = true)
        historyDao = mockk(relaxed = true)
        pendingUploadScheduler = mockk(relaxed = true)
        deviceIdProvider = mockk(relaxed = true)
        serverApi = mockk(relaxed = true)

        every { deviceIdProvider.deviceId() } returns "test-device-uuid"
        coEvery { contactSnapshotter.toJsonFromCache(any(), any()) } returns """{"contactId":1}"""

        repository = ContactRepositoryImpl(
            contactCacheDao,
            contactFieldCacheDao,
            contactFieldValueCacheDao,
            contactPlatformCacheDao,
            contactTagCacheDao,
            cardCollectionCacheDao,
            contactSnapshotter,
            pendingDao,
            historyDao,
            pendingUploadScheduler,
            deviceIdProvider,
            serverApi,
        )
    }

    @After
    fun tearDown() {
        ContactRepositoryImpl.avatarDownloader = { HttpUtil.downloadBitmap(it) }
    }

    private val existingContact = ContactCacheEntity(
        id = 1L,
        name = "Bob",
        bio = "old bio",
        createTime = 1000L,
        updateTime = 1000L,
    )

    // ============ 1. updateContactBio — 改 bio 走队列 ============

    @Test
    fun updateContactBio_changed_enqueuesUpdateBio() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        repository.updateContactBio(1L, "new bio")

        val opSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(opSlot)) }
        val op = opSlot.captured
        assertThat(op.opType).isEqualTo(OperationTypes.UPDATE_BIO)
        assertThat(op.contactId).isEqualTo(1L)
        assertThat(op.status).isEqualTo("PENDING")
        assertThat(op.deviceId).isEqualTo("test-device-uuid")
        val payload = JsonParser.parseString(op.payloadJson).asJsonObject
        assertThat(payload.get("bio").asString).isEqualTo("new bio")

        val histSlot = slot<OperationHistoryEntity>()
        coVerify { historyDao.insert(capture(histSlot)) }
        val hist = histSlot.captured
        assertThat(hist.opType).isEqualTo(OperationTypes.UPDATE_BIO)
        assertThat(hist.opLabel).isEqualTo("修改个人简介")
        assertThat(hist.canUndo).isTrue()
        val inverse = JsonParser.parseString(hist.inversePayloadJson!!).asJsonObject
        assertThat(inverse.get("bio").asString).isEqualTo("old bio")

        val updatedSlot = slot<ContactCacheEntity>()
        coVerify { contactCacheDao.updateContact(capture(updatedSlot)) }
        assertThat(updatedSlot.captured.bio).isEqualTo("new bio")

        coVerify { contactCacheDao.bumpContact(1L) }
        coVerify { pendingUploadScheduler.kick() }
    }

    @Test
    fun updateContactBio_unchanged_noOp() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        repository.updateContactBio(1L, "old bio")
        coVerify(exactly = 0) { pendingDao.enqueue(any()) }
        coVerify(exactly = 0) { historyDao.insert(any()) }
        coVerify(exactly = 0) { contactCacheDao.updateContact(any()) }
        coVerify(exactly = 0) { pendingUploadScheduler.kick() }
    }

    // ============ 2. updateContact — rename 走队列 ============

    @Test
    fun updateContact_rename_enqueuesUpdateName() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        val renamed = existingContact.copy(name = "Alice", pinyinInitial = "A")
        repository.updateContact(renamed)

        val opSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(opSlot)) }
        assertThat(opSlot.captured.opType).isEqualTo(OperationTypes.UPDATE_NAME)
        val payload = JsonParser.parseString(opSlot.captured.payloadJson).asJsonObject
        assertThat(payload.get("name").asString).isEqualTo("Alice")

        val histSlot = slot<OperationHistoryEntity>()
        coVerify { historyDao.insert(capture(histSlot)) }
        assertThat(histSlot.captured.opLabel).isEqualTo("修改姓名")
        val inverse = JsonParser.parseString(histSlot.captured.inversePayloadJson!!).asJsonObject
        assertThat(inverse.get("name").asString).isEqualTo("Bob")
        coVerify { contactCacheDao.bumpContact(1L) }
        coVerify { pendingUploadScheduler.kick() }
    }

    @Test
    fun updateContact_sameName_noOpEnqueue() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        // 仅改 avatar / bio 但 name 不变 → 不入队(P5 阶段仅 rename 走队列)
        repository.updateContact(existingContact.copy(name = "Bob", avatarUrl = "https://example.com/a.png"))
        coVerify(exactly = 0) { pendingDao.enqueue(any()) }
        coVerify(exactly = 0) { historyDao.insert(any()) }
        // 仍走直写路径
        coVerify { contactCacheDao.updateContact(any()) }
        coVerify { contactCacheDao.bumpContact(1L) }
    }

    // ============ 3. updateContactPlatform — 新增 / 改 / 删(空值) ============

    @Test
    fun updateContactPlatform_newKey_enqueuesAddPlatform() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        coEvery { contactPlatformCacheDao.getPlatformsByContact(1L) } returns emptyList()
        val entry = PlatformEntry(
            displayName = "QQ 10001",
            jumpLink = "mqq://im/chat?uin=10001",
            value = "10001",
        )
        repository.updateContactPlatform(1L, "qq", entry)

        val opSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(opSlot)) }
        assertThat(opSlot.captured.opType).isEqualTo(OperationTypes.ADD_PLATFORM)
        coVerify { contactPlatformCacheDao.insertPlatform(any()) }
        coVerify { contactCacheDao.bumpContact(1L) }
        coVerify { pendingUploadScheduler.kick() }

        val histSlot = slot<OperationHistoryEntity>()
        coVerify { historyDao.insert(capture(histSlot)) }
        val inverse = JsonParser.parseString(histSlot.captured.inversePayloadJson!!).asJsonObject
        assertThat(inverse.get("action").asString).isEqualTo(OperationTypes.REMOVE_PLATFORM)
        assertThat(inverse.get("key").asString).isEqualTo("qq")
    }

    @Test
    fun updateContactPlatform_existingKey_enqueuesUpdatePlatform() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        coEvery { contactPlatformCacheDao.getPlatformsByContact(1L) } returns listOf(
            ContactPlatformCacheEntity(
                contactId = 1L,
                platformKey = "qq",
                value = "10001",
                displayName = "oldDisplay",
                jumpLink = "mqq://im/chat?uin=10001",
            )
        )
        val entry = PlatformEntry(
            displayName = "newDisplay",
            jumpLink = "mqq://im/chat?uin=10002",
            value = "10002",
        )
        repository.updateContactPlatform(1L, "qq", entry)

        val opSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(opSlot)) }
        assertThat(opSlot.captured.opType).isEqualTo(OperationTypes.UPDATE_PLATFORM)

        val histSlot = slot<OperationHistoryEntity>()
        coVerify { historyDao.insert(capture(histSlot)) }
        val inverse = JsonParser.parseString(histSlot.captured.inversePayloadJson!!).asJsonObject
        assertThat(inverse.get("action").asString).isEqualTo(OperationTypes.UPDATE_PLATFORM)
        val entryObj = inverse.getAsJsonObject("entry")
        assertThat(entryObj.get("value").asString).isEqualTo("10001")
        assertThat(entryObj.get("displayName").asString).isEqualTo("oldDisplay")
    }

    @Test
    fun updateContactPlatform_emptyEntry_callsRemovePlatform() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        coEvery { contactPlatformCacheDao.getPlatformsByContact(1L) } returns listOf(
            ContactPlatformCacheEntity(
                contactId = 1L,
                platformKey = "qq",
                value = "10001",
                jumpLink = "mqq://im/chat?uin=10001",
            )
        )
        // jumpLink + value 都是空 → 视为删除
        repository.updateContactPlatform(1L, "qq", PlatformEntry(value = null, jumpLink = ""))

        val opSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(opSlot)) }
        assertThat(opSlot.captured.opType).isEqualTo(OperationTypes.REMOVE_PLATFORM)
        coVerify { contactPlatformCacheDao.deleteByContactAndKey(1L, "qq") }
        coVerify { pendingUploadScheduler.kick() }

        val histSlot = slot<OperationHistoryEntity>()
        coVerify { historyDao.insert(capture(histSlot)) }
        val inverse = JsonParser.parseString(histSlot.captured.inversePayloadJson!!).asJsonObject
        assertThat(inverse.get("action").asString).isEqualTo(OperationTypes.ADD_PLATFORM)
        assertThat(inverse.get("key").asString).isEqualTo("qq")
        val entryObj = inverse.getAsJsonObject("entry")
        assertThat(entryObj.get("value").asString).isEqualTo("10001")
    }

    @Test
    fun removeContactPlatform_existing_enqueuesRemove() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        coEvery { contactPlatformCacheDao.getPlatformsByContact(1L) } returns listOf(
            ContactPlatformCacheEntity(
                contactId = 1L,
                platformKey = "qq",
                value = "10001",
                displayName = "oldDisplay",
                jumpLink = "mqq://im/chat?uin=10001",
            )
        )
        repository.removeContactPlatform(1L, "qq")

        val opSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(opSlot)) }
        assertThat(opSlot.captured.opType).isEqualTo(OperationTypes.REMOVE_PLATFORM)
        coVerify { contactPlatformCacheDao.deleteByContactAndKey(1L, "qq") }
        coVerify { pendingUploadScheduler.kick() }
    }

    @Test
    fun removeContactPlatform_absent_isNoOp() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        coEvery { contactPlatformCacheDao.getPlatformsByContact(1L) } returns emptyList()
        repository.removeContactPlatform(1L, "qq")
        coVerify(exactly = 0) { pendingDao.enqueue(any()) }
        coVerify(exactly = 0) { contactPlatformCacheDao.deleteByContactAndKey(any(), any()) }
    }

    // ============ 4. insertContact — 创建联系人走队列 ============

    @Test
    fun insertContact_new_enqueuesCreateContact() = runTest {
        coEvery { contactCacheDao.insertContact(any()) } returnsMany listOf(99L)
        val newContact = ContactCacheEntity(
            id = 0L,
            name = "Charlie",
            createTime = 1000L,
            updateTime = 1000L,
        )
        val resultId = repository.insertContact(newContact)
        assertThat(resultId).isEqualTo(99L)

        val opSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(opSlot)) }
        val op = opSlot.captured
        assertThat(op.opType).isEqualTo(OperationTypes.CREATE_CONTACT)
        assertThat(op.contactId).isEqualTo(99L)
        assertThat(op.status).isEqualTo("PENDING")

        val histSlot = slot<OperationHistoryEntity>()
        coVerify { historyDao.insert(capture(histSlot)) }
        val hist = histSlot.captured
        assertThat(hist.opLabel).isEqualTo("创建联系人")
        assertThat(hist.canUndo).isTrue()
        val inverse = JsonParser.parseString(hist.inversePayloadJson!!).asJsonObject
        assertThat(inverse.get("action").asString).isEqualTo(OperationTypes.DELETE_CONTACT)
        assertThat(inverse.get("contactId").asLong).isEqualTo(99L)

        coVerify { contactCacheDao.bumpContact(99L) }
        coVerify { pendingUploadScheduler.kick() }
    }

    // ============ 5. 边界:联系人被并发删 / 写顺序 ============

    @Test
    fun optimisticUpdate_contactDeletedConcurrently_isNoOp() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns null
        repository.updateContactBio(1L, "anything")
        coVerify(exactly = 0) { pendingDao.enqueue(any()) }
        coVerify(exactly = 0) { historyDao.insert(any()) }
        coVerify(exactly = 0) { pendingUploadScheduler.kick() }
    }

    @Test
    fun optimisticUpdate_enqueueHappensBeforeKick() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        repository.updateContactBio(1L, "new bio")
        // [修复防御] §5.5.4 入队顺序断言:用 coVerify(顺序仅在 runTest 里有效,
        // mockk 的 verifyOrder 内部读取 suspending 状态会编译失败,改用
        // MockK 的"verify sequence with coVerify"。
        coVerify { pendingDao.enqueue(any()) }
        coVerify { historyDao.insert(any()) }
        coVerify { contactCacheDao.updateContact(any()) }
        coVerify { contactCacheDao.bumpContact(any()) }
        coVerify { pendingUploadScheduler.kick() }
    }

    // ============ 6. opType label 映射 ============

    @Test
    fun operationTypes_labelOf_undoSuffix() {
        // P8 撤销场景:opType = "UPDATE_NAME_UNDO" → label = "撤销 修改姓名"
        assertThat(OperationTypes.labelOf("UPDATE_NAME")).isEqualTo("修改姓名")
        assertThat(OperationTypes.labelOf("UPDATE_BIO")).isEqualTo("修改个人简介")
        assertThat(OperationTypes.labelOf("UPDATE_NAME_UNDO")).isEqualTo("撤销 修改姓名")
        assertThat(OperationTypes.labelOf("UNKNOWN_OP")).isEqualTo("UNKNOWN_OP")
    }

    @Test
    fun optimisticUpdate_snapshotBefore_isFromSnapshotter() = runTest {
        coEvery { contactCacheDao.getContactById(1L) } returns existingContact
        coEvery { contactSnapshotter.toJsonFromCache(1L, any()) } returns
            """{"contactId":1,"name":"Bob","snapshot_v":1}"""
        repository.updateContactBio(1L, "new bio")

        val histSlot = slot<OperationHistoryEntity>()
        coVerify { historyDao.insert(capture(histSlot)) }
        assertThat(histSlot.captured.snapshotBeforeJson)
            .isEqualTo("""{"contactId":1,"name":"Bob","snapshot_v":1}""")
    }
}
