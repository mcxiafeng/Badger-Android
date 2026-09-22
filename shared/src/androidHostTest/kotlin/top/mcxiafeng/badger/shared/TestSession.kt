package top.mcxiafeng.badger.shared

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.robolectric.RuntimeEnvironment
import top.mcxiafeng.badger.data.system.database.SystemDatabase
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.entity.UserInfo
import top.mcxiafeng.badger.data.user.database.CacheDatabase
import java.util.concurrent.Executors
import kotlin.uuid.Uuid

/** 开发后端预置会话（经 /api/auth/me 实测对齐：token 属主与 profileId）。 */
object TestSession {
    const val SEED_TOKEN = "XVQG98HHhIR5OdDYhxRZLsw56VKbwpeVON1DwvCz3Iw"
    val SEED_USER_UUID: Uuid = Uuid.parse("b3115f13-1e09-4046-9f1f-37d61829a0da")
    val SEED_PROFILE_UUID: Uuid = Uuid.parse("2f421318-76cb-4650-96c7-95eefd50b8e7")

    private val dbExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "test-db").apply {
            isDaemon = true
            // 跨测试类残留的 Room InvalidationTracker 刷新会在环境销毁后撞死连接指针，
            // 属测试基建噪音：打印归属，不许它污染下一个 runTest 的 UncaughtExceptions 检查
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                println("test-db 未捕获异常（测试基建噪音）: $e")
            }
        }
    }

    /**
     * Robolectric legacy SQLite shadow 按线程登记连接指针，Room 默认执行器多线程
     * 会撞 "Illegal connection pointer"——所有测试内存库必须共用这一个单线程执行器。
     */
    val dbDispatcher: CoroutineDispatcher = dbExecutor.asCoroutineDispatcher()

    fun systemDb(context: Context): RoomDatabase.Builder<SystemDatabase> =
        Room.inMemoryDatabaseBuilder(context, SystemDatabase::class.java)
            .setQueryCoroutineContext(dbDispatcher)

    fun cacheDb(context: Context): RoomDatabase.Builder<CacheDatabase> =
        Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .setQueryCoroutineContext(dbDispatcher)

    /** 释放跨类共享的 holder 库实例：其 InvalidationTracker 刷新任务会在环境销毁后撞死连接指针。 */
    fun closeHolderDb() {
        runCatching { SystemDbHolder.get().close() }
    }

    /** 每个测试类重建 holder 实例（跨类静态共享但连接随 shadow 重置）并种入登录会话。 */
    suspend fun ensureLoggedIn() {
        SystemDbHolder.resetForTest()
        SystemDbHolder.init(systemDb(RuntimeEnvironment.getApplication()))
        val dao = SystemDbHolder.get().userInfoDao()
        dao.clearAllUserInfos()
        dao.upsertUserInfo(UserInfo(userUuid = SEED_USER_UUID, token = SEED_TOKEN))
    }
}
