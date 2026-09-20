package top.mcxiafeng.badger.data.user.database

import androidx.room.RoomDatabase
import top.mcxiafeng.badger.utils.BadgerLog

object CacheDbHolder {

    const val CACHE_DB_FILE = "badger_cache.db"

    private var instance: CacheDatabase? = null

    fun init(builder: RoomDatabase.Builder<CacheDatabase>) {
        check(instance == null) { "CacheDatabase 已初始化，禁止重复 init" }
        instance = builder.build()
        BadgerLog.d("CacheDbHolderTester", "CacheDatabase 初始化完成")
    }

    fun get(): CacheDatabase =
        instance ?: error("CacheDatabase 未初始化：请先在应用入口调用 CacheDbHolder.init()")
}
