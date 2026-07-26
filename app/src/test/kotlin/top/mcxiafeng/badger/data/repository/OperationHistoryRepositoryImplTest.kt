package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.data.snapshot.ContactSnapshotter
import top.mcxiafeng.badger.data.snapshot.RestoredContact
import top.mcxiafeng.badger.data.snapshot.SnapshotPlatformEntry
import top.mcxiafeng.badger.data.snapshot.SnapshotTagRef
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.ServerApi.ConflictException
import top.mcxiafeng.badger.network.ServerApi.ConflictResponse
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingUploadScheduler

/**
 * [V2-P7/P8/P10] OperationHistoryRepositoryImpl 测试。
 *
 * 覆盖 22 个核心契约(P7 8 + P8 6 + P10 8):
 * 1. observe join 联系人名
 * 2. 联系人已被删除 → name 兜底 null
 * 3. 联系人完全找不到 → name 兜底 null
 * 4. retry 触发 PendingUploadDao.retryNow + scheduler.kick
 * 5. withdraw 标两个 DAO 为 WITHDRAWN(P7 — 但 P8 扩展多了 cache 回滚 + 反向 op 入队)
 * 6. adoptLocal 标 DONE + 写 serverVersion(P8 扩展 — 真发 ServerApi.patchContact)
 * 7. adoptServer 标 DONE + 写 snapshotAfterJson(P8 扩展 — ContactSnapshotter.fromServerContact)
 * 8. retry 在已 WITHDRAWN/DONE 时返 Failure
 * 9. withdraw UPDATE_NAME 入反向 op 到队列(_UNDO 后缀)
 * 10. withdraw CREATE_CONTACT 入 DELETE_CONTACT 队列
 * 11. withdraw ADD_PLATFORM 只本地回滚不入队列(P8 MVP 范围)
 * 12. adoptLocal 200 → ServerApi.patchContact + 更新 cache serverVersion
 * 13. adoptLocal 409 → markConflict + Failure
 * 14. adoptServer → ContactSnapshotter.fromServerContact → 整体替换 cache
 * 15. [P10] batchRetry 仅 FAILED 走 retryNow + kick 1 次
 * 16. [P10] batchRetry 空列表 → Success(0, 0) 不 kick
 * 17. [P10] batchRetry 单条异常被 catch + 继续
 * 18. [P10] batchWithdraw 跳过 WITHDRAWN + canUndo=false
 * 19. [P10] batchWithdraw 调用 withdraw per op
 * 20. [P10] batchWithdraw 单条异常被 catch + 继续
 * 21. [P10] batchWithdraw CONFLICT 跳过(需单条解决)
 */
class OperationHistoryRepositoryImplTest {

    private lateinit var historyDao: OperationHistoryDao
    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var contactPlatformCacheDao: ContactPlatformCacheDao
    private lateinit var contactFieldValueCacheDao: ContactFieldValueCacheDao
    private lateinit var contactTagCacheDao: ContactTagCacheDao
    private lateinit var pendingDao: PendingUploadDao
    private lateinit var scheduler: PendingUploadScheduler
    private lateinit var contactSnapshotter: ContactSnapshotter
    private lateinit var serverApi: ServerApi
    private lateinit var deviceIdProvider: DeviceIdProvider
    private lateinit var repository: OperationHistoryRepositoryImpl

    @Before
    fun setup() {
        historyDao = mockk(relaxed = true)
        contactCacheDao = mockk(relaxed = true)
        contactPlatformCacheDao = mockk(relaxed = true)
        contactFieldValueCacheDao = mockk(relaxed = true)
        contactTagCacheDao = mockk(relaxed = true)
        pendingDao = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        contactSnapshotter = mockk(relaxed = true)
        serverApi = mockk(relaxed = true)
        deviceIdProvider = mockk(relaxed = true)
        every { deviceIdProvider.deviceId() } returns "test-device-uuid"
        repository = OperationHistoryRepositoryImpl(
            historyDao = historyDao,
            contactCacheDao = contactCacheDao,
            pendingDao = pendingDao,
            scheduler = scheduler,
            contactSnapshotter = contactSnapshotter,
            contactPlatformCacheDao = contactPlatformCacheDao,
            contactFieldValueCacheDao = contactFieldValueCacheDao,
            contactTagCacheDao = contactTagCacheDao,
            serverApi = serverApi,
            deviceIdProvider = deviceIdProvider,
        )
    }

    @After
    fun tearDown() {
        // mockk auto-clear
    }

    private fun entity(
        opId: String = "op-1",
        contactId: Long = 1L,
        opType: String = OperationTypes.UPDATE_NAME,
        opStatus: String = "DONE",
        canUndo: Boolean = true,
        canReplay: Boolean = false,
        snapshotBeforeJson: String? = """{"contact_id":1,"name":"old","version":1,"captured_at":1000,"platforms":{},"field_values":[],"tags":[]}""",
        inversePayloadJson: String? = """{"name":"old"}""",
        serverVersion: Long? = null,
    ) = OperationHistoryEntity(
        opId = opId,
        contactId = contactId,
        opType = opType,
        opLabel = OperationTypes.labelOf(opType),
        payloadJson = """{"name":"new"}""",
        snapshotBeforeJson = snapshotBeforeJson ?: "",
        snapshotAfterJson = null,
        createdAt = 1_000L,
        opStatus = opStatus,
        serverVersion = serverVersion,
        inversePayloadJson = inversePayloadJson,
        canUndo = canUndo,
        canReplay = canReplay,
    )

    private fun contact(id: Long, name: String, serverId: String? = null, serverVersion: Long = 0L) =
        ContactCacheEntity(
            id = id,
            serverId = serverId,
            name = name,
            createTime = 1L,
            updateTime = 1L,
            serverVersion = serverVersion,
        )

    private fun restoredContact(
        contactId: Long = 1L,
        name: String = "old",
    ) = RestoredContact(
        contact = ContactCacheEntity(
            id = contactId,
            name = name,
            createTime = 1_000L,
            updateTime = 1_000L,
        ),
        platforms = listOf(
            ContactPlatformCacheEntity(
                contactId = contactId,
                platformKey = "qq",
                value = "12345",
            ),
        ),
        fieldValues = emptyList(),
        tags = listOf(SnapshotTagRef(tagId = 99L, name = "tag-x")),
    )

    // ============ 1. observe join 联系人名 ============

    @Test
    fun observe_returnsCombinedHistoryWithContactNames() = runTest {
        val history = listOf(entity(opId = "op-1", contactId = 10L))
        val contacts = listOf(contact(10L, "Alice"))
        every { historyDao.observeRecent(100) } returns flowOf(history)
        every { contactCacheDao.getAllContacts() } returns flowOf(contacts)

        val collected = repository.observeHistory(filter = HistoryFilter.All, limit = 100).first()
        assertThat(collected).hasSize(1)
        assertThat(collected[0].contactName).isEqualTo("Alice")
        assertThat(collected[0].history.opId).isEqualTo("op-1")
    }

    // ============ 2. 联系人被删除 → name 兜底 null ============

    @Test
    fun observe_deletedContact_fallsBackToNull() = runTest {
        val history = listOf(entity(opId = "op-1", contactId = 10L))
        val contacts = emptyList<ContactCacheEntity>()
        every { historyDao.observeRecent(100) } returns flowOf(history)
        every { contactCacheDao.getAllContacts() } returns flowOf(contacts)

        val collected = repository.observeHistory(filter = HistoryFilter.All, limit = 100).first()
        assertThat(collected).hasSize(1)
        assertThat(collected[0].contactName).isNull()
    }

    // ============ 3. 联系人完全找不到 → name 兜底 null ============

    @Test
    fun observe_missingContact_fallsBackToNull() = runTest {
        val history = listOf(entity(opId = "op-1", contactId = 999L))
        val contacts = listOf(contact(10L, "Alice"))
        every { historyDao.observeRecent(100) } returns flowOf(history)
        every { contactCacheDao.getAllContacts() } returns flowOf(contacts)

        val collected = repository.observeHistory(filter = HistoryFilter.All, limit = 100).first()
        assertThat(collected).hasSize(1)
        assertThat(collected[0].contactName).isNull()
    }

    // ============ 4. retry 触发 PendingUploadDao.retryNow + scheduler.kick ============

    @Test
    fun retry_marksPendingAndKicksScheduler() = runTest {
        val op = PendingUploadEntity(
            opId = "op-1",
            contactId = 1L,
            opType = OperationTypes.UPDATE_NAME,
            resourceVersion = 0L,
            payloadJson = """{"name":"x"}""",
            createdAt = 1L,
            status = "FAILED",
            deviceId = "dev",
        )
        coEvery { pendingDao.getById("op-1") } returns op
        val result = repository.retry("op-1")
        assertThat(result).isEqualTo(HistoryOpResult.Success)
        coVerify { pendingDao.retryNow("op-1", any()) }
        coVerify { scheduler.kick() }
    }

    // ============ 5. withdraw (P7 行为 — 标 WITHDRAWN + cache 回滚 + 反向 op) ============

    @Test
    fun withdraw_marksBothPendingAndHistoryWithdrawn() = runTest {
        val ent = entity(canUndo = true)
        coEvery { historyDao.getById("op-1") } returns ent
        coEvery { contactSnapshotter.fromJson(any(), any()) } returns restoredContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "old", serverId = "srv-1", serverVersion = 5L)

        val result = repository.withdraw("op-1")

        assertThat(result).isEqualTo(HistoryOpResult.Success)
        coVerify { historyDao.markWithdrawn("op-1") }
        coVerify { pendingDao.markWithdrawn("op-1") }
    }

    // ============ 6. adoptLocal (P7 — 标 DONE + serverVersion;P8 真发 ServerApi) ============

    @Test
    fun adoptLocal_marksDoneWithServerVersion() = runTest {
        val ent = entity(opStatus = "CONFLICT", serverVersion = 99L)
        coEvery { historyDao.getById("op-1") } returns ent
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "new-name", serverId = "srv-1", serverVersion = 99L)
        coEvery { serverApi.patchContact(any(), any(), any()) } returns ServerApi.ContactResponse(
            id = "srv-1",
            serverId = "srv-1",
            version = 100L,
            contact = JsonObject().apply { addProperty("name", "new-name") },
        )

        val result = repository.adoptLocal("op-1")

        assertThat(result).isEqualTo(HistoryOpResult.Success)
        coVerify { serverApi.patchContact("srv-1", any(), ifMatch = 99L) }
        coVerify { historyDao.markDone("op-1", serverVersion = 100L, snapshotAfterJson = any()) }
        coVerify { pendingDao.markDone("op-1") }
    }

    // ============ 7. adoptServer (P7 — 标 DONE;P8 ContactSnapshotter + cache REPLACE) ============

    @Test
    fun adoptServer_marksDoneWithSnapshotAfter() = runTest {
        val ent = entity(opStatus = "CONFLICT", serverVersion = 7L)
        coEvery { historyDao.getById("op-1") } returns ent
        coEvery { contactSnapshotter.fromServerContact(any(), any()) } returns restoredContact(name = "server-side")
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "old", serverId = "srv-1")

        val serverJson = """{"name":"server-side"}"""
        val result = repository.adoptServer("op-1", serverJson)

        assertThat(result).isEqualTo(HistoryOpResult.Success)
        coVerify { historyDao.markDone("op-1", serverVersion = 7L, snapshotAfterJson = serverJson) }
        coVerify { pendingDao.markDone("op-1") }
        coVerify { contactPlatformCacheDao.deleteByContact(1L) }
        coVerify { contactPlatformCacheDao.insertPlatforms(any()) }
    }

    // ============ 8. retry 在已 WITHDRAWN 时返 Failure ============

    @Test
    fun retry_alreadyWithdrawn_returnsFailure() = runTest {
        val op = PendingUploadEntity(
            opId = "op-1",
            contactId = 1L,
            opType = OperationTypes.UPDATE_NAME,
            resourceVersion = 0L,
            payloadJson = """{"name":"x"}""",
            createdAt = 1L,
            status = "WITHDRAWN",
            deviceId = "dev",
        )
        coEvery { pendingDao.getById("op-1") } returns op
        val result = repository.retry("op-1")
        assertThat(result).isInstanceOf(HistoryOpResult.Failure::class.java)
        coVerify(exactly = 0) { pendingDao.retryNow(any(), any()) }
        coVerify(exactly = 0) { scheduler.kick() }
    }

    // ============ 9. [V2-P8] withdraw UPDATE_NAME 入反向 op 到队列 ============

    @Test
    fun withdraw_updateName_undoInsertsPendingUploadWithUndoSuffix() = runTest {
        val ent = entity(
            opId = "op-name",
            opType = OperationTypes.UPDATE_NAME,
            inversePayloadJson = """{"name":"old","pinyinInitial":"L"}""",
            snapshotBeforeJson = """{"contact_id":1,"name":"old","version":1,"captured_at":1000,"platforms":{},"field_values":[],"tags":[]}""",
        )
        coEvery { historyDao.getById("op-name") } returns ent
        coEvery { contactSnapshotter.fromJson(any(), any()) } returns restoredContact(name = "old")
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "new", serverId = "srv-1", serverVersion = 5L)

        val result = repository.withdraw("op-name")

        assertThat(result).isEqualTo(HistoryOpResult.Success)
        // [修复防御]:验证新 opId 不是原 opId(主键冲突 ABORT 兜底)
        val pendingSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(pendingSlot)) }
        val captured = pendingSlot.captured
        assertThat(captured.opId).isNotEqualTo("op-name")
        assertThat(captured.opType).isEqualTo("${OperationTypes.UPDATE_NAME}${OperationTypes.UNDO_SUFFIX}")
        assertThat(captured.payloadJson).isEqualTo("""{"name":"old","pinyinInitial":"L"}""")
        assertThat(captured.resourceVersion).isEqualTo(5L)
        // history 同步插一条
        coVerify { historyDao.insert(match { it.opType == "${OperationTypes.UPDATE_NAME}${OperationTypes.UNDO_SUFFIX}" }) }
        coVerify { scheduler.kick() }
    }

    // ============ 10. [V2-P8] withdraw CREATE_CONTACT 入 DELETE_CONTACT 队列 ============

    @Test
    fun withdraw_createContact_undoInsertsDeleteContact() = runTest {
        val ent = entity(
            opId = "op-create",
            opType = OperationTypes.CREATE_CONTACT,
            inversePayloadJson = """{"action":"DELETE_CONTACT","contactId":1}""",
            snapshotBeforeJson = """{"contact_id":1,"name":"new","version":1,"captured_at":1000,"platforms":{},"field_values":[],"tags":[]}""",
        )
        coEvery { historyDao.getById("op-create") } returns ent
        coEvery { contactSnapshotter.fromJson(any(), any()) } returns restoredContact(name = "new")
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "new", serverId = "srv-1")

        val result = repository.withdraw("op-create")

        assertThat(result).isEqualTo(HistoryOpResult.Success)
        val pendingSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(pendingSlot)) }
        val captured = pendingSlot.captured
        assertThat(captured.opType).isEqualTo("${OperationTypes.CREATE_CONTACT}${OperationTypes.UNDO_SUFFIX}")
        // CREATE_CONTACT 撤销 resourceVersion = 0(DELETE 不需 If-Match 兜底,服务端按 idempotent 处理)
        assertThat(captured.resourceVersion).isEqualTo(0L)
        coVerify { scheduler.kick() }
    }

    // ============ 11. [V2-P8] withdraw ADD_PLATFORM 只本地回滚不入队列 ============

    @Test
    fun withdraw_addPlatform_undoOnlyRollsBackLocally() = runTest {
        val ent = entity(
            opId = "op-platform",
            opType = OperationTypes.ADD_PLATFORM,
            inversePayloadJson = """{"action":"REMOVE_PLATFORM","key":"qq"}""",
            snapshotBeforeJson = """{"contact_id":1,"name":"x","version":1,"captured_at":1000,"platforms":{},"field_values":[],"tags":[]}""",
        )
        coEvery { historyDao.getById("op-platform") } returns ent
        coEvery { contactSnapshotter.fromJson(any(), any()) } returns restoredContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "x", serverId = "srv-1", serverVersion = 5L)

        val result = repository.withdraw("op-platform")

        assertThat(result).isEqualTo(HistoryOpResult.Success)
        // [修复防御]:验证 pendingDao.enqueue 没被调(ADD_PLATFORM 不在 P8 支持列表)
        coVerify(exactly = 0) { pendingDao.enqueue(any()) }
        coVerify(exactly = 0) { historyDao.insert(any()) }
        // 验证 scheduler.kick 也没被调(本地回滚不需要 Worker)
        coVerify(exactly = 0) { scheduler.kick() }
        // 验证 cache 回滚仍执行(rollbackCache)
        coVerify { contactCacheDao.updateContact(any()) }
    }

    // ============ 12. [V2-P8] adoptLocal 200 → ServerApi.patchContact + 更新 cache ============

    @Test
    fun adoptLocal_success_patchesServerAndUpdatesCache() = runTest {
        val ent = entity(opStatus = "CONFLICT", serverVersion = 99L)
        coEvery { historyDao.getById("op-1") } returns ent
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "new-name", serverId = "srv-1", serverVersion = 99L)
        coEvery { serverApi.patchContact(any(), any(), any()) } returns ServerApi.ContactResponse(
            id = "srv-1",
            serverId = "srv-1",
            version = 100L,
            contact = JsonObject().apply { addProperty("name", "new-name") },
        )

        val result = repository.adoptLocal("op-1")

        assertThat(result).isEqualTo(HistoryOpResult.Success)
        // 验证 patchContact 的 payload 含顶层字段(name 必有)
        val payloadSlot = slot<JsonObject>()
        coVerify { serverApi.patchContact("srv-1", capture(payloadSlot), ifMatch = 99L) }
        assertThat(payloadSlot.captured.has("name")).isTrue()
        coVerify { contactCacheDao.updateContact(match { it.serverVersion == 100L }) }
        coVerify { historyDao.markDone("op-1", serverVersion = 100L, snapshotAfterJson = any()) }
        coVerify { pendingDao.markDone("op-1") }
    }

    // ============ 13. [V2-P8] adoptLocal 409 → markConflict + Failure ============

    @Test
    fun adoptLocal_conflict_marksConflictWithNewServerVersion() = runTest {
        val ent = entity(opStatus = "CONFLICT", serverVersion = 99L)
        coEvery { historyDao.getById("op-1") } returns ent
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "new-name", serverId = "srv-1", serverVersion = 99L)
        val conflictBody = JsonObject().apply { addProperty("server_version", 105L) }
        coEvery { serverApi.patchContact(any(), any(), any()) } throws
            ConflictException(ConflictResponse.from(conflictBody), "contacts.patch")

        val result = repository.adoptLocal("op-1")

        assertThat(result).isInstanceOf(HistoryOpResult.Failure::class.java)
        assertThat((result as HistoryOpResult.Failure).reason).contains("服务端又有新版本")
        coVerify { historyDao.markConflict("op-1", 105L, any()) }
        coVerify { pendingDao.markConflict("op-1", any()) }
    }

    // ============ 14. [V2-P8] adoptServer → ContactSnapshotter.fromServerContact → 整体替换 cache ============

    @Test
    fun adoptServer_upsertsCacheFromServerContact() = runTest {
        val ent = entity(opStatus = "CONFLICT", serverVersion = 7L)
        coEvery { historyDao.getById("op-1") } returns ent
        coEvery { contactSnapshotter.fromServerContact(any(), any()) } returns restoredContact(name = "server-side")
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "old", serverId = "srv-1")

        val serverJson = """{"name":"server-side","platforms":{"qq":{"value":"999"}}}"""
        val result = repository.adoptServer("op-1", serverJson)

        assertThat(result).isEqualTo(HistoryOpResult.Success)
        // [修复防御]:验证 4 表回滚全部调用
        coVerify { contactCacheDao.updateContact(match { it.serverVersion == 7L }) }
        coVerify { contactPlatformCacheDao.deleteByContact(1L) }
        coVerify { contactPlatformCacheDao.insertPlatforms(match { it.isNotEmpty() }) }
        coVerify { contactFieldValueCacheDao.deleteByContact(1L) }
        coVerify { contactTagCacheDao.clearByContact(1L) }
        coVerify { contactTagCacheDao.insertCrossRefs(match { it.isNotEmpty() }) }
        coVerify { contactCacheDao.bumpContact(1L) }
        coVerify { historyDao.markDone("op-1", serverVersion = 7L, snapshotAfterJson = serverJson) }
        coVerify { pendingDao.markDone("op-1") }
    }

    // ============ 15. [V2-P8] withdraw 快照缺失 → Failure ============

    @Test
    fun withdraw_snapshotMissing_returnsFailure() = runTest {
        val ent = entity(
            opId = "op-no-snap",
            snapshotBeforeJson = null,
            inversePayloadJson = """{"name":"old"}""",
        )
        coEvery { historyDao.getById("op-no-snap") } returns ent

        val result = repository.withdraw("op-no-snap")

        assertThat(result).isInstanceOf(HistoryOpResult.Failure::class.java)
        assertThat((result as HistoryOpResult.Failure).reason).contains("快照")
        coVerify(exactly = 0) { historyDao.markWithdrawn(any()) }
    }

    // ============ 16. [V2-P8] withdraw inverse 缺失 → Failure ============

    @Test
    fun withdraw_inverseMissing_returnsFailure() = runTest {
        val ent = entity(
            opId = "op-no-inv",
            inversePayloadJson = null,
            snapshotBeforeJson = """{"contact_id":1,"name":"old","version":1,"captured_at":1000,"platforms":{},"field_values":[],"tags":[]}""",
        )
        coEvery { historyDao.getById("op-no-inv") } returns ent

        val result = repository.withdraw("op-no-inv")

        assertThat(result).isInstanceOf(HistoryOpResult.Failure::class.java)
        assertThat((result as HistoryOpResult.Failure).reason).contains("反向")
    }

    // ============ 17. [V2-P10] batchRetry 仅 FAILED 走 retryNow + kick 1 次 ============

    @Test
    fun batchRetry_filtersFailedOnly_andKicksOnce() = runTest {
        // 混合:1 FAILED + 1 PENDING + 1 DONE + 1 null
        val failedOp = PendingUploadEntity(
            opId = "op-failed",
            contactId = 1L,
            opType = OperationTypes.UPDATE_NAME,
            resourceVersion = 0L,
            payloadJson = "{}",
            createdAt = 1L,
            status = "FAILED",
            deviceId = "dev",
        )
        val pendingOp = failedOp.copy(opId = "op-pending", status = "PENDING")
        val doneOp = failedOp.copy(opId = "op-done", status = "DONE")
        coEvery { pendingDao.getById("op-failed") } returns failedOp
        coEvery { pendingDao.getById("op-pending") } returns pendingOp
        coEvery { pendingDao.getById("op-done") } returns doneOp
        coEvery { pendingDao.getById("op-missing") } returns null

        val result = repository.batchRetry(listOf("op-failed", "op-pending", "op-done", "op-missing"))

        assertThat(result).isEqualTo(BatchHistoryOpResult.Success(succeeded = 1, failed = 3))
        // 仅 FAILED 走 retryNow
        coVerify(exactly = 1) { pendingDao.retryNow("op-failed", any()) }
        coVerify(exactly = 0) { pendingDao.retryNow("op-pending", any()) }
        coVerify(exactly = 0) { pendingDao.retryNow("op-done", any()) }
        coVerify(exactly = 0) { pendingDao.retryNow("op-missing", any()) }
        // kick 1 次
        coVerify(exactly = 1) { scheduler.kick() }
    }

    // ============ 18. [V2-P10] batchRetry 空列表 → Success(0, 0) 不 kick ============

    @Test
    fun batchRetry_emptyList_returnsSuccessZero_andNoKick() = runTest {
        val result = repository.batchRetry(emptyList())

        assertThat(result).isEqualTo(BatchHistoryOpResult.Success(succeeded = 0, failed = 0))
        coVerify(exactly = 0) { pendingDao.retryNow(any(), any()) }
        coVerify(exactly = 0) { scheduler.kick() }
    }

    // ============ 19. [V2-P10] batchRetry 单条 retryNow 抛异常被 catch + 继续 ============

    @Test
    fun batchRetry_perOpFailure_continues() = runTest {
        val failedOp = PendingUploadEntity(
            opId = "op-throw",
            contactId = 1L,
            opType = OperationTypes.UPDATE_NAME,
            resourceVersion = 0L,
            payloadJson = "{}",
            createdAt = 1L,
            status = "FAILED",
            deviceId = "dev",
        )
        val okOp = failedOp.copy(opId = "op-ok")
        coEvery { pendingDao.getById("op-throw") } returns failedOp
        coEvery { pendingDao.getById("op-ok") } returns okOp
        coEvery { pendingDao.retryNow("op-throw", any()) } throws RuntimeException("模拟 DB 异常")
        coEvery { pendingDao.retryNow("op-ok", any()) } returns Unit

        val result = repository.batchRetry(listOf("op-throw", "op-ok"))

        assertThat(result).isEqualTo(BatchHistoryOpResult.Success(succeeded = 1, failed = 1))
        coVerify(exactly = 1) { pendingDao.retryNow("op-ok", any()) }
        coVerify(exactly = 1) { scheduler.kick() }
    }

    // ============ 20. [V2-P10] batchWithdraw 跳过 WITHDRAWN + canUndo=false ============

    @Test
    fun batchWithdraw_filtersUndoneOnly() = runTest {
        // 4 条:1 可撤销 + 1 WITHDRAWN + 1 canUndo=false + 1 null
        val undonableEnt = entity(opId = "op-undo", canUndo = true, opStatus = "DONE")
        val withdrawnEnt = entity(opId = "op-withdrawn", canUndo = true, opStatus = "WITHDRAWN")
        val noUndoEnt = entity(opId = "op-noundo", canUndo = false, opStatus = "DONE")
        coEvery { historyDao.getById("op-undo") } returns undonableEnt
        coEvery { historyDao.getById("op-withdrawn") } returns withdrawnEnt
        coEvery { historyDao.getById("op-noundo") } returns noUndoEnt
        coEvery { historyDao.getById("op-missing") } returns null
        // 让 op-undo 走完整 withdraw 流程不抛
        coEvery { contactSnapshotter.fromJson(any(), any()) } returns restoredContact(name = "old")
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "new", serverId = "srv-1", serverVersion = 5L)

        val result = repository.batchWithdraw(listOf("op-undo", "op-withdrawn", "op-noundo", "op-missing"))

        // op-undo 成功走完 withdraw;另外 3 条被过滤
        assertThat(result).isEqualTo(BatchHistoryOpResult.Success(succeeded = 1, failed = 3))
        // op-undo 的 markWithdrawn 命中一次
        coVerify(exactly = 1) { historyDao.markWithdrawn("op-undo") }
        coVerify(exactly = 0) { historyDao.markWithdrawn("op-withdrawn") }
        coVerify(exactly = 0) { historyDao.markWithdrawn("op-noundo") }
    }

    // ============ 21. [V2-P10] batchWithdraw 单条 withdraw 抛异常被 catch + 继续 ============

    @Test
    fun batchWithdraw_perOpFailure_continues() = runTest {
        val opA = entity(opId = "op-throw", canUndo = true, opStatus = "DONE")
        val opB = entity(opId = "op-ok", canUndo = true, opStatus = "DONE")
        coEvery { historyDao.getById("op-throw") } returns opA
        coEvery { historyDao.getById("op-ok") } returns opB
        // op-throw 第一次 historyDao.getById 返 opA,第二次返 null 模拟 withdraw 内部异常
        coEvery { historyDao.markWithdrawn("op-throw") } throws RuntimeException("模拟回滚失败")
        // op-ok 完整走完
        coEvery { contactSnapshotter.fromJson(any(), any()) } returns restoredContact(name = "old")
        coEvery { contactCacheDao.getContactById(1L) } returns contact(1L, "new", serverId = "srv-1", serverVersion = 5L)

        val result = repository.batchWithdraw(listOf("op-throw", "op-ok"))

        assertThat(result).isEqualTo(BatchHistoryOpResult.Success(succeeded = 1, failed = 1))
    }

    // ============ 22. [V2-P10] batchWithdraw CONFLICT 跳过(需单条解决) ============

    @Test
    fun batchWithdraw_skipsConflict() = runTest {
        val conflictEnt = entity(opId = "op-conflict", canUndo = true, opStatus = "CONFLICT")
        coEvery { historyDao.getById("op-conflict") } returns conflictEnt

        val result = repository.batchWithdraw(listOf("op-conflict"))

        assertThat(result).isEqualTo(BatchHistoryOpResult.Success(succeeded = 0, failed = 1))
        coVerify(exactly = 0) { historyDao.markWithdrawn(any()) }
    }
}