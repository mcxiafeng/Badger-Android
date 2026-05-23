package top.mcxiafeng.badger.utils

import com.google.common.truth.Truth.assertThat
import com.google.gson.GsonBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import top.mcxiafeng.badger.data.*

class CollectionExporterTest {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Test
    fun previewImport_validJson_returnsCollectionAndContactCount() {
        val json = gson.toJson(BadgerExport(
            collections = listOf(
                CollectionExport(
                    name = "工作",
                    contacts = listOf(
                        ContactExport(name = "张三", fields = listOf(FieldExport("phone", "13800138000"))),
                        ContactExport(name = "李四", fields = emptyList())
                    )
                ),
                CollectionExport(
                    name = "朋友",
                    contacts = listOf(
                        ContactExport(name = "王五", fields = emptyList())
                    )
                )
            )
        ))
        val (collectionCount, contactCount) = previewImport(json)
        assertThat(collectionCount).isEqualTo(2)
        assertThat(contactCount).isEqualTo(3)
    }

    @Test
    fun previewImport_invalidJson_returnsZeroZero() {
        val (collections, contacts) = previewImport("not valid json{{{")
        assertThat(collections).isEqualTo(0)
        assertThat(contacts).isEqualTo(0)
    }

    @Test
    fun previewImport_emptyCollections_returnsCounts() {
        val json = gson.toJson(BadgerExport(collections = emptyList()))
        val (collections, contacts) = previewImport(json)
        assertThat(collections).isEqualTo(0)
        assertThat(contacts).isEqualTo(0)
    }

    @Test
    fun importFromJson_validJson_createsCollectionsAndContacts() = runTest {
        val repository = mockk<ContactRepository>(relaxed = true)
        val phoneField = ContactField(id = 1, fieldName = "手机", fieldKey = "phone", isSystem = true)
        coEvery { repository.getAllFieldsOnce() } returns listOf(phoneField)
        coEvery { repository.getAllCollectionsOnce() } returns emptyList()
        coEvery { repository.insertCollection(any()) } returns 1L
        coEvery { repository.insertContact(any()) } returns 1L

        val json = gson.toJson(BadgerExport(
            collections = listOf(
                CollectionExport(
                    name = "工作",
                    contacts = listOf(
                        ContactExport(name = "张三", fields = listOf(FieldExport("phone", "13800138000")))
                    )
                )
            )
        ))

        val result = importFromJson(repository, json)
        assertThat(result.importedCollections).isEqualTo(1)
        assertThat(result.importedContacts).isEqualTo(1)
        coVerify { repository.insertCollection(match { it.name == "工作" }) }
        coVerify { repository.insertContact(match { it.name == "张三" }) }
        coVerify { repository.insertFieldValue(match { it.value == "13800138000" && it.fieldId == 1L }) }
        coVerify { repository.addContactToCollection(any(), any(), "import") }
    }

    @Test
    fun importFromJson_duplicateCollectionName_skips() = runTest {
        val repository = mockk<ContactRepository>(relaxed = true)
        coEvery { repository.getAllFieldsOnce() } returns emptyList()
        coEvery { repository.getAllCollectionsOnce() } returns listOf(CardCollection(id = 1, name = "工作"))

        val json = gson.toJson(BadgerExport(
            collections = listOf(
                CollectionExport(name = "工作", contacts = emptyList())
            )
        ))

        val result = importFromJson(repository, json)
        assertThat(result.skippedCollections).isEqualTo(1)
        assertThat(result.importedCollections).isEqualTo(0)
    }

    @Test
    fun importFromJson_invalidJson_throwsException() = runTest {
        val repository = mockk<ContactRepository>(relaxed = true)
        try {
            importFromJson(repository, "not valid json{{{")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("无效的 JSON 格式")
        }
    }

    @Test
    fun importFromJson_unsupportedVersion_throwsException() = runTest {
        val repository = mockk<ContactRepository>(relaxed = true)
        val json = """{"version":2,"app":"badger","exportTime":0,"collections":[]}"""
        try {
            importFromJson(repository, json)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("不支持的版本")
        }
    }

    @Test
    fun importContactsToCollection_createsContactsInExistingCollection() = runTest {
        val repository = mockk<ContactRepository>(relaxed = true)
        val phoneField = ContactField(id = 1, fieldName = "手机", fieldKey = "phone", isSystem = true)
        coEvery { repository.getAllFieldsOnce() } returns listOf(phoneField)
        coEvery { repository.insertContact(any()) } returns 10L

        val json = gson.toJson(BadgerExport(
            collections = listOf(
                CollectionExport(
                    name = "导入",
                    contacts = listOf(
                        ContactExport(name = "张三", fields = listOf(FieldExport("phone", "13800138000"))),
                        ContactExport(name = "李四", fields = emptyList())
                    )
                )
            )
        ))

        val count = importContactsToCollection(repository, 5L, json)
        assertThat(count).isEqualTo(2)
        coVerify { repository.addContactToCollection(any(), 5L, "import") }
    }

    @Test
    fun importContactsToCollection_unsupportedVersion_throwsException() = runTest {
        val repository = mockk<ContactRepository>(relaxed = true)
        val json = """{"version":99,"app":"badger","exportTime":0,"collections":[]}"""
        try {
            importContactsToCollection(repository, 1L, json)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("不支持的版本")
        }
    }

    @Test
    fun gsonRoundTrip_preservesData() {
        val original = BadgerExport(
            collections = listOf(
                CollectionExport(
                    name = "工作",
                    description = "工作联系人",
                    contacts = listOf(
                        ContactExport(
                            name = "张三",
                            avatarUrl = "https://example.com/avatar.jpg",
                            note = "测试",
                            isFavorite = true,
                            fields = listOf(
                                FieldExport("phone", "13800138000"),
                                FieldExport("qq", "123456")
                            )
                        )
                    )
                )
            )
        )
        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, BadgerExport::class.java)
        assertThat(deserialized).isEqualTo(original)
    }
}
