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
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule
import top.mcxiafeng.badger.testutil.TestDataProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScanResultDaoTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule(org.robolectric.RuntimeEnvironment.getApplication())

    private lateinit var dao: ScanResultDao
    private var contactId: Long = 0
    private var collectionId: Long = 0

    @Before
    fun setup() = runTest {
        dao = dbRule.db.scanResultDao()
        contactId = dbRule.db.contactDao().insertContact(TestDataProvider.testContact(name = "张三"))
        collectionId = dbRule.db.cardCollectionDao().insertCollection(TestDataProvider.testCardCollection(name = "测试"))
    }

    @Test
    fun insertScanResult_addsRecord() = runTest {
        dao.insertScanResult(TestDataProvider.testScanResult(contactId = contactId, collectionId = collectionId))
        val results = dao.getAllScanResults().first()
        assertThat(results).hasSize(1)
    }

    @Test
    fun getScanResultsByContact_returnsOnlyContactRecords() = runTest {
        val contact2Id = dbRule.db.contactDao().insertContact(TestDataProvider.testContact(name = "李四"))
        dao.insertScanResult(TestDataProvider.testScanResult(contactId = contactId, collectionId = collectionId))
        dao.insertScanResult(TestDataProvider.testScanResult(contactId = contact2Id, collectionId = collectionId))
        val results = dao.getScanResultsByContact(contactId).first()
        assertThat(results).hasSize(1)
    }

    @Test
    fun deleteScanResultById_removesRecord() = runTest {
        dao.insertScanResult(TestDataProvider.testScanResult(id = 100, contactId = contactId, collectionId = collectionId))
        dao.deleteScanResultById(100)
        assertThat(dao.getAllScanResults().first()).isEmpty()
    }

    @Test
    fun deleteScanResultsByContactAndCollection_removesMatching() = runTest {
        dao.insertScanResult(TestDataProvider.testScanResult(contactId = contactId, collectionId = collectionId))
        dao.deleteScanResultsByContactAndCollection(contactId, collectionId)
        assertThat(dao.getAllScanResults().first()).isEmpty()
    }

    @Test
    fun findPotentialDuplicates_byQrCodeContent() = runTest {
        dbRule.db.contactFieldValueDao().insertFieldValue(
            TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "13800138000")
        )
        dao.insertScanResult(
            TestDataProvider.testScanResult(
                contactId = contactId, collectionId = collectionId,
                qrCodeContent = "https://qq.com/123456"
            )
        )
        val results = dao.findPotentialDuplicates("123456")
        assertThat(results).hasSize(1)
        assertThat(results[0].name).isEqualTo("张三")
    }

    @Test
    fun findPotentialDuplicates_byOcrText() = runTest {
        dbRule.db.contactFieldValueDao().insertFieldValue(
            TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "13800138000")
        )
        dao.insertScanResult(
            TestDataProvider.testScanResult(
                contactId = contactId, collectionId = collectionId,
                ocrText = "张三 手机13800138000"
            )
        )
        val results = dao.findPotentialDuplicates("138")
        assertThat(results).hasSize(1)
    }

    @Test
    fun findPotentialDuplicates_excludesSpecifiedId() = runTest {
        dao.insertScanResult(
            TestDataProvider.testScanResult(
                contactId = contactId, collectionId = collectionId,
                qrCodeContent = "test_content"
            )
        )
        dbRule.db.contactFieldValueDao().insertFieldValue(
            TestDataProvider.testFieldValue(contactId = contactId, fieldId = 1L, value = "13800138000")
        )
        val results = dao.findPotentialDuplicates("test_content", excludeId = contactId)
        assertThat(results).isEmpty()
    }

    @Test
    fun findPotentialDuplicates_noMatch_returnsEmpty() = runTest {
        dao.insertScanResult(
            TestDataProvider.testScanResult(contactId = contactId, collectionId = collectionId)
        )
        val results = dao.findPotentialDuplicates("不存在的内容")
        assertThat(results).isEmpty()
    }

    @Test
    fun getStyleCountsByCollection_returnsMap() = runTest {
        dao.insertScanResult(TestDataProvider.testScanResult(contactId = contactId, collectionId = collectionId))
        val counts = dao.getStyleCountsByCollection(collectionId)
        assertThat(counts).hasSize(1)
        assertThat(counts[contactId]).isEqualTo(1)
    }
}