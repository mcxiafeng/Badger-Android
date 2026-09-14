package top.mcxiafeng.badger.shared.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import kotlin.reflect.KClass

object SpikeContextHolder {
    @Volatile
    var appContext: Context? = null
}

actual fun platformSpikeDatabaseBuilder(name: String): RoomDatabase.Builder<SpikeDatabase> {
    val context = SpikeContextHolder.appContext
        ?: error("SpikeContextHolder.appContext not initialized")
    return Room.databaseBuilder(context, SpikeDatabase::class.java, name)
}

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
