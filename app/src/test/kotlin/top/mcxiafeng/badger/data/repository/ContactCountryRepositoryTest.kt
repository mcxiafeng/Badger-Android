package top.mcxiafeng.badger.data.repository

import android.app.Application
import android.database.sqlite.SQLiteException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ContactCountryRepositoryTest {
    @get:Rule val dbRule = InMemoryDatabaseRule(RuntimeEnvironment.getApplication())
    private lateinit var repo: FieldRepository
    private var contactId = 0L

    @Before
    fun setUp() = runTest {
        val db = dbRule.db
        repo = FieldRepositoryImpl(db.contactFieldDao(), db.customFieldDao(), db.contactFieldValueDao(), db.contactPlatformDao(), db)
        db.contactFieldDao().insertField(ContactField(fieldName = "国家", fieldKey = "country"))
        db.contactFieldDao().insertField(ContactField(fieldName = "地区", fieldKey = "region"))
        contactId = db.contactDao().insertContact(Contact(name = "测试联系人"))
        repo.updateFieldValueByKey(contactId, "region", "朝阳区")
    }

    @Test
    fun keepsRegionOnFirstCountryAndReselection_thenClearsOnCountryChange() = runTest {
        repo.updateContactCountry(contactId, "中国")
        assertThat(repo.getFieldValueByContactAndKey(contactId, "region")).isEqualTo("朝阳区")
        repo.updateContactCountry(contactId, "中国")
        assertThat(repo.getFieldValueByContactAndKey(contactId, "region")).isEqualTo("朝阳区")

        repo.updateContactCountry(contactId, "日本")

        assertThat(repo.getFieldValueByContactAndKey(contactId, "country")).isEqualTo("日本")
        assertThat(repo.getFieldValueByContactAndKey(contactId, "region")).isEmpty()
    }

    @Test
    fun rollsBackCountryChangeWhenRegionClearFails() = runTest {
        repo.updateContactCountry(contactId, "中国")
        dbRule.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_region_clear BEFORE UPDATE ON contact_field_values " +
                "WHEN NEW.value = '' BEGIN SELECT RAISE(ABORT, 'test rejected region clear'); END",
        )

        val failure = runCatching { repo.updateContactCountry(contactId, "日本") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SQLiteException::class.java)
        assertThat(repo.getFieldValueByContactAndKey(contactId, "country")).isEqualTo("中国")
        assertThat(repo.getFieldValueByContactAndKey(contactId, "region")).isEqualTo("朝阳区")
    }
}
