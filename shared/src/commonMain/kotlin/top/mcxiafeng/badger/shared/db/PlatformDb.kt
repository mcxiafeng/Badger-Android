package top.mcxiafeng.badger.shared.db

import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

expect fun platformSpikeDatabaseBuilder(name: String): RoomDatabase.Builder<SpikeDatabase>

expect object PlatformContextHolder {
    fun inject(context: Any)
}
