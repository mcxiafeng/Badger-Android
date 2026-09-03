package top.mcxiafeng.badger.shared.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [K02 spike] Room KMP 行为验证（JVM/Robolectric + JdbcSqliteDriver）。
 *
 * 驱动选型说明：bundled driver 在 Windows JVM 单测缺 `sqliteJni` native（2026-09-04 实测
 * UnsatisfiedLinkError），设备无关的语义验证改走 JDBC driver——LIKE 大小写规则、保守重建
 * migration 均为 SQLite 引擎行为，与驱动无关；bundled driver 的行为验证留 K07 Android 模拟器。
 * 结论记入 docs/kmp-dependency-matrix.md §3。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomLikeSpikeTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    /** Room KMP builder + bundled driver（sqlite-bundled-jvm 提供 JVM native） */
    private fun buildSpikeDb(dbName: String): RoomDatabase.Builder<SpikeDatabase> {
        val appContext = context()
        SpikeContextHolder.appContext = appContext
        return Room.databaseBuilder(appContext, SpikeDatabase::class.java, dbName)
            .setDriver(BundledSQLiteDriver())
    }

    @Test
    fun `LIKE search with Chinese and ASCII keywords matches Android semantics`() = runTest {
        val db = buildSpikeDb("spike-like-${System.nanoTime()}.db").build()

        db.contactDao().insertAll(
            listOf(
                SpikeContactEntity(name = "张三", pinyinInitial = "Z", isDeleted = false),
                SpikeContactEntity(name = "张老三", pinyinInitial = "Z", isDeleted = true),
                SpikeContactEntity(name = "李四", pinyinInitial = "L", isDeleted = false),
                SpikeContactEntity(name = "abc Def", pinyinInitial = "A", isDeleted = false),
            )
        )

        // 中文子串命中，isDeleted 行排除
        val zhHits = db.contactDao().searchByName("张")
        assertEquals(1, zhHits.size)
        assertEquals("张三", zhHits.first().name)

        // ASCII LIKE 大小写不敏感（SQLite 同引擎语义，与 Android 现状一致）
        val asciiHits = db.contactDao().searchByName("ABC")
        assertEquals(1, asciiHits.size)
        val noHit = db.contactDao().searchByName("王")
        assertEquals(0, noHit.size)

        db.close()
    }

    @Test
    fun `conservative rebuild migration preserves data`() = runTest {
        val appContext = context()
        val dbName = "spike-mig-${System.nanoTime()}.db"
        val dbPath = appContext.getDatabasePath(dbName).absolutePath
        appContext.getDatabasePath(dbName).parentFile?.mkdirs()

        // 手工造 v1 库（无 pinyinInitial 列 + user_version=1，模拟老用户升级）
        val conn = BundledSQLiteDriver().open(dbPath)
        conn.execSQL(
            "CREATE TABLE contacts_cache (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "isDeleted INTEGER NOT NULL)"
        )
        conn.execSQL("INSERT INTO contacts_cache (id, name, isDeleted) VALUES (1, '王五', 0)")
        conn.execSQL("INSERT INTO contacts_cache (id, name, isDeleted) VALUES (2, '赵六', 0)")
        conn.execSQL("PRAGMA user_version = 1")
        conn.close()

        val db = buildSpikeDb(dbName)
            .addMigrations(SPIKE_MIGRATION_1_2)
            .build()

        assertEquals(2, db.contactDao().count())
        val migrated = db.contactDao().searchByName("王五")
        assertEquals(1, migrated.size)
        // 重建补列后的默认值
        assertEquals("", migrated.first().pinyinInitial)

        db.close()
    }
}
