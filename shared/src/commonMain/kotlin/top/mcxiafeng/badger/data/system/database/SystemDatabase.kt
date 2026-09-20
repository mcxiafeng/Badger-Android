package top.mcxiafeng.badger.data.system.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import top.mcxiafeng.badger.data.Converters
import top.mcxiafeng.badger.data.system.dao.SyncAtomDao
import top.mcxiafeng.badger.data.system.dao.UserSyncStateDao
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.data.system.entity.UserSyncState

@Database(entities = [SyncAtom::class, UserSyncState::class], version = 1)
@ConstructedBy(SystemDatabaseConstructor::class)
@TypeConverters(Converters::class)
abstract class SystemDatabase : RoomDatabase() {
    abstract fun syncAtomDao(): SyncAtomDao
    abstract fun userSyncStateDao(): UserSyncStateDao
}

@Suppress("KotlinNoActualForExpect")
expect object SystemDatabaseConstructor : RoomDatabaseConstructor<SystemDatabase> {
    override fun initialize(): SystemDatabase
}
