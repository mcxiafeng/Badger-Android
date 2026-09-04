package top.mcxiafeng.badger.shared.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room.RoomDatabaseConstructor
import kotlin.reflect.KClass

/**
 * [K02 spike] Android actual：Context 文件路径 + bundled driver。
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

/**
 * [KMP K07] Android actual：与旧库完全同一文件（getDatabasePath），只换 driver。
 */
/**
 * [KMP K07] Android 侧 bundled-driver builder（供 AppDatabase.build 调用；
 * 非泛型——AppDatabase 当前在 app 模块，K08 迁 commonMain 后换 Constructor 模式）。
 */
fun <T : RoomDatabase> androidDatabaseBuilder(
    klass: Class<T>,
    name: String,
): RoomDatabase.Builder<T> {
    val context = SpikeContextHolder.appContext
        ?: error("SpikeContextHolder.appContext not initialized")
    return Room.databaseBuilder(context, klass, name)
        .setDriver(BundledSQLiteDriver())
}

actual object PlatformContextHolder {
    actual fun inject(context: Any) {
        SpikeContextHolder.appContext = context as Context
    }
}
