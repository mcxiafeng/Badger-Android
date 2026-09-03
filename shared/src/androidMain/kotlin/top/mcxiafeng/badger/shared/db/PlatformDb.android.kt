package top.mcxiafeng.badger.shared.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * [K02 spike] Android actual：Context 文件路径 + bundled driver。
 * K07 迁移真实 AppDatabase 时保持 `context.getDatabasePath` 同一文件。
 */
object SpikeContextHolder {
    @Volatile
    var appContext: Context? = null
}

actual fun platformSpikeDatabaseBuilder(name: String): RoomDatabase.Builder<SpikeDatabase> {
    val context = SpikeContextHolder.appContext
        ?: error("SpikeContextHolder.appContext not initialized")
    return Room.databaseBuilder(context, SpikeDatabase::class.java, name)
        .setDriver(BundledSQLiteDriver())
}
