package top.mcxiafeng.badger.sync

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi

/**
 * [V2-P4] PendingUploadWorker 单元测试。
 *
 * 覆盖规约 docs/BADGER_V2_CLIENT_PLAN.md §4.6 + §0#4(批量并发上限 = 8):
 * 1. 空队列 → Executor 轮询空集合
 * 2. 拉批 8 条逐个处理 → 全部 DONE
 * 3. nextAttemptAt 在未来 → 跳过
 * 4. 部分 op 失败(5xx) → 标 FAILED + 继续下一个
 * 5. 部分 op 409 → 标 CONFLICT + 继续下一个
 * 6. 短批(< 8 条)→ 一次拉完即退出
 * 7. 大于 BATCH_SIZE(12 条)按 batch 拆批
 *
 * 实际策略:测试 Worker 的"拉批 + 调 Executor"循环逻辑需要在 WorkManager 测试驱动器下跑,
 * [androidx.work.WorkerParameters] 构造函数极复杂且 internal API 多变。本测试套件分两层:
 * - **Worker 端的循环 / markInFlight / 批次控制** 已经在 [PendingUploadExecutorTest] 覆盖
 *   (doExecute / handleCreate / handlePatch / handleDelete / handleMerge / 状态机);
 * - 这里测试 **Worker 视角的批量行为**:模拟 Worker 拿到 [pendingDao.nextReady] 列表
 *   后逐个走 markInFlight + executor.execute,与 Worker 内部循环同语义。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PendingUploadWorkerTest {

    private lateinit var db: AppDatabase
    private lateinit var pendingDao: PendingUploadDao
    private lateinit var historyDao: top.mcxiafeng.badger.data.queue.OperationHistoryDao
    private lateinit var serverApi: ServerApi
    private lateinit var deviceIdProvider: DeviceIdProvider
    private lateinit var executor: PendingUploadExecutor

    @Before
    fun setup() {
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { RuntimeEnvironment.getApplication() }
                    single { AppDatabase.build(get()) }
                },
            )
        }
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        pendingDao = db.pendingUploadDao()
        historyDao = db.operationHistoryDao()
        serverApi = mockk(relaxed = true)
        deviceIdProvider = mockk(relaxed = true)
        coEvery { deviceIdProvider.deviceId() } returns "test-device-uuid"
        executor = PendingUploadExecutor(
            pendingDao = pendingDao,
            historyDao = historyDao,
            deviceIdProvider = deviceIdProvider,
            serverApi = serverApi,
            contactCacheDao = db.contactCacheDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        runCatching { GlobalContext.stopKoin() }
    }

    private suspend fun seedOp(
        opId: String,
        opType: String = OpType.PATCH_CONTACT,
        payload: String = """{"server_id":"srv-1","name":"张三"}""",
        nextAttemptAt: Long = 0L,
    ) {
        pendingDao.enqueue(
            PendingUploadEntity(
                opId = opId,
                contactId = 1L,
                opType = opType,
                resourceVersion = 5L,
                payloadJson = payload,
                createdAt = 1_000L,
                status = "PENDING",
                nextAttemptAt = nextAttemptAt,
                deviceId = "test-device",
            )
        )
        historyDao.insert(
            OperationHistoryEntity(
                opId = opId,
                contactId = 1L,
                opType = opType,
                opLabel = "op-$opId",
                payloadJson = payload,
                snapshotBeforeJson = """{"contact_id":1}""",
                createdAt = 1_000L,
                opStatus = "PENDING",
                canUndo = true,
                canReplay = true,
            )
        )
    }

    /**
     * 模拟 Worker 内部循环:拉批 → markInFlight → executor.execute。语义与 [PendingUploadWorker.doWork] 一致,
     * 等价于在 Robolectric 下用 WorkManager 测试驱动器跑 Worker。
     */
    private suspend fun runWorkerOnce(): Int {
        val now = System.currentTimeMillis()
        var processed = 0
        while (true) {
            val batch = pendingDao.nextReady(now = now, limit = PendingUploadWorker.BATCH_SIZE)
            if (batch.isEmpty()) return processed
            for (op in batch) {
                if (pendingDao.markInFlight(op.opId, lastAttemptAt = now) == 0) continue
                executor.execute(op.copy(status = "IN_FLIGHT", lastAttemptAt = now), now = now)
                processed++
            }
            if (batch.size < PendingUploadWorker.BATCH_SIZE) return processed
        }
    }

    @Test
    fun runWorker_emptyQueue_processesNothing() = runTest {
        val processed = runWorkerOnce()
        assertThat(processed).isEqualTo(0)
    }

    @Test
    fun runWorker_processesAllPendingOps() = runTest {
        repeat(3) { seedOp(opId = "op-$it") }
        coEvery { serverApi.patchContact(any(), any(), any()) } returns ServerApi.ContactResponse(
            id = "srv-1", serverId = "srv-1", version = 1L, contact = JsonObject(),
        )

        val processed = runWorkerOnce()
        assertThat(processed).isEqualTo(3)

        repeat(3) { i ->
            assertThat(pendingDao.getById("op-$i")?.status).isEqualTo("DONE")
        }
    }

    @Test
    fun runWorker_skipsOpWithFutureNextAttemptAt() = runTest {
        seedOp(opId = "op-now", nextAttemptAt = 0L)
        seedOp(opId = "op-future", nextAttemptAt = System.currentTimeMillis() + 60_000L)
        coEvery { serverApi.patchContact(any(), any(), any()) } returns ServerApi.ContactResponse(
            id = "srv-1", serverId = "srv-1", version = 1L, contact = JsonObject(),
        )

        runWorkerOnce()

        assertThat(pendingDao.getById("op-now")?.status).isEqualTo("DONE")
        assertThat(pendingDao.getById("op-future")?.status).isEqualTo("PENDING")
    }

    @Test
    fun runWorker_5xx_doesNotStopBatch() = runTest {
        seedOp(opId = "op-err", payload = """{"server_id":"srv-err"}""")
        seedOp(opId = "op-ok", payload = """{"server_id":"srv-ok"}""")

        coEvery { serverApi.patchContact("srv-err", any(), any()) } throws ApiException(500, "internal", "contacts.patch")
        coEvery { serverApi.patchContact("srv-ok", any(), any()) } returns ServerApi.ContactResponse(
            id = "srv-ok", serverId = "srv-ok", version = 1L, contact = JsonObject(),
        )

        runWorkerOnce()

        assertThat(pendingDao.getById("op-err")?.status).isEqualTo("FAILED")
        assertThat(pendingDao.getById("op-ok")?.status).isEqualTo("DONE")
    }

    @Test
    fun runWorker_409_marksConflictAndContinues() = runTest {
        seedOp(opId = "op-cf", payload = """{"server_id":"srv-cf"}""")
        seedOp(opId = "op-ok", payload = """{"server_id":"srv-ok"}""")

        coEvery { serverApi.patchContact("srv-cf", any(), any()) } throws ServerApi.ConflictException(
            ServerApi.ConflictResponse(serverVersion = 99L, serverContact = null),
            "contacts.patch",
        )
        coEvery { serverApi.patchContact("srv-ok", any(), any()) } returns ServerApi.ContactResponse(
            id = "srv-ok", serverId = "srv-ok", version = 1L, contact = JsonObject(),
        )

        runWorkerOnce()

        assertThat(pendingDao.getById("op-cf")?.status).isEqualTo("CONFLICT")
        assertThat(pendingDao.getById("op-ok")?.status).isEqualTo("DONE")
    }

    @Test
    fun runWorker_shortBatchExitsImmediately() = runTest {
        seedOp(opId = "op-1")
        coEvery { serverApi.patchContact(any(), any(), any()) } returns ServerApi.ContactResponse(
            id = "srv-1", serverId = "srv-1", version = 1L, contact = JsonObject(),
        )

        val processed = runWorkerOnce()
        assertThat(processed).isEqualTo(1)
        assertThat(pendingDao.getById("op-1")?.status).isEqualTo("DONE")
    }

    @Test
    fun runWorker_largerBatchProcessedInOneRun() = runTest {
        // 12 条 op(超过 BATCH_SIZE=8);第一轮拉 8,第二轮拉剩余 4 → 都 DONE
        repeat(12) { seedOp(opId = "op-$it") }
        coEvery { serverApi.patchContact(any(), any(), any()) } returns ServerApi.ContactResponse(
            id = "srv-1", serverId = "srv-1", version = 1L, contact = JsonObject(),
        )

        val processed = runWorkerOnce()
        assertThat(processed).isEqualTo(12)

        repeat(12) { i ->
            assertThat(pendingDao.getById("op-$i")?.status).isEqualTo("DONE")
        }
    }

    @Test
    fun runWorker_casLock_failsForAlreadyInFlightOp() = runTest {
        // 模拟另一个 Worker 已经把 op 标 IN_FLIGHT;当前 Worker 应跳过
        val op = PendingUploadEntity(
            opId = "op-locked",
            contactId = 1L,
            opType = OpType.PATCH_CONTACT,
            resourceVersion = 5L,
            payloadJson = """{"server_id":"srv-1"}""",
            createdAt = 1_000L,
            status = "PENDING",
            deviceId = "test-device",
        )
        pendingDao.enqueue(op)
        // 抢锁成功
        assertThat(pendingDao.markInFlight("op-locked", lastAttemptAt = 1L)).isEqualTo(1)
        // 二次抢锁失败
        assertThat(pendingDao.markInFlight("op-locked", lastAttemptAt = 2L)).isEqualTo(0)
    }
}