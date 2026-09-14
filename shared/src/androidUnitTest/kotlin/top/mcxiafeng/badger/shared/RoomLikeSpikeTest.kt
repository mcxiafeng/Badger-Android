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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomLikeSpikeTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    
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

        
        val zhHits = db.contactDao().searchByName("张")
        assertEquals(1, zhHits.size)
        assertEquals("张三", zhHits.first().name)

        
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
        
        assertEquals("", migrated.first().pinyinInitial)

        db.close()
    }
}
