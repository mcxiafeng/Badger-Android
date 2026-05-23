package top.mcxiafeng.badger.data.dao

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.ContactFieldValueDao
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule
import top.mcxiafeng.badger.testutil.TestDataProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContactFieldValueDaoTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule(org.robolectric.RuntimeEnvironment.getApplication())

    private lateinit var dao: ContactFieldValueDao
    private var contactId: Long = 0

    @Before
    fun setup() = runTest {
        dao = dbRule.db.contactFieldValueDao()
        contactId = dbRule.db.contactDao().insertContact(TestDataProvider.testContact(name = "张三"))
    }

    @Test
    fun getFieldValuesByContactOnce_returnsOnlyContactValues() = runTest {
        val contact2Id = dbRule.db.contactDao().insertContact(TestDataProvider.testContact(name = "李四"))
        dao.insertFieldValue(TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "138"))
        dao.insertFieldValue(TestDataProvider.testFieldValue(contactId = contact2Id, fieldId = 1L, value = "139"))
        val values = dao.getFieldValuesByContactOnce(contactId)
        assertThat(values).hasSize(1)
        assertThat(values[0].value).isEqualTo("138")
    }

    @Test
    fun insertFieldValue_allowsMultipleSameField() = runTest {
        dao.insertFieldValue(TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "13800138000"))
        dao.insertFieldValue(TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "13900139000"))
        val values = dao.getFieldValuesByContactOnce(contactId)
        assertThat(values).hasSize(2)
    }

    @Test
    fun insertOrUpdateFieldValues_batchInsert() = runTest {
        val values = listOf(
            TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "138"),
            TestDataProvider.testFieldValue(contactId = contactId, fieldId = 2L, value = "test@example.com")
        )
        dao.insertOrUpdateFieldValues(values)
        val saved = dao.getFieldValuesByContactOnce(contactId)
        assertThat(saved).hasSize(2)
    }

    @Test
    fun deleteFieldValue_removesSingle() = runTest {
        val id = dao.insertFieldValue(TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "138"))
        dao.deleteFieldValue(TestDataProvider.testFieldValue(id = id, contactId = contactId, fieldId = 1L, value = "138"))
        assertThat(dao.getFieldValuesByContactOnce(contactId)).isEmpty()
    }

    @Test
    fun deleteFieldValue_removesAllForContact_whenDeletingEach() = runTest {
        dao.insertFieldValue(TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "138"))
        dao.insertFieldValue(TestDataProvider.testFieldValue(contactId = contactId, fieldId = 2L, value = "test@test.com"))
        val values = dao.getFieldValuesByContactOnce(contactId)
        for (v in values) {
            dao.deleteFieldValue(v)
        }
        assertThat(dao.getFieldValuesByContactOnce(contactId)).isEmpty()
    }

    @Test
    fun getFieldValue_returnsValue() = runTest {
        dao.insertFieldValue(TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "13800138000"))
        val value = dao.getFieldValue(contactId, 1L)
        assertThat(value).isEqualTo("13800138000")
    }

    @Test
    fun getFieldValue_nonexistent_returnsNull() = runTest {
        val value = dao.getFieldValue(contactId, 999L)
        assertThat(value).isNull()
    }

    @Test
    fun getCustomFieldValue_returnsValue() = runTest {
        val customFieldId = dbRule.db.customFieldDao().insertCustomField(TestDataProvider.testCustomField(fieldName = "公司"))
        dao.insertFieldValue(TestDataProvider.testFieldValue(contactId = contactId, customFieldId = customFieldId, value = "Google"))
        val value = dao.getCustomFieldValue(contactId, customFieldId)
        assertThat(value).isEqualTo("Google")
    }
}