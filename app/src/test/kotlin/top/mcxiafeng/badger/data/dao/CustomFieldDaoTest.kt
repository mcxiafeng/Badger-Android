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
import top.mcxiafeng.badger.data.CustomField
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule
import top.mcxiafeng.badger.testutil.TestDataProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomFieldDaoTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule(org.robolectric.RuntimeEnvironment.getApplication())

    private lateinit var dao: CustomFieldDao

    @Before
    fun setup() {
        dao = dbRule.db.customFieldDao()
    }

    @Test
    fun getAllEnabledCustomFields_excludesDisabled() = runTest {
        dao.insertCustomField(TestDataProvider.testCustomField(fieldName = "已禁用", isEnabled = false))
        val enabled = dao.getAllEnabledCustomFields().first()
        assertThat(enabled.none { it.fieldName == "已禁用" }).isTrue()
    }

    @Test
    fun getCustomFieldById_returnsCorrect() = runTest {
        val id = dao.insertCustomField(TestDataProvider.testCustomField(fieldName = "公司"))
        val field = dao.getCustomFieldById(id)
        assertThat(field).isNotNull()
        assertThat(field!!.fieldName).isEqualTo("公司")
    }

    @Test
    fun insertCustomField_returnsId() = runTest {
        val id = dao.insertCustomField(TestDataProvider.testCustomField(fieldName = "测试"))
        assertThat(id).isGreaterThan(0)
    }

    @Test
    fun deleteCustomField_removesField_cascadesValues() = runTest {
        val customFieldId = dao.insertCustomField(TestDataProvider.testCustomField(fieldName = "公司"))
        val contactId = dbRule.db.contactDao().insertContact(TestDataProvider.testContact(name = "张三"))
        dbRule.db.contactFieldValueDao().insertFieldValue(
            TestDataProvider.testFieldValue(contactId = contactId, customFieldId = customFieldId, value = "Google")
        )
        dao.deleteCustomField(TestDataProvider.testCustomField(id = customFieldId, fieldName = "公司"))
        assertThat(dao.getCustomFieldById(customFieldId)).isNull()
        val values = dbRule.db.contactFieldValueDao().getFieldValuesByContactOnce(contactId)
        assertThat(values.none { it.customFieldId == customFieldId }).isTrue()
    }

    @Test
    fun setCustomFieldEnabled_updatesState() = runTest {
        val id = dao.insertCustomField(TestDataProvider.testCustomField(fieldName = "公司", isEnabled = true))
        dao.setCustomFieldEnabled(id, false)
        val field = dao.getCustomFieldById(id)
        assertThat(field!!.isEnabled).isFalse()
    }

    @Test
    fun updateCustomFieldOrder_changesOrder() = runTest {
        val id = dao.insertCustomField(TestDataProvider.testCustomField(fieldName = "公司", sortOrder = 0))
        dao.updateCustomFieldOrder(id, 10)
        val field = dao.getCustomFieldById(id)
        assertThat(field!!.sortOrder).isEqualTo(10)
    }

    @Test
    fun getCustomFieldsByIds_batchQuery() = runTest {
        val id1 = dao.insertCustomField(TestDataProvider.testCustomField(fieldName = "公司"))
        val id2 = dao.insertCustomField(TestDataProvider.testCustomField(fieldName = "职位"))
        val fields = dao.getCustomFieldsByIds(listOf(id1, id2))
        assertThat(fields).hasSize(2)
    }
}
