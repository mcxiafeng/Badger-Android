package top.mcxiafeng.badger.shared.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.ConstructedBy
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Entity(tableName = "contacts_cache")
data class SpikeContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "pinyinInitial") val pinyinInitial: String,
    @ColumnInfo(name = "isDeleted") val isDeleted: Boolean,
)

@Dao
interface SpikeContactDao {
    
    @Query("SELECT * FROM contacts_cache WHERE isDeleted = 0 AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<SpikeContactEntity>

    @Insert
    suspend fun insertAll(items: List<SpikeContactEntity>)

    @Query("SELECT COUNT(*) FROM contacts_cache")
    suspend fun count(): Int
}

@Database(
    entities = [SpikeContactEntity::class],
    version = 2,
    exportSchema = false,
)
@ConstructedBy(SpikeDatabaseConstructor::class)
abstract class SpikeDatabase : RoomDatabase() {
    abstract fun contactDao(): SpikeContactDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SpikeDatabaseConstructor : RoomDatabaseConstructor<SpikeDatabase>

val SPIKE_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS contacts_cache_new (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "pinyinInitial TEXT NOT NULL, " +
                "isDeleted INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO contacts_cache_new (id, name, pinyinInitial, isDeleted) " +
                "SELECT id, name, '', isDeleted FROM contacts_cache"
        )
        db.execSQL("DROP TABLE contacts_cache")
        db.execSQL("ALTER TABLE contacts_cache_new RENAME TO contacts_cache")
    }
}
