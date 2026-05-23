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
import top.mcxiafeng.badger.data.CardCollectionDao
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule
import top.mcxiafeng.badger.testutil.TestDataProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CardCollectionDaoTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule(org.robolectric.RuntimeEnvironment.getApplication())

    private lateinit var dao: CardCollectionDao

    @Before
    fun setup() {
        dao = dbRule.db.cardCollectionDao()
    }

    @Test
    fun getAllCollections_returnsSortedByName() = runTest {
        dao.insertCollection(TestDataProvider.testCardCollection(name = "工作"))
        dao.insertCollection(TestDataProvider.testCardCollection(name = "A收藏"))
        val collections = dao.getAllCollections().first()
        assertThat(collections.map { it.name }).isInOrder()
    }

    @Test
    fun getCollectionsWithCount_correctCount() = runTest {
        val collectionId = dao.insertCollection(TestDataProvider.testCardCollection(name = "测试合集"))
        val contactId = dbRule.db.contactDao().insertContact(TestDataProvider.testContact(name = "张三"))
        dbRule.db.scanResultDao().insertScanResult(
            TestDataProvider.testScanResult(contactId = contactId, collectionId = collectionId)
        )
        val withCount = dao.getCollectionsWithCount().first()
        val target = withCount.find { it.collection.name == "测试合集" }
        assertThat(target).isNotNull()
        assertThat(target!!.contactCount).isEqualTo(1)
    }

    @Test
    fun getCollectionsWithCount_emptyCollection_returnsZero() = runTest {
        dao.insertCollection(TestDataProvider.testCardCollection(name = "空合集"))
        val withCount = dao.getCollectionsWithCount().first()
        val target = withCount.find { it.collection.name == "空合集" }
        assertThat(target).isNotNull()
        assertThat(target!!.contactCount).isEqualTo(0)
    }

    @Test
    fun getCollectionsWithCount_distinctContactCount() = runTest {
        val collectionId = dao.insertCollection(TestDataProvider.testCardCollection(name = "合集"))
        val contactId = dbRule.db.contactDao().insertContact(TestDataProvider.testContact(name = "张三"))
        dbRule.db.scanResultDao().insertScanResult(
            TestDataProvider.testScanResult(contactId = contactId, collectionId = collectionId, sourceType = "scan")
        )
        dbRule.db.scanResultDao().insertScanResult(
            TestDataProvider.testScanResult(contactId = contactId, collectionId = collectionId, sourceType = "photo")
        )
        val withCount = dao.getCollectionsWithCount().first()
        val target = withCount.find { it.collection.name == "合集" }
        assertThat(target!!.contactCount).isEqualTo(1)
    }

    @Test
    fun insertCollection_replaceOnConflict() = runTest {
        dao.insertCollection(TestDataProvider.testCardCollection(id = 100, name = "原名"))
        dao.insertCollection(TestDataProvider.testCardCollection(id = 100, name = "新名"))
        val collection = dao.getCollectionById(100)
        assertThat(collection!!.name).isEqualTo("新名")
    }

    @Test
    fun deleteCollection_cascadesScanResults() = runTest {
        val collectionId = dao.insertCollection(TestDataProvider.testCardCollection(name = "待删除"))
        val contactId = dbRule.db.contactDao().insertContact(TestDataProvider.testContact(name = "张三"))
        dbRule.db.scanResultDao().insertScanResult(
            TestDataProvider.testScanResult(contactId = contactId, collectionId = collectionId)
        )
        dao.deleteCollection(TestDataProvider.testCardCollection(id = collectionId, name = "待删除"))
        assertThat(dbRule.db.scanResultDao().getScanResultsByContact(contactId).first()).isEmpty()
    }

    @Test
    fun getCollectionById_existing_returnsCollection() = runTest {
        val id = dao.insertCollection(TestDataProvider.testCardCollection(name = "工作"))
        assertThat(dao.getCollectionById(id)).isNotNull()
    }

    @Test
    fun getCollectionById_nonexistent_returnsNull() = runTest {
        assertThat(dao.getCollectionById(999L)).isNull()
    }
}
