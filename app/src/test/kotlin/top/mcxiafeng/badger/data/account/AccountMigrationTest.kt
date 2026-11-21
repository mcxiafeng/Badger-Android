package top.mcxiafeng.badger.data.account

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.SyncStateEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountMigrationTest {
    private lateinit var context: Context
    private lateinit var scope: AccountScope
    private var opened: AppDatabase? = null

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        scope = AccountStore(context).localScope()
        context.deleteDatabase(scope.databaseName)
    }

    @After
    fun cleanup() {
        opened?.close()
        context.deleteDatabase(scope.databaseName)
    }

    @Test
    fun frozenV5FixtureMigratesInPlaceAndRetainsBusinessDataAndFts() = runBlocking(Dispatchers.IO) {
        seedV5Fixture()
        val db = AccountDatabaseProvider(context).open(scope).also { opened = it }
        assertThat(db.contactDao().getContactById(CONTACT_ID)?.name).isEqualTo("legacy-contact")
        assertThat(db.contactDao().getContactById(CONTACT_ID)?.bio).isEqualTo("legacy-bio")
        assertThat(db.userProfileDao().getProfileOnce()?.cardImagePath).isEqualTo("legacy-card.webp")
        val sql = db.openHelper.writableDatabase
        assertThat(sql.version).isEqualTo(CURRENT_VERSION)
        listOf("custom_fields", "contact_field_values", "card_collections", "scan_results", "contact_platforms", "tags", "contact_tag")
            .forEach { table ->
                sql.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getInt(0)).isEqualTo(1)
                }
            }
        sql.query("SELECT rowid FROM contacts_fts WHERE contacts_fts MATCH 'legacy' AND rowid = 42").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(CONTACT_ID)
        }
        assertThat(db.syncStateDao().getAll()).isEmpty()
        db.syncStateDao().put(SyncStateEntity("mapping", "retained"))
        assertThat(db.syncStateDao().get("mapping")).isEqualTo("retained")
        assertThat(db.contactDao().insertContact(Contact(name = "after-migration"))).isGreaterThan(CONTACT_ID)
    }

    @Test
    fun syncStateAndBusinessRowsCommitAndRollBackTogether() = runBlocking(Dispatchers.IO) {
        val db = AccountDatabaseProvider(context).open(scope).also { opened = it }
        val result = runCatching {
            db.withTransaction {
                db.contactDao().insertContact(Contact(id = CONTACT_ID, name = "must-rollback"))
                db.syncStateDao().put(SyncStateEntity("outbox", "must-rollback"))
                error("Test transaction rollback")
            }
        }
        assertThat(result.isFailure).isTrue()
        assertThat(db.contactDao().getContactById(CONTACT_ID)).isNull()
        assertThat(db.syncStateDao().getAll()).isEmpty()
        db.withTransaction {
            db.contactDao().insertContact(Contact(id = CONTACT_ID, name = "committed"))
            db.syncStateDao().put(SyncStateEntity("mapping", "one"))
            db.syncStateDao().put(SyncStateEntity("mapping", "two"))
        }
        assertThat(db.syncStateDao().getAll()).containsExactly(SyncStateEntity("mapping", "two"))
        db.syncStateDao().delete("mapping")
        assertThat(db.syncStateDao().get("mapping")).isNull()
        db.syncStateDao().put(SyncStateEntity("other", "value"))
        db.syncStateDao().clear()
        assertThat(db.syncStateDao().getAll()).isEmpty()
        assertThat(db.contactDao().getContactById(CONTACT_ID)?.name).isEqualTo("committed")
    }

    @Test
    fun unsupportedDatabaseVersionFailsWithoutDestructiveFallback() = runBlocking(Dispatchers.IO) {
        seedV5Fixture(UNSUPPORTED_VERSION)
        val db = AccountDatabaseProvider(context).open(scope).also { opened = it }
        assertThat(runCatching { db.contactDao().getContactById(CONTACT_ID) }.isFailure).isTrue()
        db.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(scope.databaseName).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertThat(raw.version).isEqualTo(UNSUPPORTED_VERSION)
            raw.rawQuery("SELECT name FROM contacts WHERE id = ?", arrayOf(CONTACT_ID.toString())).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("legacy-contact")
            }
        }
    }

    private fun seedV5Fixture(version: Int = PREVIOUS_VERSION) {
        val file = context.getDatabasePath(scope.databaseName)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            val resource = checkNotNull(javaClass.getResourceAsStream("/account/v5-fixture.sql"))
            resource.bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("--") }.forEach(raw::execSQL)
            }
            raw.execSQL("INSERT INTO contacts (id,name,bio,pinyinInitial,createTime,updateTime) VALUES (42,'legacy-contact','legacy-bio','L',1,2)")
            raw.execSQL("INSERT INTO contact_fields (id,fieldName,fieldKey,sortOrder,isSystem,isEnabled,createTime) VALUES (99,'Phone','phone',0,1,1,1)")
            raw.execSQL("INSERT INTO custom_fields (id,fieldName,fieldType,options,sortOrder,isEnabled,createTime) VALUES (8,'Custom','text','[]',0,1,1)")
            raw.execSQL("INSERT INTO contact_field_values (id,contactId,customFieldId,value,createTime,updateTime) VALUES (9,42,8,'legacy-value',1,2)")
            raw.execSQL("INSERT INTO card_collections (id,name,createTime) VALUES (7,'legacy-collection',1)")
            raw.execSQL("INSERT INTO scan_results (id,contactId,collectionId,scannedTime,sourceType,confidence) VALUES (9,42,7,1,'scan',1)")
            raw.execSQL("INSERT INTO user_profile (id,name,cardImagePath,updateTime) VALUES (1,'legacy-self','legacy-card.webp',2)")
            raw.execSQL("INSERT INTO contact_platforms (id,contactId,platformKey,value,jumpLink) VALUES (10,42,'qq','123','https://example.test')")
            raw.execSQL("INSERT INTO tags (id,name,color,pinyinInitial,source,showDot,createTime) VALUES (11,'legacy-tag',1,'L','manual',1,1)")
            raw.execSQL("INSERT INTO contact_tag (contactId,tagId,source,confidence,createTime) VALUES (42,11,'manual',1,1)")
            raw.version = version
        }
    }

    companion object {
        private const val CONTACT_ID = 42L
        private const val PREVIOUS_VERSION = 5
        private const val CURRENT_VERSION = 6
        private const val UNSUPPORTED_VERSION = 99
    }
}
