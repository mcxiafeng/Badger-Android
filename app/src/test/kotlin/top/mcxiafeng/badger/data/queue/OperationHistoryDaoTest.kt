package top.mcxiafeng.badger.data.queue

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
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

/**
 * [V2-P2] OperationHistoryDao 测试。
 *
 * 覆盖规约 docs/BADGER_V2_CLIENT_PLAN.md §6:
 * 1. 写入 + 按 opId 读
 * 2. 倒序分页 + 顶栏徽章数字
 * 3. 状态转移:DONE 写 serverVersion + CONFLICT 写 currentSnapshot
 * 4. purgeOld 仅清理终态(CONFLICT / FAILED 绝不被自动删,等用户处理)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OperationHistoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: OperationHistoryDao

    @Before
    fun setup() {
        // [§14.2] Robolectric 测试不走 BadgerApplication.onCreate;若 ViewModel/Repository
        // 任何路径触到 KoinComponentBy.get(),必须先 startKoin。
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
        dao = db.operationHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
        runCatching { GlobalContext.stopKoin() }
    }

    private fun hist(
        opId: String,
        contactId: Long = 1,
        opType: String = "UPDATE_NAME",
        opStatus: String = "PENDING",
        createdAt: Long = System.currentTimeMillis(),
        canUndo: Boolean = true,
        canReplay: Boolean = false,
        inversePayloadJson: String? = """{"name":"旧名字"}""",
        snapshotBeforeJson: String = """{"name":"旧名字"}""",
    ) = OperationHistoryEntity(
        opId = opId,
        contactId = contactId,
        opType = opType,
        opLabel = "改名字",
        payloadJson = """{"name":"新名字"}""",
        snapshotBeforeJson = snapshotBeforeJson,
        snapshotAfterJson = null,
        createdAt = createdAt,
        opStatus = opStatus,
        canUndo = canUndo,
        canReplay = canReplay,
        inversePayloadJson = inversePayloadJson,
    )

    // ============ 基础 ============

    @Test
    fun insert_thenGetById_returnsPayload() = runTest {
        dao.insert(hist("op-1"))
        val loaded = dao.getById("op-1")
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.opType).isEqualTo("UPDATE_NAME")
        assertThat(loaded.inversePayloadJson).isEqualTo("""{"name":"旧名字"}""")
    }

    @Test
    fun getByContact_filtersByContactId() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(hist("op-1", contactId = 1, createdAt = now + 1))
        dao.insert(hist("op-2", contactId = 2, createdAt = now + 2))
        dao.insert(hist("op-3", contactId = 1, createdAt = now + 3))
        val list = dao.getByContact(contactId = 1, limit = 50)
        assertThat(list.map { it.opId }).containsExactly("op-3", "op-1").inOrder()
    }

    // ============ 分页 / 倒序 ============

    @Test
    fun getPage_ordersByCreatedAtDescAndPaginates() = runTest {
        val now = System.currentTimeMillis()
        for (i in 1..5) dao.insert(hist("op-$i", createdAt = now + i))
        val page1 = dao.getPage(limit = 2, offset = 0)
        val page2 = dao.getPage(limit = 2, offset = 2)
        val page3 = dao.getPage(limit = 2, offset = 4)
        assertThat(page1.map { it.opId }).containsExactly("op-5", "op-4").inOrder()
        assertThat(page2.map { it.opId }).containsExactly("op-3", "op-2").inOrder()
        assertThat(page3.map { it.opId }).containsExactly("op-1").inOrder()
    }

    @Test
    fun observeRecent_emitsOrderedList() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(hist("op-1", createdAt = now + 1))
        dao.insert(hist("op-2", createdAt = now + 2))
        dao.insert(hist("op-3", createdAt = now + 3))
        val list = dao.observeRecent(limit = 10).first()
        assertThat(list.map { it.opId }).containsExactly("op-3", "op-2", "op-1").inOrder()
    }

    // ============ 状态转移 ============

    @Test
    fun markDone_writesServerVersionAndSnapshotAfter() = runTest {
        dao.insert(hist("op-1"))
        dao.markDone(
            opId = "op-1",
            serverVersion = 42L,
            snapshotAfterJson = """{"name":"新名字"}""",
        )
        val loaded = dao.getById("op-1")!!
        assertThat(loaded.opStatus).isEqualTo("DONE")
        assertThat(loaded.serverVersion).isEqualTo(42L)
        assertThat(loaded.snapshotAfterJson).isEqualTo("""{"name":"新名字"}""")
        assertThat(loaded.lastError).isNull()
    }

    @Test
    fun markConflict_writesServerVersionAndError() = runTest {
        dao.insert(hist("op-1"))
        dao.markConflict(
            opId = "op-1",
            serverVersion = 99L,
            lastError = "version_conflict: current=99, expected=42",
        )
        val loaded = dao.getById("op-1")!!
        assertThat(loaded.opStatus).isEqualTo("CONFLICT")
        assertThat(loaded.serverVersion).isEqualTo(99L)
        assertThat(loaded.lastError).contains("version_conflict")
    }

    @Test
    fun markFailed_updatesAttempts() = runTest {
        dao.insert(hist("op-1"))
        dao.markFailed(opId = "op-1", attempts = 3, lastError = "503")
        val loaded = dao.getById("op-1")!!
        assertThat(loaded.opStatus).isEqualTo("FAILED")
        assertThat(loaded.attempts).isEqualTo(3)
        assertThat(loaded.lastError).isEqualTo("503")
    }

    @Test
    fun markWithdrawn_doesNotTouchCanUndo() = runTest {
        // 撤销不影响 canUndo 标记——撤回本身也是可撤回的(op 记录还在)
        dao.insert(hist("op-1", canUndo = true))
        dao.markWithdrawn("op-1")
        val loaded = dao.getById("op-1")!!
        assertThat(loaded.opStatus).isEqualTo("WITHDRAWN")
        assertThat(loaded.canUndo).isTrue()
    }

    // ============ observePending(待处理徽章数字) ============

    @Test
    fun observePending_filtersConflictAndFailedAndEmitsFromFlow() = runTest {
        dao.insert(hist("op-1", opStatus = "CONFLICT"))
        dao.insert(hist("op-2", opStatus = "FAILED_PERMANENT"))
        dao.insert(hist("op-3", opStatus = "DONE"))
        dao.insert(hist("op-4", opStatus = "PENDING"))
        val pending = dao.observePending(limit = 50).first()
        assertThat(pending.map { it.opId }).containsExactly("op-2", "op-1").inOrder()
    }

    // ============ 清理 ============

    @Test
    fun purgeOld_keepsActiveStates() = runTest {
        // [修复防御]: CONFLICT / FAILED / PENDING / IN_FLIGHT 绝不被自动清理
        val now = System.currentTimeMillis()
        dao.insert(hist("op-done", opStatus = "DONE", createdAt = now - 100_000))
        dao.insert(hist("op-done2", opStatus = "FAILED_PERMANENT", createdAt = now - 100_000))
        dao.insert(hist("op-conflict", opStatus = "CONFLICT", createdAt = now - 100_000))
        dao.insert(hist("op-failed", opStatus = "FAILED", createdAt = now - 100_000))
        dao.insert(hist("op-pending", opStatus = "PENDING", createdAt = now - 100_000))
        dao.insert(hist("op-withdrawn", opStatus = "WITHDRAWN", createdAt = now - 100_000))
        val removed = dao.purgeOld(before = now - 1000)
        // 仅 DONE / FAILED_PERMANENT 进入终态后清理。
        // CONFLICT / FAILED:用户可能想"采用本地/服务端"或"立即重试"
        // PENDING / IN_FLIGHT:队列中
        // WITHDRAWN:用户的"反悔入口"
        assertThat(removed).isEqualTo(2)  // op-done + op-done2
        assertThat(dao.getById("op-done")).isNull()
        assertThat(dao.getById("op-done2")).isNull()
        assertThat(dao.getById("op-conflict")).isNotNull()
        assertThat(dao.getById("op-failed")).isNotNull()
        assertThat(dao.getById("op-pending")).isNotNull()
        assertThat(dao.getById("op-withdrawn")).isNotNull()
    }

    // ============ 计数 ============

    @Test
    fun countByStatus_countsDone() = runTest {
        dao.insert(hist("op-1", opStatus = "DONE"))
        dao.insert(hist("op-2", opStatus = "DONE"))
        dao.insert(hist("op-3", opStatus = "PENDING"))
        assertThat(dao.count()).isEqualTo(3)
        assertThat(dao.countByStatus("DONE")).isEqualTo(2)
        assertThat(dao.countByStatus("PENDING")).isEqualTo(1)
    }
}