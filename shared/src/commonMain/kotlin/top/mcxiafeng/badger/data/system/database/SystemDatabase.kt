package top.mcxiafeng.badger.data.system.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import top.mcxiafeng.badger.data.Converters
import top.mcxiafeng.badger.data.system.dao.SyncAtomDao
import top.mcxiafeng.badger.data.system.dao.UserInfoDao
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.data.system.entity.UserInfo

@Database(entities = [SyncAtom::class, UserInfo::class], version = 1)
@ConstructedBy(SystemDatabaseConstructor::class)
@TypeConverters(Converters::class)
abstract class SystemDatabase : RoomDatabase() {
    abstract fun syncAtomDao(): SyncAtomDao
    abstract fun userInfoDao(): UserInfoDao
}

@Suppress("KotlinNoActualForExpect")
expect object SystemDatabaseConstructor : RoomDatabaseConstructor<SystemDatabase> {
    override fun initialize(): SystemDatabase
}
