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
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.data.ContactFieldDao
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContactFieldDaoTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule(org.robolectric.RuntimeEnvironment.getApplication())

    private lateinit var dao: ContactFieldDao

    @Before
    fun setup() {
        dao = dbRule.db.contactFieldDao()
    }

    @Test
    fun getAllEnabledFields_excludesDisabled() = runTest {
        dao.insertField(ContactField(id = 100, fieldName = "已禁用", fieldKey = "disabled", isEnabled = false))
        val enabled = dao.getAllEnabledFields().first()
        assertThat(enabled.none { it.fieldKey == "disabled" }).isTrue()
    }

    @Test
    fun getAllFields_includesDisabled() = runTest {
        dao.insertField(ContactField(id = 100, fieldName = "已禁用", fieldKey = "disabled", isEnabled = false))
        val all = dao.getAllFields().first()
        assertThat(all.any { it.fieldKey == "disabled" }).isTrue()
    }

    @Test
    fun getFieldByKey_returnsCorrectField() = runTest {
        val field = dao.getFieldByKey("phone")
        assertThat(field).isNotNull()
        assertThat(field!!.fieldKey).isEqualTo("phone")
    }

    @Test
    fun getFieldByKey_nonexistent_returnsNull() = runTest {
        assertThat(dao.getFieldByKey("nonexistent")).isNull()
    }

    @Test
    fun insertField_replaceOnConflict() = runTest {
        dao.insertField(ContactField(id = 100, fieldName = "原字段", fieldKey = "custom1"))
        dao.insertField(ContactField(id = 100, fieldName = "替换字段", fieldKey = "custom1"))
        val field = dao.getFieldById(100)
        assertThat(field!!.fieldName).isEqualTo("替换字段")
    }

    @Test
    fun deleteField_removesField() = runTest {
        dao.insertField(ContactField(id = 100, fieldName = "待删除", fieldKey = "todelete"))
        dao.deleteField(ContactField(id = 100, fieldName = "待删除", fieldKey = "todelete"))
        assertThat(dao.getFieldByKey("todelete")).isNull()
    }

    @Test
    fun setFieldEnabled_updatesEnabledState() = runTest {
        dao.insertField(ContactField(id = 100, fieldName = "测试", fieldKey = "custom1", isEnabled = true))
        dao.setFieldEnabled(100, false)
        val field = dao.getFieldById(100)
        assertThat(field!!.isEnabled).isFalse()
    }

    @Test
    fun updateFieldOrder_changesOrder() = runTest {
        dao.insertField(ContactField(id = 100, fieldName = "测试", fieldKey = "custom1", sortOrder = 0))
        dao.updateFieldOrder(100, 5)
        val field = dao.getFieldById(100)
        assertThat(field!!.sortOrder).isEqualTo(5)
    }

    @Test
    fun getFieldsByIds_batchQuery() = runTest {
        val fields = dao.getFieldsByIds(listOf(1L, 2L, 3L))
        assertThat(fields).hasSize(3)
    }
}
