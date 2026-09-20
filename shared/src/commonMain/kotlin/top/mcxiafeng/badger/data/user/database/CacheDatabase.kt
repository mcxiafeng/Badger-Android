package top.mcxiafeng.badger.data.user.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import top.mcxiafeng.badger.data.Converters
import top.mcxiafeng.badger.data.user.dao.CollectionDao
import top.mcxiafeng.badger.data.user.dao.PersonDao
import top.mcxiafeng.badger.data.user.dao.ProfileDao
import top.mcxiafeng.badger.data.user.dao.TagsDao
import top.mcxiafeng.badger.data.user.dao.UserDao
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.CollectionPersonRef
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.PersonTagsRef
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.data.user.entity.User
import top.mcxiafeng.badger.data.user.entity.UserPersonRef
import top.mcxiafeng.badger.data.user.entity.UserTagsRef

@Database(
    entities = [
        User::class,
        Collection::class,
        Person::class,
        Profile::class,
        Tags::class,
        UserPersonRef::class,
        UserTagsRef::class,
        CollectionPersonRef::class,
        PersonTagsRef::class
    ],
    version = 2
)
@ConstructedBy(CacheDatabaseConstructor::class)
@TypeConverters(Converters::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun collectionDao(): CollectionDao
    abstract fun personDao(): PersonDao
    abstract fun profileDao(): ProfileDao
    abstract fun tagsDao(): TagsDao
}

@Suppress("KotlinNoActualForExpect")
expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    override fun initialize(): CacheDatabase
}
