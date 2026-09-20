package top.mcxiafeng.badger.data.system.database

import androidx.room.RoomDatabase
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * SystemDatabase 全局单例持有者，模式同 [[top.mcxiafeng.badger.data.user.database.CacheDbHolder]]。
 */
object SystemDbHolder {

    const val SYSTEM_DB_FILE = "badger_system.db"

    private var instance: SystemDatabase? = null

    fun init(builder: RoomDatabase.Builder<SystemDatabase>) {
        check(instance == null) { "SystemDatabase 已初始化，禁止重复 init" }
        instance = builder.build()
        BadgerLog.d("SystemDbHolderTester", "SystemDatabase 初始化完成")
    }

    fun get(): SystemDatabase =
        instance ?: error("SystemDatabase 未初始化：请先在应用入口调用 SystemDbHolder.init()")

    /**
     * 丢弃当前实例。仅测试用：Robolectric 每个测试方法结束后 shadow 连接被重置，
     * 而同 JVM 的测试类共享静态——跨类必须重建实例，否则拿着死连接指针查询。
     */
    fun resetForTest() {
        instance = null
    }
}
