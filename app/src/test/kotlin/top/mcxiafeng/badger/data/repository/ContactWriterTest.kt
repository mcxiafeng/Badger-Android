package top.mcxiafeng.badger.data.repository

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.model.FieldMergeEntry
import top.mcxiafeng.badger.data.model.MergeChoice
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldCacheEntity
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.ocr.ExtractedContactInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContactWriterTest {
    private lateinit var database: AppDatabase
    private lateinit var serverApi: ServerApi
    private lateinit var writer: ContactWriter

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        serverApi = mockk(relaxed = true)
        writer = ContactWriter(database, serverApi)
        seedPhoneAndCollection()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveScanned_writesContactFieldsPlatformsAndMember(): Unit = runBlocking {
        val now = System.currentTimeMillis()
        val result = writer.saveScanned(
            contact = ContactCacheEntity(name = "张三", createTime = now, updateTime = now),
            info = ExtractedContactInfo(
                name = "张三",
                phone = "13800001111",
                platforms = mapOf("qq" to "12345"),
            ),
            sourceType = "scan",
        )

        val written = result as CommitResult.Written
        val contact = database.contactCacheDao().getContactById(written.contactId)!!
        assertThat(contact.name).isEqualTo("张三")
        assertThat(contact.isLocalOnly).isTrue()
        assertThat(contact.serverId).isNotEmpty()
        val values = database.contactFieldValueCacheDao().getFieldValuesByContactOnce(written.contactId)
        assertThat(values.map { it.value }).contains("13800001111")
        val platforms = database.contactPlatformCacheDao().getPlatformsByContact(written.contactId)
        assertThat(platforms.map { it.platformKey }).contains("qq")
        assertThat(database.collectionMemberCacheDao().exists(written.contactId, 1L)).isTrue()
        verify { serverApi.enqueueCreatePerson(written.contactId, "张三", any(), contact.serverId!!) }
    }

    @Test
    fun saveScanned_stripsNumericFieldSuffix_intoQqPlatform(): Unit = runBlocking {
        val now = System.currentTimeMillis()
        val result = writer.saveScanned(
            contact = ContactCacheEntity(name = "李四", createTime = now, updateTime = now),
            info = ExtractedContactInfo(
                name = "李四",
                platforms = mapOf("qq_1" to "999"),
            ),
            sourceType = "scan",
        )
        val written = result as CommitResult.Written
        val platforms = database.contactPlatformCacheDao().getPlatformsByContact(written.contactId)
        assertThat(platforms.single().platformKey).isEqualTo("qq")
        assertThat(platforms.single().value).isEqualTo("999")
    }

    @Test
    fun mergeScanned_keepReplaceAppend(): Unit = runBlocking {
        val now = System.currentTimeMillis()
        val saved = writer.saveScanned(
            contact = ContactCacheEntity(name = "王五", createTime = now, updateTime = now),
            info = ExtractedContactInfo(name = "王五", phone = "111"),
            sourceType = "scan",
        ) as CommitResult.Written
        val phoneFieldId = database.contactFieldCacheDao().getFieldByKey("phone")!!.id
        val existingValue = database.contactFieldValueCacheDao()
            .getFieldValuesByContactOnce(saved.contactId)
            .single { it.fieldId == phoneFieldId }

        val result = writer.mergeScanned(
            existingContactId = saved.contactId,
            newInfo = ExtractedContactInfo(name = "王五新", phone = "222"),
            mergeEntries = listOf(
                FieldMergeEntry(
                    fieldKey = "phone",
                    fieldName = "电话",
                    existingValue = "111",
                    newValue = "222",
                    selectedValue = MergeChoice.REPLACE,
                ),
            ),
            collectionId = 1L,
            sourceType = "scan",
            chosenName = "王五新",
        )
        assertThat(result).isInstanceOf(CommitResult.Written::class.java)
        val updated = database.contactCacheDao().getContactById(saved.contactId)!!
        assertThat(updated.name).isEqualTo("王五新")
        val phoneRows = database.contactFieldValueCacheDao()
            .getFieldValuesByContactOnce(saved.contactId)
            .filter { it.fieldId == phoneFieldId }
        assertThat(phoneRows.single { it.id == existingValue.id }.value).isEqualTo("222")
    }

    @Test
    fun attachScanned_skipsDuplicatePhone_addsNew(): Unit = runBlocking {
        val now = System.currentTimeMillis()
        val saved = writer.saveScanned(
            contact = ContactCacheEntity(name = "赵六", createTime = now, updateTime = now),
            info = ExtractedContactInfo(name = "赵六", phone = "111"),
            sourceType = "scan",
        ) as CommitResult.Written

        writer.attachScanned(
            existingContactId = saved.contactId,
            info = ExtractedContactInfo(phone = "111"),
            selectedFields = listOf("phone"),
            collectionId = 1L,
        )
        val phoneFieldId = database.contactFieldCacheDao().getFieldByKey("phone")!!.id
        val afterSame = database.contactFieldValueCacheDao()
            .getFieldValuesByContactOnce(saved.contactId)
            .filter { it.fieldId == phoneFieldId }
        assertThat(afterSame).hasSize(1)

        writer.attachScanned(
            existingContactId = saved.contactId,
            info = ExtractedContactInfo(phone = "333"),
            selectedFields = listOf("phone"),
            collectionId = 1L,
        )
        val afterNew = database.contactFieldValueCacheDao()
            .getFieldValuesByContactOnce(saved.contactId)
            .filter { it.fieldId == phoneFieldId }
        assertThat(afterNew.map { it.value }).containsExactly("111", "333")
    }

    @Test
    fun mergeScanned_missingContact_returnsNotFound(): Unit = runBlocking {
        val result = writer.mergeScanned(
            existingContactId = 9999L,
            newInfo = ExtractedContactInfo(name = "幽灵"),
            mergeEntries = emptyList(),
            collectionId = 1L,
            sourceType = "scan",
        )
        assertThat(result).isEqualTo(CommitResult.NotFound)
        assertThat(database.contactCacheDao().getContactById(9999L)).isNull()
    }

    @Test
    fun stripFieldKeySuffix_qq1_becomesQq() {
        assertThat(ContactWriter.stripFieldKeySuffix("qq_1")).isEqualTo("qq")
        assertThat(ContactWriter.stripFieldKeySuffix("phone")).isEqualTo("phone")
        assertThat(ContactWriter.stripFieldKeySuffix("foo_bar")).isEqualTo("foo_bar")
    }

    private suspend fun seedPhoneAndCollection() {
        val now = System.currentTimeMillis()
        database.contactFieldCacheDao().insertField(
            ContactFieldCacheEntity(
                fieldName = "电话",
                fieldKey = "phone",
                isSystem = true,
                isEnabled = true,
                createTime = now,
            ),
        )
        database.cardCollectionCacheDao().insertCollection(
            CardCollectionCacheEntity(
                id = 1L,
                name = "默认名片夹",
                createTime = now,
                isLocalOnly = true,
            ),
        )
    }
}
