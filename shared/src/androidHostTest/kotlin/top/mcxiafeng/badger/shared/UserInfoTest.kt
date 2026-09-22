package top.mcxiafeng.badger.shared

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.system.database.SystemDatabase
import top.mcxiafeng.badger.data.system.entity.UserInfo
import kotlin.uuid.Uuid

/**
 * UserSyncState 载体契约探针：Room 内存库，验证 Token 存取与增量游标读写规则。
 *
 * 契约（见 UserSyncState KDoc）：
 * - syncVersion=0 表示从未同步（首次拉取 since=0 全量）；
 * - lastSyncTime 只随 advanceSyncCursor 记录服务端下发时间，禁止本地时钟写入；
 * - 游标推进以 syncVersion 为准，token 与游标互不影响。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserInfoTest {

    @Test
    fun carrierRoundTrip() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, SystemDatabase::class.java).build()
        val dao = db.userSyncStateDao()
        val user = Uuid.random()

        // 未登录：无状态行
        assertNull(dao.getState(user))

        // 登录写入：token 落库，游标停在 0（下次 since=0 = 全量）
        dao.upsertState(UserInfo(userUuid = user, token = "tk-1"))
        val initial = dao.getState(user)!!
        assertEquals("tk-1", initial.token)
        assertEquals(0L, initial.syncVersion)
        assertEquals(0L, initial.lastSyncTime)

        // 推进游标：版本号权威，服务端下发时间随行记录
        val serverTime = 1_760_000_000_000L
        dao.advanceSyncCursor(user, syncVersion = 7L, serverTime = serverTime)
        val advanced = dao.getState(user)!!
        assertEquals(7L, advanced.syncVersion)
        assertEquals(serverTime, advanced.lastSyncTime)
        assertEquals("tk-1", advanced.token)

        // token 换发不影响游标
        dao.updateToken(user, "tk-2")
        val relogged = dao.getState(user)!!
        assertEquals("tk-2", relogged.token)
        assertEquals(7L, relogged.syncVersion)
        assertEquals(serverTime, relogged.lastSyncTime)

        // observe 流跟随
        assertEquals(7L, dao.observeState(user).first()?.syncVersion)

        // 登出清理
        dao.deleteState(user)
        assertNull(dao.getState(user))
        db.close()
    }

    @Test
    fun activeSessionSemantics() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, SystemDatabase::class.java).build()
        val dao = db.userSyncStateDao()
        val userA = Uuid.random()
        val userB = Uuid.random()

        // 单会话模型：登录 = clearAllStates 后只保留一行，getActiveState 即当前会话
        dao.upsertState(UserInfo(userUuid = userA, token = "tk-a"))
        dao.upsertState(UserInfo(userUuid = userB, token = "tk-b"))
        dao.clearAllStates()
        assertNull(dao.getActiveState())

        dao.upsertState(UserInfo(userUuid = userA, token = "tk-a2", syncVersion = 5L, lastSyncTime = 99L))
        val active = dao.getActiveState()!!
        assertEquals(userA, active.userUuid)
        assertEquals("tk-a2", active.token)
        assertEquals(5L, active.syncVersion)
        assertEquals(99L, active.lastSyncTime)
        db.close()
    }
}
