package top.mcxiafeng.badger.data.dao

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule
import top.mcxiafeng.badger.testutil.TestDataProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContactDaoTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule(org.robolectric.RuntimeEnvironment.getApplication())

    private lateinit var dao: ContactDao

    @Before
    fun setup() {
        dao = dbRule.db.contactDao()
    }

    @Test
    fun getAllContacts_returnsAllInsertedContacts() = runTest {
        dao.insertContact(TestDataProvider.testContact(name = "张三"))
        dao.insertContact(TestDataProvider.testContact(name = "李四"))
        val contacts = dao.getAllContacts().first()
        assertThat(contacts).hasSize(2)
    }

    @Test
    fun getContactById_existingId_returnsContact() = runTest {
        val id = dao.insertContact(TestDataProvider.testContact(name = "张三"))
        val contact = dao.getContactById(id)
        assertThat(contact).isNotNull()
        assertThat(contact!!.name).isEqualTo("张三")
    }

    @Test
    fun getContactById_nonexistentId_returnsNull() = runTest {
        val contact = dao.getContactById(999L)
        assertThat(contact).isNull()
    }

    @Test
    fun insertContact_conflictReplaces() = runTest {
        val original = TestDataProvider.testContact(id = 10, name = "原名")
        dao.insertContact(original)
        val updated = TestDataProvider.testContact(id = 10, name = "新名")
        dao.insertContact(updated)
        val contact = dao.getContactById(10)
        assertThat(contact!!.name).isEqualTo("新名")
    }

    @Test
    fun deleteContact_cascadesFieldValuesAndScanResults() = runTest {
        val contactId = dao.insertContact(TestDataProvider.testContact(name = "张三"))
        val collectionId = dbRule.db.cardCollectionDao().insertCollection(
            TestDataProvider.testCardCollection(name = "测试")
        )
        dbRule.db.contactFieldValueDao().insertFieldValue(
            TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "13800138000")
        )
        dbRule.db.scanResultDao().insertScanResult(
            TestDataProvider.testScanResult(contactId = contactId, collectionId = collectionId)
        )

        dao.deleteContact(TestDataProvider.testContact(id = contactId, name = "张三"))

        assertThat(dao.getContactById(contactId)).isNull()
        assertThat(dbRule.db.contactFieldValueDao().getFieldValuesByContactOnce(contactId)).isEmpty()
        assertThat(dbRule.db.scanResultDao().getScanResultsByContact(contactId).first()).isEmpty()
    }

    @Test
    fun searchContacts_byName_returnsMatching() = runTest {
        dao.insertContact(TestDataProvider.testContact(name = "张三"))
        dao.insertContact(TestDataProvider.testContact(name = "李四"))
        val results = dao.searchContacts("张").first()
        assertThat(results).hasSize(1)
        assertThat(results[0].name).isEqualTo("张三")
    }

    @Test
    fun searchContacts_byFieldValue_returnsMatching() = runTest {
        val contactId = dao.insertContact(TestDataProvider.testContact(name = "张三"))
        dao.insertContact(TestDataProvider.testContact(name = "李四"))
        dbRule.db.contactFieldValueDao().insertFieldValue(
            TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "13800138000")
        )
        val results = dao.searchContacts("138").first()
        assertThat(results).hasSize(1)
        assertThat(results[0].name).isEqualTo("张三")
    }

    @Test
    fun searchContacts_noMatch_returnsEmpty() = runTest {
        dao.insertContact(TestDataProvider.testContact(name = "张三"))
        val results = dao.searchContacts("不存在的名字").first()
        assertThat(results).isEmpty()
    }

    @Test
    fun getContactsByCollection_returnsOnlyCollectionMembers() = runTest {
        val contact1 = dao.insertContact(TestDataProvider.testContact(name = "张三"))
        val contact2 = dao.insertContact(TestDataProvider.testContact(name = "李四"))
        val collectionId = dbRule.db.cardCollectionDao().insertCollection(
            TestDataProvider.testCardCollection(name = "工作")
        )
        dbRule.db.scanResultDao().insertScanResult(
            TestDataProvider.testScanResult(contactId = contact1, collectionId = collectionId)
        )
        val results = dao.getContactsByCollection(collectionId).first()
        assertThat(results).hasSize(1)
        assertThat(results[0].name).isEqualTo("张三")
    }

    @Test
    fun getContactsByCollectionOnce_returnsList() = runTest {
        val contact1 = dao.insertContact(TestDataProvider.testContact(name = "张三"))
        val collectionId = dbRule.db.cardCollectionDao().insertCollection(
            TestDataProvider.testCardCollection(name = "工作")
        )
        dbRule.db.scanResultDao().insertScanResult(
            TestDataProvider.testScanResult(contactId = contact1, collectionId = collectionId)
        )
        val results = dao.getContactsByCollectionOnce(collectionId)
        assertThat(results).hasSize(1)
    }
}
