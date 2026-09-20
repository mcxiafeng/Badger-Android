package top.mcxiafeng.badger.data.user.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers

fun androidCacheDatabaseBuilder(context: Context): RoomDatabase.Builder<CacheDatabase> {
    val dbFile = context.getDatabasePath(CacheDbHolder.CACHE_DB_FILE)
    return Room.databaseBuilder<CacheDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath,
    )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setQueryCoroutineContext(Dispatchers.IO)
}
