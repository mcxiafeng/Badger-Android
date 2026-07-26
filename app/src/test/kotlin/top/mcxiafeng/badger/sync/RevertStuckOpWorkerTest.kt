package top.mcxiafeng.badger.sync

import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import androidx.room.Room
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity

/**
 * [V2-P6] RevertStuckOpWorker 30s 恢复窗口单测。
 *
 * 覆盖规约 `docs/BADGER_V2_CLIENT_PLAN.md` §5.5.3:
 * 1. 已 DONE → no-op
 * 2. FAILED_PERMANENT → 复活 isDeleted=false(只有 DELETE_CONTACT)
 * 3. CONFLICT → 复活 isDeleted=false
 * 4. PENDING / IN_FLIGHT → 等 Worker 自然完结(不强行变更)
 * 5. WITHDRAWN → no-op
 * 6. 缺 opId → Result.failure
 * 7. 未知 opId(不在 pending_uploads 里) → Result.success(no-op)
 * 8. opType 不是 DELETE_CONTACT → 不复活(避免 MERGE_CONTACT 等被误回滚)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RevertStuckOpWorkerTest {

    private lateinit var db: AppDatabase
    private lateinit var pendingDao: PendingUploadDao
    private lateinit var historyDao: OperationHistoryDao
    private lateinit var contactCacheDao: ContactCacheDao

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
    }

    private fun buildWorker(inputData: Data): RevertStuckOpWorker {
        // [修复防御]: WorkerParameters 内部构造极复杂(Robolectric 模拟困难),
        // 用 mockk 桩 WorkerParameters 让 inputData 简单可读,直接避免 WorkManager TestDriver 初始化。
        val params: WorkerParameters = mockk(relaxed = true)
        every { params.inputData } returns inputData
        return RevertStuckOpWorker(
            appContext = RuntimeEnvironment.getApplication(),
            params = params,
            pendingDao = pendingDao,
            historyDao = historyDao,
            contactCacheDao = contactCacheDao,
        )
    }

    private suspend fun seedContact(id: Long, isDeleted: Boolean) {
        contactCacheDao.insertContact(
            top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity(
                id = id,
                name = "Bob",
                createTime = 1000L,
                updateTime = 1000L,
                isDeleted = isDeleted,
            )
        )
    }

    private suspend fun seedOp(
        opId: String = "op-1",
        contactId: Long = 1L,
        opType: String = "DELETE_CONTACT",
        status: String = "FAILED_PERMANENT",
    ) {
        pendingDao.enqueue(
            PendingUploadEntity(
                opId = opId,
                contactId = contactId,
                opType = opType,
                resourceVersion = 5L,
                payloadJson = """{"server_id":"srv-1"}""",
                createdAt = 1_000L,
                status = status,
                deviceId = "test-device",
            )
        )
        historyDao.insert(
            OperationHistoryEntity(
                opId = opId,
                contactId = contactId,
                opType = opType,
                opLabel = "删除联系人",
                payloadJson = """{"server_id":"srv-1"}""",
                snapshotBeforeJson = """{"contact_id":$contactId}""",
                createdAt = 1_000L,
                opStatus = status,
                canUndo = false,
                canReplay = true,
            )
        )
    }

    @Test
    fun doWork_doneStatus_skipsRevive() = runTest {
        seedContact(1L, isDeleted = true)
        seedOp(status = "DONE")

        val worker = buildWorker(workDataOf(RevertStuckOpWorker.KEY_OP_ID to "op-1"))
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        // DONE 不应复活
        val contact = contactCacheDao.getContactById(1L)
        assertThat(contact?.isDeleted).isTrue()
    }

    @Test
    fun doWork_failedPermanent_revives() = runTest {
        seedContact(1L, isDeleted = true)
        seedOp(status = "FAILED_PERMANENT")

        val worker = buildWorker(workDataOf(RevertStuckOpWorker.KEY_OP_ID to "op-1"))
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        // FAILED_PERMANENT → 复活 isDeleted=false
        val contact = contactCacheDao.getContactById(1L)
        assertThat(contact?.isDeleted).isFalse()
    }

    @Test
    fun doWork_conflict_revives() = runTest {
        seedContact(1L, isDeleted = true)
        seedOp(status = "CONFLICT")

        val worker = buildWorker(workDataOf(RevertStuckOpWorker.KEY_OP_ID to "op-1"))
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val contact = contactCacheDao.getContactById(1L)
        assertThat(contact?.isDeleted).isFalse()
    }

    @Test
    fun doWork_pendingStatus_doesNotModify() = runTest {
        seedContact(1L, isDeleted = true)
        seedOp(status = "PENDING")

        val worker = buildWorker(workDataOf(RevertStuckOpWorker.KEY_OP_ID to "op-1"))
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        // PENDING 仍等 Worker 自然完结,不应复活
        val contact = contactCacheDao.getContactById(1L)
        assertThat(contact?.isDeleted).isTrue()
    }

    @Test
    fun doWork_inFlightStatus_doesNotModify() = runTest {
        seedContact(1L, isDeleted = true)
        seedOp(status = "IN_FLIGHT")

        val worker = buildWorker(workDataOf(RevertStuckOpWorker.KEY_OP_ID to "op-1"))
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val contact = contactCacheDao.getContactById(1L)
        assertThat(contact?.isDeleted).isTrue()
    }

    @Test
    fun doWork_withdrawnStatus_skipsRevive() = runTest {
        seedContact(1L, isDeleted = true)
        seedOp(status = "WITHDRAWN")

        val worker = buildWorker(workDataOf(RevertStuckOpWorker.KEY_OP_ID to "op-1"))
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val contact = contactCacheDao.getContactById(1L)
        assertThat(contact?.isDeleted).isTrue()
    }

    @Test
    fun doWork_missingOpId_returnsFailure() = runTest {
        val worker = buildWorker(Data.EMPTY)
        val result = worker.doWork()
        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }

    @Test
    fun doWork_unknownOpId_returnsSuccess() = runTest {
        val worker = buildWorker(workDataOf(RevertStuckOpWorker.KEY_OP_ID to "op-nonexistent"))
        val result = worker.doWork()
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
    }

    @Test
    fun doWork_nonDeleteOpType_doesNotRevive() = runTest {
        seedContact(1L, isDeleted = true)
        seedOp(opType = "MERGE_CONTACT", status = "FAILED_PERMANENT")

        val worker = buildWorker(workDataOf(RevertStuckOpWorker.KEY_OP_ID to "op-1"))
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        // MERGE_CONTACT 应保留 isDeleted=true(用户已合并,不应误复活)
        val contact = contactCacheDao.getContactById(1L)
        assertThat(contact?.isDeleted).isTrue()
    }
}
