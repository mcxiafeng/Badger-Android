package top.mcxiafeng.badger.shared.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * [K02 spike] contacts_cache 最小复刻（17 版 schema 子集，列对齐 ContactCacheEntity）。
 * 用于在 bundled driver 下验证建表 / LIKE 搜索 / 保守重建型 migration 的行为。
 */
@Entity(tableName = "contacts_cache")
data class SpikeContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "pinyinInitial") val pinyinInitial: String,
    @ColumnInfo(name = "isDeleted") val isDeleted: Boolean,
)

@Dao
interface SpikeContactDao {
    /** 对齐 ContactCacheDao.searchByName 的 LIKE 语义（三表联查的 name 列部分） */
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
abstract class SpikeDatabase : RoomDatabase() {
    abstract fun contactDao(): SpikeContactDao
}

/**
 * [K02 spike] 保守重建型 migration（1→2 模拟 MIGRATION_6_7 的
 * 「create new → copy → drop → rename」模式），验证 bundled driver 下的行为：
 * v1 表无 pinyinInitial 列，重建后补列 + 空串默认值，数据行全保留。
 *
 * [K07 知识点] Room KMP 的 Migration 签名是 KMP 驱动层的 androidx.sqlite.SQLiteConnection，
 * 不是 Android 的 SupportSQLiteDatabase——AppDatabase 现有 16 条迁移直用 SupportSQLiteDatabase
 * 的地方（execSQL 之外）需经 room-sqlite-wrapper 桥接。
 */
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
