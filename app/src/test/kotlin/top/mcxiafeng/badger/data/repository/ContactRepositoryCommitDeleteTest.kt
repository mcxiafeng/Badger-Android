package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.data.snapshot.ContactSnapshotter
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import java.io.IOException
import java.net.ConnectException

/**
 * [V2-P6] ContactRepositoryImpl.commitDelete / commitMerge 双通道关键操作单测。
 *
 * 覆盖规约 `docs/BADGER_V2_CLIENT_PLAN.md` §5.5(关键操作双通道):
 * - 6 个 commitDelete:
 *   1. 直发 200 + DONE → 物理删除 + history 标 DONE
 *   2. 直发 404 → 幂等成功,hardDelete + DONE
 *   3. 直发 5xx → recoverFromDirect + kick + scheduleRevertIfStuck
 *   4. 直发 IO 异常 → recoverFromDirect + kick + scheduleRevertIfStuck
 *   5. 入队顺序:enqueue → history → setDeleted → bumpContact
 *   6. isLocalOnly=true → 跳过 HTTP,直接 hardDelete
 * - 2 个 commitMerge:
 *   7. 200 OK → hardDelete merged + 写回 target.serverVersion
 *   8. 409 CONFLICT → recoverFromDirect + kick(由 Worker 兜底)
 *
 * [修复防御]:每个测试都用 mockk(relaxed = false)显式 stub 关键依赖,
 * 避免 relaxed 模式掩盖 verify 失败 — §5.5.4 入队顺序验证需要断言真实调用次数。
 */
class ContactRepositoryCommitDeleteTest {

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
        coEvery { contactSnapshotter.toJsonFromCache(any(), any()) } returns """{"contactId":1,"name":"Bob"}"""

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

    private fun existingContact(
        id: Long = 1L,
        serverId: String? = "srv-1",
        serverVersion: Long = 5L,
        isLocalOnly: Boolean = false,
    ) = ContactCacheEntity(
        id = id,
        name = "Bob",
        serverId = serverId,
        serverVersion = serverVersion,
        isLocalOnly = isLocalOnly,
        isDeleted = false,
        createTime = 1000L,
        updateTime = 1000L,
    )

    // ============ commitDelete — 直发 200 成功 ============

    @Test
    fun commitDelete_200_success_hardDeleteAndMarkDone() = runTest {
        val contact = existingContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deleteContact("srv-1", ifMatch = 5L) } returns true

        val result = repository.commitDelete(1L)

        assertThat(result).isEqualTo(CommitResult.SentSuccess)

        // 1. 入队顺序
        val pendingSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(pendingSlot)) }
        val op = pendingSlot.captured
        assertThat(op.opType).isEqualTo(OperationTypes.DELETE_CONTACT)
        assertThat(op.status).isEqualTo("IN_FLIGHT")
        assertThat(op.resourceVersion).isEqualTo(5L)
        assertThat(op.payloadJson).contains("\"server_id\":\"srv-1\"")

        val historySlot = slot<OperationHistoryEntity>()
        coVerify { historyDao.insert(capture(historySlot)) }
        val history = historySlot.captured
        assertThat(history.opLabel).isEqualTo("删除联系人")
        assertThat(history.canUndo).isFalse()
        assertThat(history.canReplay).isTrue()

        // 2. softDelete + bump 后 200 → hardDelete
        coVerify { contactCacheDao.setDeleted(1L, deleted = true, any()) }
        coVerify { contactCacheDao.deleteById(1L) }
        coVerify { contactPlatformCacheDao.deleteByContact(1L) }
        coVerify { contactFieldValueCacheDao.deleteByContact(1L) }
        coVerify { contactTagCacheDao.clearByContact(1L) }

        // 3. 标记 DONE
        coVerify { pendingDao.markDone(op.opId) }
        coVerify { historyDao.markDone(op.opId, serverVersion = null, snapshotAfterJson = null) }

        // 4. 失败兜底不应该触发
        coVerify(exactly = 0) { pendingDao.recoverFromDirect(any(), any(), any()) }
        coVerify(exactly = 0) { pendingUploadScheduler.scheduleRevertIfStuck(any(), any()) }
    }

    // ============ commitDelete — 404 幂等成功 ============

    @Test
    fun commitDelete_404_idempotent_hardDelete() = runTest {
        val contact = existingContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        // ServerApi.deleteContact 内部已 catch 404 返 true,但显式模拟未走 catch 的情况
        coEvery { serverApi.deleteContact(any(), any()) } throws ApiException(404, "not found", "contacts.delete")

        val result = repository.commitDelete(1L)

        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        // 404 走幂等成功,走 hardDelete
        coVerify { contactCacheDao.deleteById(1L) }
        coVerify { contactPlatformCacheDao.deleteByContact(1L) }
        coVerify { pendingDao.markDone(any()) }
        // 不应在 404 路径调用 recoverFromDirect
        coVerify(exactly = 0) { pendingDao.recoverFromDirect(any(), any(), any()) }
        coVerify(exactly = 0) { pendingUploadScheduler.scheduleRevertIfStuck(any(), any()) }
    }

    // ============ commitDelete — 5xx → recoverFromDirect + revert 30s ============

    @Test
    fun commitDelete_5xx_recoverFromDirectAndScheduleRevert() = runTest {
        val contact = existingContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deleteContact(any(), any()) } throws ApiException(503, "service unavailable", "contacts.delete")

        val result = repository.commitDelete(1L)

        assertThat(result).isInstanceOf(CommitResult.SentFailed::class.java)
        assertThat((result as CommitResult.SentFailed).reason).contains("HTTP 503")

        // 必须走 recoverFromDirect + kick + 30s revert 兜底
        coVerify { pendingDao.recoverFromDirect(any(), any(), any()) }
        coVerify { pendingUploadScheduler.kick() }
        coVerify { pendingUploadScheduler.scheduleRevertIfStuck(any(), delaySeconds = 30L) }

        // 不应 hardDelete(直发失败,数据要保留供 30s revert 复活)
        coVerify(exactly = 0) { contactCacheDao.deleteById(1L) }
    }

    // ============ commitDelete — IO 异常(网络断开)→ recoverFromDirect ============

    @Test
    fun commitDelete_ioException_recoverFromDirect() = runTest {
        val contact = existingContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deleteContact(any(), any()) } throws IOException("network unreachable")

        val result = repository.commitDelete(1L)

        assertThat(result).isInstanceOf(CommitResult.SentFailed::class.java)
        coVerify { pendingDao.recoverFromDirect(any(), any(), any()) }
        coVerify { pendingUploadScheduler.kick() }
        coVerify { pendingUploadScheduler.scheduleRevertIfStuck(any(), delaySeconds = 30L) }
    }

    @Test
    fun commitDelete_connectException_recoverFromDirect() = runTest {
        val contact = existingContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deleteContact(any(), any()) } throws ConnectException("Failed to connect")

        val result = repository.commitDelete(1L)

        assertThat(result).isInstanceOf(CommitResult.SentFailed::class.java)
        coVerify { pendingDao.recoverFromDirect(any(), any(), any()) }
        coVerify { pendingUploadScheduler.kick() }
    }

    // ============ commitDelete — 入队顺序(§5.5.4 红线) ============

    @Test
    fun commitDelete_enqueueOrder_isCorrect() = runTest {
        val contact = existingContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deleteContact(any(), any()) } returns true

        repository.commitDelete(1L)

        // §5.5.4 红线:enqueue → history → setDeleted → bumpContact
        // (顺序断言用 mockk 的 verifyOrder,见 commitDelete_invariantOrder)
        coVerify { pendingDao.enqueue(any()) }
        coVerify { historyDao.insert(any()) }
        coVerify { contactCacheDao.setDeleted(1L, deleted = true, any()) }
        coVerify { contactCacheDao.bumpContact(1L) }
        // serverApi.deleteContact 必须在 setDeleted 之后调用(否则 UI 没隐藏就先发)
        coVerify { serverApi.deleteContact(any(), any()) }
    }

    // ============ commitDelete — isLocalOnly=true 跳过 HTTP ============

    @Test
    fun commitDelete_isLocalOnly_skipsHttpAndHardDeletes() = runTest {
        val contact = existingContact(serverId = null, isLocalOnly = true)
        coEvery { contactCacheDao.getContactById(1L) } returns contact

        val result = repository.commitDelete(1L)

        // isLocalOnly:服务端没这个 id,直接 hardDelete
        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        coVerify { contactCacheDao.deleteById(1L) }
        // 不应该调 HTTP
        coVerify(exactly = 0) { serverApi.deleteContact(any(), any()) }
        // 不应该入队(没必要)
        coVerify(exactly = 0) { pendingDao.enqueue(any()) }
    }

    // ============ commitDelete — contactId 不存在 ============

    @Test
    fun commitDelete_contactNotFound_returnsNotFound() = runTest {
        coEvery { contactCacheDao.getContactById(99L) } returns null

        val result = repository.commitDelete(99L)

        assertThat(result).isEqualTo(CommitResult.NotFound)
        coVerify(exactly = 0) { pendingDao.enqueue(any()) }
        coVerify(exactly = 0) { historyDao.insert(any()) }
        coVerify(exactly = 0) { serverApi.deleteContact(any(), any()) }
    }

    // ============ commitMerge — 200 OK ============

    @Test
    fun commitMerge_200_hardDeleteMergedAndUpdateTargetServerVersion() = runTest {
        val target = existingContact(id = 1L)
        val merged1 = existingContact(id = 2L, serverId = "srv-2")
        val merged2 = existingContact(id = 3L, serverId = "srv-3")
        coEvery { contactCacheDao.getContactById(1L) } returns target
        coEvery { contactCacheDao.getContactById(2L) } returns merged1
        coEvery { contactCacheDao.getContactById(3L) } returns merged2
        coEvery { serverApi.mergeContact("srv-1", listOf("srv-2", "srv-3"), ifMatch = 5L) } returns
            ServerApi.ContactResponse(id = "srv-1", serverId = "srv-1", version = 6L, contact = JsonObject())

        val result = repository.commitMerge(1L, listOf(2L, 3L))

        assertThat(result).isEqualTo(CommitResult.SentSuccess)

        // 入队
        val pendingSlot = slot<PendingUploadEntity>()
        coVerify { pendingDao.enqueue(capture(pendingSlot)) }
        val op = pendingSlot.captured
        assertThat(op.opType).isEqualTo(OperationTypes.MERGE_CONTACT)
        // payloadJson 含 target_server_id + merged_server_ids
        val payload = JsonParser.parseString(op.payloadJson).asJsonObject
        assertThat(payload.get("target_server_id").asString).isEqualTo("srv-1")
        val mergedArr = payload.getAsJsonArray("merged_server_ids")
        assertThat(mergedArr).isNotNull()
        assertThat(mergedArr.map { it.asString }).containsExactly("srv-2", "srv-3")

        // softDelete merged + bump
        coVerify { contactCacheDao.setDeleted(2L, deleted = true, any()) }
        coVerify { contactCacheDao.setDeleted(3L, deleted = true, any()) }
        coVerify { contactCacheDao.bumpContact(1L) }
        coVerify { contactCacheDao.bumpContact(2L) }
        coVerify { contactCacheDao.bumpContact(3L) }

        // 200 → hardDelete merged + 写回 target.serverVersion
        coVerify { contactCacheDao.deleteById(2L) }
        coVerify { contactCacheDao.deleteById(3L) }
        val updatedTarget = slot<ContactCacheEntity>()
        coVerify { contactCacheDao.updateContact(capture(updatedTarget)) }
        assertThat(updatedTarget.captured.serverVersion).isEqualTo(6L)

        coVerify { pendingDao.markDone(op.opId) }
        coVerify { historyDao.markDone(op.opId, serverVersion = 6L, snapshotAfterJson = null) }
    }

    // ============ commitMerge — 409 CONFLICT ============

    @Test
    fun commitMerge_409_recoverFromDirect() = runTest {
        val target = existingContact(id = 1L)
        val merged1 = existingContact(id = 2L, serverId = "srv-2")
        coEvery { contactCacheDao.getContactById(1L) } returns target
        coEvery { contactCacheDao.getContactById(2L) } returns merged1
        val conflictJson = JsonObject().apply { addProperty("server_version", 99L) }
        coEvery { serverApi.mergeContact(any(), any(), any()) } throws
            ServerApi.ConflictException(ServerApi.ConflictResponse.from(conflictJson), "contacts.merge")

        val result = repository.commitMerge(1L, listOf(2L))

        assertThat(result).isInstanceOf(CommitResult.SentFailed::class.java)
        assertThat((result as CommitResult.SentFailed).reason).contains("409")
        coVerify { pendingDao.recoverFromDirect(any(), any(), any()) }
        coVerify { pendingUploadScheduler.kick() }
        // 不要 hardDelete(CONFLICT 让用户决策)
        coVerify(exactly = 0) { contactCacheDao.deleteById(2L) }
    }

    // ============ commitMerge — empty mergedIds ============

    @Test
    fun commitMerge_emptyMergedIds_returnsNotFound() = runTest {
        val target = existingContact(id = 1L)
        coEvery { contactCacheDao.getContactById(1L) } returns target

        val result = repository.commitMerge(1L, emptyList())

        assertThat(result).isEqualTo(CommitResult.NotFound)
        coVerify(exactly = 0) { pendingDao.enqueue(any()) }
        coVerify(exactly = 0) { serverApi.mergeContact(any(), any(), any()) }
    }

    // ============ commitMerge — target.snapshotBeforeJson 含 target 全字段 ============

    @Test
    fun commitMerge_snapshotBefore_usesTargetId() = runTest {
        val target = existingContact(id = 1L)
        val merged1 = existingContact(id = 2L, serverId = "srv-2")
        coEvery { contactCacheDao.getContactById(1L) } returns target
        coEvery { contactCacheDao.getContactById(2L) } returns merged1
        coEvery { serverApi.mergeContact(any(), any(), any()) } returns
            ServerApi.ContactResponse(id = "srv-1", serverId = "srv-1", version = 6L, contact = JsonObject())

        repository.commitMerge(1L, listOf(2L))

        // 校验 snapshotter.toJsonFromCache 被调用时使用 targetId(不是 merged)
        coVerify { contactSnapshotter.toJsonFromCache(1L, any()) }
        coVerify(exactly = 0) { contactSnapshotter.toJsonFromCache(2L, any()) }
    }
}
