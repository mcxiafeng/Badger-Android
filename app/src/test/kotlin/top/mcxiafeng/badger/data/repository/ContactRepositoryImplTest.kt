package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.testutil.TestDataProvider

class ContactRepositoryImplTest {

    private lateinit var contactDao: ContactDao
    private lateinit var contactFieldDao: ContactFieldDao
    private lateinit var customFieldDao: CustomFieldDao
    private lateinit var contactFieldValueDao: ContactFieldValueDao
    private lateinit var scanResultDao: ScanResultDao
    private lateinit var contactPlatformDao: ContactPlatformDao
    private lateinit var contactFtsDao: ContactFtsDao
    private lateinit var repository: ContactRepositoryImpl

    @Before
    fun setup() {
        contactDao = mockk(relaxed = true)
        contactFieldDao = mockk(relaxed = true)
        customFieldDao = mockk(relaxed = true)
        contactFieldValueDao = mockk(relaxed = true)
        scanResultDao = mockk(relaxed = true)
        contactPlatformDao = mockk(relaxed = true)
        contactFtsDao = mockk(relaxed = true)
        repository = ContactRepositoryImpl(
            contactDao, contactFieldDao, customFieldDao,
            contactFieldValueDao, scanResultDao, contactPlatformDao, contactFtsDao
        )
    }

    // ========== calculateNameSimilarity (via reflection) ==========

    @Test
    fun calculateNameSimilarity_identicalNames_returns1() {
        val method = ContactRepositoryImpl::class.java.getDeclaredMethod(
            "calculateNameSimilarity", String::class.java, String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(repository, "张三", "张三") as Float
        assertThat(result).isEqualTo(1.0f)
    }

    @Test
    fun calculateNameSimilarity_completelyDifferent_returns0() {
        val method = ContactRepositoryImpl::class.java.getDeclaredMethod(
            "calculateNameSimilarity", String::class.java, String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(repository, "ABC", "XYZ") as Float
        assertThat(result).isEqualTo(0.0f)
    }

    @Test
    fun calculateNameSimilarity_partialOverlap_returnsBetween0And1() {
        val method = ContactRepositoryImpl::class.java.getDeclaredMethod(
            "calculateNameSimilarity", String::class.java, String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(repository, "AB", "BC") as Float
        assertThat(result).isGreaterThan(0f)
        assertThat(result).isLessThan(1f)
    }

    @Test
    fun calculateNameSimilarity_caseInsensitive_returns1() {
        val method = ContactRepositoryImpl::class.java.getDeclaredMethod(
            "calculateNameSimilarity", String::class.java, String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(repository, "test", "TEST") as Float
        assertThat(result).isEqualTo(1.0f)
    }

    // ========== checkDuplicate ==========

    @Test
    fun checkDuplicate_emptyFieldValues_returnsNotDuplicate() = runTest {
        coEvery { contactDao.getContactsByName(any()) } returns emptyList()
        every { contactDao.searchContactsByName(any()) } returns flowOf(emptyList())
        val result = repository.checkDuplicate("张三", emptyMap(), emptyMap())
        assertThat(result.isDuplicate).isFalse()
        assertThat(result.similarityScore).isEqualTo(0f)
    }

    // ========== getContactWithFieldsById ==========

    @Test
    fun getContactWithFieldsById_filtersDisabledFields() = runTest {
        val contact = TestDataProvider.testContact(id = 1, name = "张三")
        coEvery { contactDao.getContactById(1L) } returns contact
        coEvery { contactFieldValueDao.getFieldValuesByContactOnce(1L) } returns listOf(
            TestDataProvider.testFieldValue(fieldId = 1L, value = "13800138000"),
            TestDataProvider.testFieldValue(fieldId = 100L, value = "disabled_value")
        )
        coEvery { contactFieldDao.getFieldsByIds(listOf(1L, 100L)) } returns listOf(
            TestDataProvider.testContactField(id = 1, fieldKey = "phone", isEnabled = true),
            TestDataProvider.testContactField(id = 100, fieldKey = "disabled_field", isEnabled = false)
        )
        val result = repository.getContactWithFieldsById(1L)
        assertThat(result).isNotNull()
        assertThat(result!!.fieldValues).hasSize(1)
        assertThat(result.fieldValues[0].fieldKey).isEqualTo("phone")
    }

    // ========== getAllContactsWithFields ==========

    @Test
    fun getAllContactsWithFields_returnsEmptyFieldValues() = runTest {
        every { contactDao.getAllContacts() } returns flowOf(listOf(TestDataProvider.testContact(name = "张三")))
        val result = repository.getAllContactsWithFields().first()
        assertThat(result).hasSize(1)
        assertThat(result[0].fieldValues).isEmpty()
    }
}