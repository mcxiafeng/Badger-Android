package top.mcxiafeng.badger.sync

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import java.io.IOException
import java.net.ConnectException

/**
 * [V2-P4] PendingUploadExecutor 单元测试。
 *
 * 覆盖规约 docs/BADGER_V2_CLIENT_PLAN.md §4.2 / §5.5.2 / §5.5.4:
 * 1. 200 → DONE(serverVersion 写回)
 * 2. 409 → CONFLICT(serverVersion 与服务端权威版本对齐)
 * 3. 404 → 视为幂等成功(DELETE 路径)
 * 4. 5xx → FAILED(attempts++ + nextAttempt 退避)
 * 5. attempts>=max → FAILED_PERMANENT
 * 6. IOException / ConnectException → FAILED(不退化为永久失败)
 * 7. 未知 opType → 永久失败(不抛异常)
 * 8. nextAttempt 退避公式边界(2s → 4s → ... → 5min)
 *
 * 跑 Robolectric 是为了拿到 Application context,与 P2/P3 测试一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PendingUploadExecutorTest {

    private lateinit var db: AppDatabase
    private lateinit var pendingDao: PendingUploadDao
    private lateinit var historyDao: OperationHistoryDao
    private lateinit var serverApi: ServerApi
    private lateinit var deviceIdProvider: DeviceIdProvider
    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var executor: PendingUploadExecutor

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        pendingDao = db.pendingUploadDao()
        historyDao = db.operationHistoryDao()
        contactCacheDao = db.contactCacheDao()
        serverApi = mockk(relaxed = true)
        deviceIdProvider = mockk(relaxed = true)
        coEvery { deviceIdProvider.deviceId() } returns "test-device-uuid"
        executor = PendingUploadExecutor(
            pendingDao = pendingDao,
            historyDao = historyDao,
            deviceIdProvider = deviceIdProvider,
            serverApi = serverApi,
            contactCacheDao = contactCacheDao,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedOp(
        opId: String = "op-1",
        opType: String = OpType.PATCH_CONTACT,
        payload: String = """{"server_id":"srv-1","name":"张三"}""",
        status: String = "IN_FLIGHT",
        attempts: Int = 0,
        maxAttempts: Int = 8,
    ): PendingUploadEntity {
        val op = PendingUploadEntity(
            opId = opId,
            contactId = 1L,
            opType = opType,
            resourceVersion = 5L,
            payloadJson = payload,
            createdAt = 1_000L,
            status = status,
            attempts = attempts,
            maxAttempts = maxAttempts,
            deviceId = "test-device",
        )
        pendingDao.enqueue(op)
        return op
    }

    private suspend fun seedHistory(
        opId: String,
        payload: String = """{"name":"张三"}""",
        snapshotBefore: String = """{"contact_id":1,"name":"张三"}""",
    ) {
        historyDao.insert(
            OperationHistoryEntity(
                opId = opId,
                contactId = 1L,
                opType = OpType.PATCH_CONTACT,
                opLabel = "改名",
                payloadJson = payload,
                snapshotBeforeJson = snapshotBefore,
                createdAt = 1_000L,
                opStatus = "IN_FLIGHT",
                canUndo = true,
                canReplay = true,
            )
        )
    }

    private fun patchOk(serverVersion: Long = 7L): ServerApi.ContactResponse =
        ServerApi.ContactResponse(
            id = "srv-1",
            serverId = "srv-1",
            version = serverVersion,
            contact = JsonObject().apply { addProperty("name", "李四") },
        )

    // ============ 200 OK ============

    @Test
    fun execute_200_patchContact_marksDoneWithServerVersion() = runTest {
        val op = seedOp()
        seedHistory(op.opId)
        coEvery { serverApi.patchContact(any(), any(), any()) } returns patchOk(serverVersion = 9L)

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.Done::class.java)
        assertThat((result as ExecResult.Done).serverVersion).isEqualTo(9L)

        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("DONE")

        val history = historyDao.getById(op.opId)
        assertThat(history?.opStatus).isEqualTo("DONE")
        assertThat(history?.serverVersion).isEqualTo(9L)
    }

    @Test
    fun execute_200_createContact_marksDone() = runTest {
        val op = seedOp(opId = "op-c", opType = OpType.CREATE_CONTACT, payload = """{"name":"王五"}""")
        seedHistory(op.opId, payload = """{"name":"王五"}""")
        coEvery { serverApi.createContact(any(), any()) } returns patchOk(serverVersion = 1L)

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.Done::class.java)
        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("DONE")
    }

    // ============ 409 CONFLICT ============

    @Test
    fun execute_409_marksConflictWithServerVersion() = runTest {
        val op = seedOp(opId = "op-cf", opType = OpType.PATCH_CONTACT)
        seedHistory(op.opId)
        val conflictJson = JsonObject().apply { addProperty("server_version", 12L) }
        coEvery { serverApi.patchContact(any(), any(), any()) } throws
            ServerApi.ConflictException(ServerApi.ConflictResponse.from(conflictJson), "contacts.patch")

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.Conflict::class.java)
        assertThat((result as ExecResult.Conflict).serverVersion).isEqualTo(12L)

        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("CONFLICT")
        assertThat(loaded?.lastError).contains("serverVersion=12")

        val history = historyDao.getById(op.opId)
        assertThat(history?.opStatus).isEqualTo("CONFLICT")
        assertThat(history?.serverVersion).isEqualTo(12L)
    }

    // ============ 404 幂等成功 ============

    @Test
    fun execute_404_deleteContact_isIdempotentSuccess() = runTest {
        val op = seedOp(opId = "op-del", opType = OpType.DELETE_CONTACT, payload = """{"server_id":"srv-1"}""")
        seedHistory(op.opId)
        // 第一次 deleteContact 抛 404(服务端已删),Executor 内部 catch 并返 true
        coEvery { serverApi.deleteContact(any(), any()) } throws ApiException(404, "not found", "contacts.delete")

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.Done::class.java)
        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("DONE")
    }

    // ============ 5xx 暂时性失败 ============

    @Test
    fun execute_5xx_marksFailedWithBackoff() = runTest {
        val op = seedOp(opId = "op-5xx", attempts = 2)
        seedHistory(op.opId)
        coEvery { serverApi.patchContact(any(), any(), any()) } throws ApiException(503, "service unavailable", "contacts.patch")

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.RetryScheduled::class.java)
        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("FAILED")
        assertThat(loaded?.attempts).isEqualTo(3)
        // 退避公式:attempts=3 → 8s,即 nextAttemptAt - now ≈ 8000ms
        val delay = (loaded?.nextAttemptAt ?: 0L) - 2_000L
        assertThat(delay).isEqualTo(8_000L)
    }

    @Test
    fun execute_5xx_atMaxAttempts_marksFailedPermanent() = runTest {
        val op = seedOp(opId = "op-perm", attempts = 7, maxAttempts = 8)
        seedHistory(op.opId)
        coEvery { serverApi.patchContact(any(), any(), any()) } throws ApiException(503, "service unavailable", "contacts.patch")

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.PermanentFailure::class.java)
        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("FAILED_PERMANENT")
        val history = historyDao.getById(op.opId)
        assertThat(history?.opStatus).isEqualTo("FAILED")
    }

    // ============ IO 异常 ============

    @Test
    fun execute_connectException_marksFailed() = runTest {
        val op = seedOp(opId = "op-io", attempts = 0)
        seedHistory(op.opId)
        coEvery { serverApi.patchContact(any(), any(), any()) } throws ConnectException("Failed to connect")

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.RetryScheduled::class.java)
        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("FAILED")
        assertThat(loaded?.attempts).isEqualTo(1)
        assertThat(loaded?.lastError).contains("ConnectException")
    }

    @Test
    fun execute_ioException_marksFailed() = runTest {
        val op = seedOp(opId = "op-io2", attempts = 0)
        seedHistory(op.opId)
        coEvery { serverApi.patchContact(any(), any(), any()) } throws IOException("stream closed")

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.RetryScheduled::class.java)
        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("FAILED")
    }

    // ============ 401 token 失效 ============

    @Test
    fun execute_401_keepsPendingWithDelayedRetry() = runTest {
        val op = seedOp(opId = "op-401", attempts = 0)
        seedHistory(op.opId)
        coEvery { serverApi.patchContact(any(), any(), any()) } throws ApiException(401, "expired", "contacts.patch")

        val result = executor.execute(op, now = 2_000L)

        // 401 不计入 attempts 推进,Worker 下次重试
        assertThat(result).isInstanceOf(ExecResult.RetryScheduled::class.java)
        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("FAILED")
        assertThat(loaded?.attempts).isEqualTo(0)
        assertThat(loaded?.lastError).contains("401")
    }

    // ============ 未知 opType ============

    @Test
    fun execute_unknownOpType_marksPermanentFailure() = runTest {
        val op = seedOp(opId = "op-unk", opType = "BOGUS_OP")
        seedHistory(op.opId)

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.PermanentFailure::class.java)
        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("FAILED_PERMANENT")
    }

    // ============ Skipped(非活跃态) ============

    @Test
    fun execute_doneOp_isSkipped() = runTest {
        val op = seedOp(opId = "op-done", status = "DONE")

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.Skipped::class.java)
        coVerify(exactly = 0) { serverApi.patchContact(any(), any(), any()) }
    }

    // ============ [V2-P8] 撤销双边同步 _UNDO ============

    @Test
    fun execute_updateNameUndo_callsPatchContact() = runTest {
        // [V2-P8] UPDATE_NAME 撤销:inversePayloadJson 直接当 PATCH payload
        val op = seedOp(
            opId = "op-name-undo",
            opType = OperationTypes.UPDATE_NAME + OperationTypes.UNDO_SUFFIX,
            payload = """{"name":"张三","pinyinInitial":"Z"}""",
        )
        // [修复防御]:seedOp 用 contactId=1,Executor.handlePatch 兜底查 cache serverId — 必须种一条有 serverId 的联系人
        contactCacheDao.insertContact(
            ContactCacheEntity(
                id = 1L,
                serverId = "srv-1",
                name = "李四",
                createTime = 1L,
                updateTime = 1L,
                serverVersion = 5L,
            )
        )
        seedHistory(op.opId)
        coEvery { serverApi.patchContact(any(), any(), any()) } returns patchOk(serverVersion = 6L)

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.Done::class.java)
        // [修复防御]:验证 server_id 兜底路径 — payload 没有 server_id,Executor 走 cache 查 srv-1
        coVerify { serverApi.patchContact("srv-1", any(), ifMatch = 5L) }
    }

    @Test
    fun execute_createContactUndo_callsDeleteContact() = runTest {
        // [V2-P8] CREATE_CONTACT 撤销 = DELETE_CONTACT(inversePayloadJson 含 server_id)
        val op = seedOp(
            opId = "op-create-undo",
            opType = OperationTypes.CREATE_CONTACT + OperationTypes.UNDO_SUFFIX,
            payload = """{"server_id":"srv-1","id":1}""",
        )
        contactCacheDao.insertContact(
            ContactCacheEntity(
                id = 1L,
                serverId = "srv-1",
                name = "王五",
                createTime = 1L,
                updateTime = 1L,
            )
        )
        seedHistory(op.opId)
        coEvery { serverApi.deleteContact(any(), any()) } returns true

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.Done::class.java)
        coVerify { serverApi.deleteContact("srv-1", ifMatch = 5L) }
    }

    @Test
    fun execute_addPlatformUndo_marksFailedPermanent() = runTest {
        // [V2-P8] ADD/UPDATE/REMOVE_PLATFORM 撤销 P8 MVP 暂不支持 → FAILED_PERMANENT
        val op = seedOp(
            opId = "op-platform-undo",
            opType = OperationTypes.ADD_PLATFORM + OperationTypes.UNDO_SUFFIX,
            payload = """{"action":"REMOVE_PLATFORM","key":"qq"}""",
        )
        seedHistory(op.opId)

        val result = executor.execute(op, now = 2_000L)

        assertThat(result).isInstanceOf(ExecResult.PermanentFailure::class.java)
        val loaded = pendingDao.getById(op.opId)
        assertThat(loaded?.status).isEqualTo("FAILED_PERMANENT")
        assertThat(loaded?.lastError).contains("P9+")
        // 验证 ServerApi 没被调(避免误调)
        coVerify(exactly = 0) { serverApi.patchContact(any(), any(), any()) }
        coVerify(exactly = 0) { serverApi.deleteContact(any(), any()) }
    }

    // ============ 退避公式 ============

    @Test
    fun nextAttempt_exponential_backoff() {
        val now = 1_000_000L
        assertThat(PendingUploadExecutor.nextAttempt(now, attempts = 1)).isEqualTo(now + 2_000L)
        assertThat(PendingUploadExecutor.nextAttempt(now, attempts = 2)).isEqualTo(now + 4_000L)
        assertThat(PendingUploadExecutor.nextAttempt(now, attempts = 3)).isEqualTo(now + 8_000L)
        assertThat(PendingUploadExecutor.nextAttempt(now, attempts = 8)).isEqualTo(now + 256_000L)
        // 9+ → 5min 封顶
        assertThat(PendingUploadExecutor.nextAttempt(now, attempts = 9)).isEqualTo(now + 300_000L)
        assertThat(PendingUploadExecutor.nextAttempt(now, attempts = 20)).isEqualTo(now + 300_000L)
    }
}