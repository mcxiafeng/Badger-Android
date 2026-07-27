package top.mcxiafeng.badger.data.queue

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase

/**
 * [V2-P2] PendingUploadDao 状态机 + FIFO 契约测试。
 *
 * 覆盖规约 docs/BADGER_V2_CLIENT_PLAN.md §4.2 / §4.4 / §5.5.4:
 * 1. enqueue → nextReady 拉到 (FIFO)
 * 2. markInFlight 双 Worker 抢锁 (CAS 语义)
 * 3. markFailed 写回 attempts + 退避时间
 * 4. markFailedPermanent 终结
 * 5. recoverFromDirect 把 IN_FLIGHT → PENDING
 * 6. retryNow 清错误 + 立即可执行
 * 7. purgeDone / deleteFinished 仅清理终态
 *
 * 跑 Robolectric 是为了拿到 Application context 建内存 Room db,
 * 避免被 @HiltAndroidApp 启动 OpenCV 等副作用干扰。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PendingUploadDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PendingUploadDao

    @Before
    fun setup() {
        // [§14.2] Robolectric 测试不走 BadgerApplication.onCreate;若 ViewModel/Repository
        // 任何路径触到 KoinComponentBy.get(),必须先 startKoin。这里强制 stop+start,
        // 保证同一 JVM 的上一个测试即便残留 GlobalContext 也不会撞 KoinApplicationAlreadyStartedException。
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
        dao = db.pendingUploadDao()
    }

    @After
    fun tearDown() {
        db.close()
        runCatching { GlobalContext.stopKoin() }
    }

    private fun op(
        opId: String,
        contactId: Long = 1,
        status: String = "PENDING",
        createdAt: Long = System.currentTimeMillis(),
        nextAttemptAt: Long = createdAt,
        attempts: Int = 0,
    ) = PendingUploadEntity(
        opId = opId,
        contactId = contactId,
        opType = "UPDATE_NAME",
        resourceVersion = 0,
        payloadJson = """{"name":"张三"}""",
        createdAt = createdAt,
        status = status,
        attempts = attempts,
        nextAttemptAt = nextAttemptAt,
        deviceId = "test-device",
    )

    // ============ 基础入队 / FIFO ============

    @Test
    fun enqueue_single_persistsRow() = runTest {
        dao.enqueue(op("op-1"))
        val loaded = dao.getById("op-1")
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.opType).isEqualTo("UPDATE_NAME")
        assertThat(loaded.status).isEqualTo("PENDING")
    }

    @Test
    fun nextReady_FIFOOrdersByCreatedAtAsc() = runTest {
        val now = System.currentTimeMillis()
        dao.enqueue(op("op-1", createdAt = now + 100))
        dao.enqueue(op("op-2", createdAt = now + 200))
        dao.enqueue(op("op-3", createdAt = now + 300))
        val ready = dao.nextReady(now = now + 1000, limit = 8)
        assertThat(ready.map { it.opId }).containsExactly("op-1", "op-2", "op-3").inOrder()
    }

    @Test
    fun nextReady_skipsOpsNotReadyYet() = runTest {
        val now = System.currentTimeMillis()
        dao.enqueue(op("op-now", createdAt = now, nextAttemptAt = now))
        dao.enqueue(op("op-future", createdAt = now, nextAttemptAt = now + 60_000))
        val ready = dao.nextReady(now = now, limit = 8)
        assertThat(ready.map { it.opId }).containsExactly("op-now")
    }

    @Test
    fun nextReady_skipsOpsNotPending() = runTest {
        val now = System.currentTimeMillis()
        dao.enqueue(op("op-pending", createdAt = now))
        dao.enqueue(op("op-done", createdAt = now + 1, status = "DONE"))
        dao.enqueue(op("op-conflict", createdAt = now + 2, status = "CONFLICT"))
        dao.enqueue(op("op-failed", createdAt = now + 3, status = "FAILED"))
        val ready = dao.nextReady(now = now, limit = 8)
        assertThat(ready.map { it.opId }).containsExactly("op-pending")
    }

    @Test
    fun nextReady_respectsLimit() = runTest {
        val now = System.currentTimeMillis()
        for (i in 1..10) dao.enqueue(op("op-$i", createdAt = now + i))
        val ready = dao.nextReady(now = now + 100, limit = 3)
        assertThat(ready).hasSize(3)
    }

    // ============ 状态机:enqueue → IN_FLIGHT → DONE / CONFLICT / FAILED ============

    @Test
    fun markInFlight_succeedsFromPending() = runTest {
        dao.enqueue(op("op-1"))
        val rows = dao.markInFlight(opId = "op-1", lastAttemptAt = 1000L)
        assertThat(rows).isEqualTo(1)
        assertThat(dao.getById("op-1")!!.status).isEqualTo("IN_FLIGHT")
    }

    @Test
    fun markInFlight_returnsZero_whenAlreadyInFlight() = runTest {
        // [修复防御]: 双 Worker 抢锁,第二个 markInFlight 必须返回 0,
        // Worker 见到受影响行数 = 0 就放弃该 op,避免同一条 op 被并发发两次。
        dao.enqueue(op("op-1"))
        dao.markInFlight("op-1", 1000L)
        val rows = dao.markInFlight("op-1", 2000L)
        assertThat(rows).isEqualTo(0)
    }

    @Test
    fun markDone_setsStatus() = runTest {
        dao.enqueue(op("op-1", status = "IN_FLIGHT"))
        dao.markDone("op-1")
        assertThat(dao.getById("op-1")!!.status).isEqualTo("DONE")
    }

    @Test
    fun markConflict_setsStatusAndLastError() = runTest {
        dao.enqueue(op("op-1", status = "IN_FLIGHT"))
        dao.markConflict("op-1", "version mismatch")
        val loaded = dao.getById("op-1")!!
        assertThat(loaded.status).isEqualTo("CONFLICT")
        assertThat(loaded.lastError).isEqualTo("version mismatch")
    }

    @Test
    fun markFailed_updatesAttemptsAndBackoff() = runTest {
        val now = System.currentTimeMillis()
        dao.enqueue(op("op-1", status = "IN_FLIGHT"))
        dao.markFailed(
            opId = "op-1",
            attempts = 1,
            lastError = "503",
            now = now,
            nextAttemptAt = now + 2_000L,
        )
        val loaded = dao.getById("op-1")!!
        assertThat(loaded.status).isEqualTo("FAILED")
        assertThat(loaded.attempts).isEqualTo(1)
        assertThat(loaded.lastError).isEqualTo("503")
        assertThat(loaded.nextAttemptAt).isEqualTo(now + 2_000L)
    }

    @Test
    fun markFailedPermanent_terminatesRetry() = runTest {
        val now = System.currentTimeMillis()
        dao.enqueue(op("op-1", status = "IN_FLIGHT", attempts = 8))
        dao.markFailedPermanent("op-1", "max reached", now)
        val loaded = dao.getById("op-1")!!
        assertThat(loaded.status).isEqualTo("FAILED_PERMANENT")
        // nextReady 应跳过
        assertThat(dao.nextReady(now = now + 999_999, limit = 8)).isEmpty()
    }

    @Test
    fun markWithdrawn_skippedByNextReady() = runTest {
        dao.enqueue(op("op-1"))
        dao.markWithdrawn("op-1")
        assertThat(dao.nextReady(now = System.currentTimeMillis(), limit = 8)).isEmpty()
    }

    // ============ recoverFromDirect(直发失败 → Worker 兜底) ============

    @Test
    fun recoverFromDirect_movesInFlightBackToPending() = runTest {
        val now = System.currentTimeMillis()
        dao.enqueue(op("op-1", status = "IN_FLIGHT"))
        val rows = dao.recoverFromDirect(
            opId = "op-1",
            lastError = "HTTP ConnectException",
            now = now,
        )
        assertThat(rows).isEqualTo(1)
        val loaded = dao.getById("op-1")!!
        assertThat(loaded.status).isEqualTo("PENDING")
        assertThat(loaded.lastError).isEqualTo("HTTP ConnectException")
        assertThat(loaded.nextAttemptAt).isEqualTo(now)
    }

    @Test
    fun recoverFromDirect_returnsZero_whenNotInFlight() = runTest {
        // 已经是 PENDING,不能 recover(防止二次重置)
        dao.enqueue(op("op-1", status = "PENDING"))
        val rows = dao.recoverFromDirect("op-1", "x", System.currentTimeMillis())
        assertThat(rows).isEqualTo(0)
    }

    // ============ retryNow(用户从历史页立即重试) ============

    @Test
    fun retryNow_clearsErrorAndAttempts() = runTest {
        val now = System.currentTimeMillis()
        dao.enqueue(op("op-1", status = "FAILED_PERMANENT", attempts = 8))
        dao.markFailedPermanent("op-1", "max", now)
        dao.retryNow("op-1", now)
        val loaded = dao.getById("op-1")!!
        assertThat(loaded.status).isEqualTo("PENDING")
        assertThat(loaded.attempts).isEqualTo(0)
        assertThat(loaded.lastError).isNull()
        assertThat(loaded.nextAttemptAt).isEqualTo(now)
    }

    // ============ 清理 ============

    @Test
    fun purgeDone_removes_doneOps() = runTest {
        val now = System.currentTimeMillis()
        dao.enqueue(op("op-1", status = "DONE", createdAt = now - 100_000))
        dao.enqueue(op("op-2", status = "DONE", createdAt = now - 100))
        val removed = dao.purgeDone(before = now - 1000)
        assertThat(removed).isEqualTo(1)
        assertThat(dao.getById("op-1")).isNull()
        assertThat(dao.getById("op-2")).isNotNull()
    }

    @Test
    fun deleteFinished_refusesToDeleteActiveOps() = runTest {
        // [修复防御]: 只允许删 DONE / WITHDRAWN,绝不允许删 PENDING / IN_FLIGHT
        dao.enqueue(op("op-pending", status = "PENDING"))
        dao.enqueue(op("op-inflight", status = "IN_FLIGHT"))
        dao.enqueue(op("op-done", status = "DONE"))
        val removed = dao.deleteFinished("op-pending")
        assertThat(removed).isEqualTo(0)
        removed.toString() // keep var live
        val removedInflight = dao.deleteFinished("op-inflight")
        assertThat(removedInflight).isEqualTo(0)
        val removedDone = dao.deleteFinished("op-done")
        assertThat(removedDone).isEqualTo(1)
    }

    // ============ 计数 ============

    @Test
    fun countByStatus_groupsCorrectly() = runTest {
        val now = System.currentTimeMillis()
        dao.enqueue(op("op-1", createdAt = now + 1))
        dao.enqueue(op("op-2", createdAt = now + 2, status = "DONE"))
        dao.enqueue(op("op-3", createdAt = now + 3, status = "FAILED"))
        assertThat(dao.count()).isEqualTo(3)
        assertThat(dao.countByStatus("PENDING")).isEqualTo(1)
        assertThat(dao.countByStatus("DONE")).isEqualTo(1)
        assertThat(dao.countByStatus("FAILED")).isEqualTo(1)
    }
}