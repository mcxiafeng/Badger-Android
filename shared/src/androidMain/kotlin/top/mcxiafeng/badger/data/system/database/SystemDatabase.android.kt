package top.mcxiafeng.badger.data.system.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers

/**
 * Android 平台 SystemDatabase builder：配置同 CacheDatabase.android（框架 SQLite，不设驱动）。
 */
fun androidSystemDatabaseBuilder(context: Context): RoomDatabase.Builder<SystemDatabase> {
    val dbFile = context.getDatabasePath(SystemDbHolder.SYSTEM_DB_FILE)
    return Room.databaseBuilder<SystemDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath,
    )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setQueryCoroutineContext(Dispatchers.IO)
}
