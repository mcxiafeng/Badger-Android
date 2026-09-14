package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContactCacheDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ContactCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.contactCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun contact(id: Long, name: String, isDeleted: Boolean = false) = ContactCacheEntity(
        id = id,
        serverId = "srv-$id",
        name = name,
        createTime = 1L,
        updateTime = 1L,
        isDeleted = isDeleted,
    )

    @Test
    fun getContactsByName_excludesSoftDeleted(): Unit = runBlocking {
        dao.insertContact(contact(1, "张三"))
        dao.insertContact(contact(2, "张三", isDeleted = true))

        val rows = dao.getContactsByName("张三")

        assertThat(rows.map { it.id }).containsExactly(1L)
    }

    @Test
    fun getContactsByName_caseInsensitiveStillWorks(): Unit = runBlocking {
        dao.insertContact(contact(1, "zhangsan"))

        val rows = dao.getContactsByName("ZHANGSAN")

        assertThat(rows).hasSize(1)
    }

    @Test
    fun searchContactsByName_excludesSoftDeleted(): Unit = runBlocking {
        dao.insertContact(contact(1, "张三丰"))
        dao.insertContact(contact(2, "张三", isDeleted = true))

        val rows = dao.searchContactsByName("张").first()

        assertThat(rows.map { it.id }).containsExactly(1L)
    }
}
