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
    private lateinit var collectionDao: CardCollectionDao
    private lateinit var userProfileDao: UserProfileDao
    private lateinit var repository: ContactRepositoryImpl

    @Before
    fun setup() {
        contactDao = mockk(relaxed = true)
        contactFieldDao = mockk(relaxed = true)
        customFieldDao = mockk(relaxed = true)
        contactFieldValueDao = mockk(relaxed = true)
        scanResultDao = mockk(relaxed = true)
        collectionDao = mockk(relaxed = true)
        userProfileDao = mockk(relaxed = true)
        repository = ContactRepositoryImpl(
            contactDao, contactFieldDao, customFieldDao,
            contactFieldValueDao, scanResultDao, collectionDao, userProfileDao
        )
    }

    // ========== deleteField system field guard ==========

    @Test
    fun deleteField_systemField_isIgnored() = runTest {
        val systemField = TestDataProvider.testContactField(id = 1, fieldKey = "phone", isSystem = true)
        repository.deleteField(systemField)
        coVerify(exactly = 0) { contactFieldDao.deleteField(any()) }
    }

    @Test
    fun deleteField_nonSystemField_callsDaoDelete() = runTest {
        val customField = TestDataProvider.testContactField(id = 100, fieldKey = "custom1", isSystem = false)
        repository.deleteField(customField)
        coVerify { contactFieldDao.deleteField(customField) }
    }

    // ========== saveContactFieldValues map transformation ==========

    @Test
    fun saveContactFieldValues_transformsMapToList() = runTest {
        val fieldValues = mapOf(1L to "13800138000", 2L to "test@example.com")
        repository.saveContactFieldValues(10L, fieldValues)
        coVerify {
            contactFieldValueDao.insertOrUpdateFieldValues(match { values ->
                values.size == 2 &&
                    values.any { it.fieldId == 1L && it.value == "13800138000" && it.contactId == 10L } &&
                    values.any { it.fieldId == 2L && it.value == "test@example.com" && it.contactId == 10L }
            })
        }
    }

    @Test
    fun saveContactCustomFieldValues_transformsMapToList() = runTest {
        val fieldValues = mapOf(5L to "Google")
        repository.saveContactCustomFieldValues(10L, fieldValues)
        coVerify {
            contactFieldValueDao.insertOrUpdateFieldValues(match { values ->
                values.size == 1 &&
                    values[0].customFieldId == 5L &&
                    values[0].fieldId == null &&
                    values[0].value == "Google" &&
                    values[0].contactId == 10L
            })
        }
    }

    // ========== getFieldValueByContactAndKey two-step lookup ==========

    @Test
    fun getFieldValueByContactAndKey_resolvesFieldKeyThenQueries() = runTest {
        val field = TestDataProvider.testContactField(id = 1, fieldKey = "phone")
        coEvery { contactFieldDao.getFieldByKey("phone") } returns field
        coEvery { contactFieldValueDao.getFieldValue(10L, 1L) } returns "13800138000"

        val result = repository.getFieldValueByContactAndKey(10L, "phone")
        assertThat(result).isEqualTo("13800138000")
        coVerify { contactFieldDao.getFieldByKey("phone") }
        coVerify { contactFieldValueDao.getFieldValue(10L, 1L) }
    }

    @Test
    fun getFieldValueByContactAndKey_unknownKey_returnsNull() = runTest {
        coEvery { contactFieldDao.getFieldByKey("unknown") } returns null
        val result = repository.getFieldValueByContactAndKey(10L, "unknown")
        assertThat(result).isNull()
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

    // ========== getUserProfileOnce ==========

    @Test
    fun getUserProfileOnce_filtersBlankJumpLink() = runTest {
        val profile = UserProfile(
            id = 1L, name = "测试",
            platforms = mapOf(
                "qq" to PlatformEntry(jumpLink = "https://qq.com/123", value = "123"),
                "wechat" to PlatformEntry(jumpLink = "", value = "wxid")
            )
        )
        coEvery { userProfileDao.getProfileOnce() } returns profile
        val result = repository.getUserProfileOnce()
        assertThat(result).isNotNull()
        assertThat(result!!.platforms).hasSize(2)
    }

    @Test
    fun getUserProfileOnce_nullProfile_returnsNull() = runTest {
        coEvery { userProfileDao.getProfileOnce() } returns null
        val result = repository.getUserProfileOnce()
        assertThat(result).isNull()
    }

    // ========== updatePlatformField ==========

    @Test
    fun updatePlatformField_createsProfileWhenNoneExists() = runTest {
        coEvery { userProfileDao.getProfileOnce() } returns null
        coEvery { userProfileDao.saveProfile(any()) } returns Unit
        repository.updatePlatformField("qq", "https://qq.com/123", "123")
        coVerify { userProfileDao.saveProfile(match { it.name == "用户" && it.platforms?.containsKey("qq") == true }) }
    }

    @Test
    fun updatePlatformField_blankJumpLink_removesPlatform() = runTest {
        val existing = UserProfile(
            id = 1L, name = "测试",
            platforms = mapOf("qq" to PlatformEntry(jumpLink = "https://qq.com/123", value = "123"))
        )
        coEvery { userProfileDao.getProfileOnce() } returns existing
        coEvery { userProfileDao.saveProfile(any()) } returns Unit
        repository.updatePlatformField("qq", "", null)
        coVerify { userProfileDao.saveProfile(match { it.platforms?.containsKey("qq") == false }) }
    }

    // ========== removePlatform ==========

    @Test
    fun removePlatform_noProfile_isNoOp() = runTest {
        coEvery { userProfileDao.getProfileOnce() } returns null
        repository.removePlatform("qq")
        coVerify(exactly = 0) { userProfileDao.saveProfile(any()) }
    }

    @Test
    fun removePlatform_existingPlatform_removesFromMap() = runTest {
        val existing = UserProfile(
            id = 1L, name = "测试",
            platforms = mapOf("qq" to PlatformEntry(jumpLink = "https://qq.com/123", value = "123"))
        )
        coEvery { userProfileDao.getProfileOnce() } returns existing
        coEvery { userProfileDao.saveProfile(any()) } returns Unit
        repository.removePlatform("qq")
        coVerify { userProfileDao.saveProfile(match { it.platforms?.containsKey("qq") == false }) }
    }

    // ========== addContactToCollection ==========

    @Test
    fun addContactToCollection_createsScanResult() = runTest {
        repository.addContactToCollection(1L, 2L, "scan", qrCodeContent = "test")
        coVerify { scanResultDao.insertScanResult(match {
            it.contactId == 1L && it.collectionId == 2L && it.sourceType == "scan" && it.qrCodeContent == "test"
        }) }
    }

    // ========== getFieldValueMapByContact ==========

    @Test
    fun getFieldValueMapByContact_generatesCustomKey() = runTest {
        val customFieldId = 10L
        coEvery { contactFieldValueDao.getFieldValuesByContactOnce(1L) } returns listOf(
            TestDataProvider.testFieldValue(fieldId = 1L, value = "13800138000"),
            TestDataProvider.testFieldValue(customFieldId = customFieldId, value = "Google")
        )
        coEvery { contactFieldDao.getFieldById(1L) } returns TestDataProvider.testContactField(id = 1, fieldKey = "phone")
        val map = repository.getFieldValueMapByContact(1L)
        assertThat(map["phone"]).isEqualTo("13800138000")
        assertThat(map["custom_10"]).isEqualTo("Google")
    }

    @Test
    fun getFieldValueMapByContact_firstValueWins() = runTest {
        coEvery { contactFieldValueDao.getFieldValuesByContactOnce(1L) } returns listOf(
            TestDataProvider.testFieldValue(fieldId = 1L, value = "first"),
            TestDataProvider.testFieldValue(fieldId = 1L, value = "second")
        )
        coEvery { contactFieldDao.getFieldById(1L) } returns TestDataProvider.testContactField(id = 1, fieldKey = "phone")
        val map = repository.getFieldValueMapByContact(1L)
        assertThat(map["phone"]).isEqualTo("first")
        assertThat(map).hasSize(1)
    }

    // ========== checkDuplicate ==========

    @Test
    fun checkDuplicate_emptyFieldValues_returnsNotDuplicate() = runTest {
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