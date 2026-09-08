package top.mcxiafeng.badger.shared.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import kotlin.reflect.KClass

/**
 * [K02 spike] Android actual：Context 文件路径。
 *
 * [崩溃修复 2026-09-09] 不再 setDriver(BundledSQLiteDriver)：
 * bundled driver 下 Room 不创建 SupportSQLiteOpenHelper（无 Factory），
 * 而 room-ktx 的 `withTransaction`（DbTransaction.android actual / OutboxStore /
 * ContactWriter 的事务路径）会调 getOpenHelper() → 必崩
 * "Cannot return a SupportSQLiteOpenHelper since no SupportSQLiteOpenHelper.Factory was configured"。
 * Android 走 framework SQLite（KMP 可选），iOS 端 bundled driver 不受影响。
 */
object SpikeContextHolder {
    @Volatile
    var appContext: Context? = null
}

actual fun platformSpikeDatabaseBuilder(name: String): RoomDatabase.Builder<SpikeDatabase> {
    val context = SpikeContextHolder.appContext
        ?: error("SpikeContextHolder.appContext not initialized")
    return Room.databaseBuilder(context, SpikeDatabase::class.java, name)
}

/**
 * [KMP K07] Android actual：与旧库完全同一文件（getDatabasePath）。
 */
fun <T : RoomDatabase> androidDatabaseBuilder(
    klass: Class<T>,
    name: String,
): RoomDatabase.Builder<T> {
    val context = SpikeContextHolder.appContext
        ?: error("SpikeContextHolder.appContext not initialized")
    return Room.databaseBuilder(context, klass, name)
}

actual object PlatformContextHolder {
    actual fun inject(context: Any) {
        SpikeContextHolder.appContext = context as Context
    }
}
