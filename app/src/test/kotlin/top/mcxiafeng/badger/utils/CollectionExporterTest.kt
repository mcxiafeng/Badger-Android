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
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import kotlinx.coroutines.flow.flowOf

class CollectionExporterTest {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Test
    fun previewImport_validJson_returnsCollectionAndContactCount() {
        val json = gson.toJson(BadgerExport(
            version = 2,
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
        val json = gson.toJson(BadgerExport(version = 2, collections = emptyList()))
        val (collections, contacts) = previewImport(json)
        assertThat(collections).isEqualTo(0)
        assertThat(contacts).isEqualTo(0)
    }

    @Test
    fun importFromJson_validJson_createsCollectionsAndContacts() = runTest {
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val fieldRepository = mockk<FieldRepository>(relaxed = true)
        val collectionRepository = mockk<CollectionRepository>(relaxed = true)
        val tagRepository = mockk<TagRepository>(relaxed = true)
        val phoneField = ContactField(id = 1, fieldName = "手机", fieldKey = "phone", isSystem = true)
        coEvery { fieldRepository.getAllFieldsOnce() } returns listOf(phoneField)
        coEvery { collectionRepository.getAllCollectionsOnce() } returns emptyList()
        coEvery { collectionRepository.insertCollection(any()) } returns 1L
        coEvery { contactRepository.insertContact(any()) } returns 1L
        every { contactRepository.getAllContacts() } returns flowOf(emptyList())

        val json = gson.toJson(BadgerExport(
            version = 2,
            collections = listOf(
                CollectionExport(
                    name = "工作",
                    contacts = listOf(
                        ContactExport(name = "张三", fields = listOf(FieldExport("phone", "13800138000")))
                    )
                )
            )
        ))

        val result = importFromJson(contactRepository, fieldRepository, collectionRepository, tagRepository, json)
        assertThat(result.importedCollections).isEqualTo(1)
        assertThat(result.importedContacts).isEqualTo(1)
        coVerify { collectionRepository.insertCollection(match { it.name == "工作" }) }
        coVerify { contactRepository.insertContact(match { it.name == "张三" }) }
        coVerify { fieldRepository.insertFieldValue(match { it.value == "13800138000" && it.fieldId == 1L }) }
        coVerify { collectionRepository.addContactToCollection(any(), any(), "import") }
    }

    @Test
    fun importFromJson_v2WithTags_restoresTags() = runTest {
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val fieldRepository = mockk<FieldRepository>(relaxed = true)
        val collectionRepository = mockk<CollectionRepository>(relaxed = true)
        val tagRepository = mockk<TagRepository>(relaxed = true)
        coEvery { fieldRepository.getAllFieldsOnce() } returns emptyList()
        coEvery { collectionRepository.getAllCollectionsOnce() } returns emptyList()
        coEvery { collectionRepository.insertCollection(any()) } returns 1L
        coEvery { contactRepository.insertContact(any()) } returns 7L
        every { contactRepository.getAllContacts() } returns flowOf(emptyList())
        // upsertTag 同名返回 100
        coEvery { tagRepository.upsertTag(any(), any(), any()) } returns 100L
        coEvery { tagRepository.getAllTagsOnce() } returns emptyList()
        coEvery { tagRepository.addTagToContact(any(), any()) } returns Unit
        coEvery { tagRepository.addTagsToContact(any(), any()) } returns Unit

        val json = gson.toJson(BadgerExport(
            version = 2,
            collections = listOf(
                CollectionExport(
                    name = "工作",
                    contacts = listOf(
                        ContactExport(
                            name = "张三",
                            fields = emptyList(),
                            tags = listOf(
                                TagExport(name = "同事", color = 0xFF1976D2L),
                                TagExport(name = "VIP", color = 0xFFFF5722L)
                            )
                        )
                    )
                )
            )
        ))

        val result = importFromJson(contactRepository, fieldRepository, collectionRepository, tagRepository, json)
        assertThat(result.importedContacts).isEqualTo(1)
        // 批量 applyImportedTags 走批量事务路径(每条 tagExport 同名复用 + insertCrossRefs);
        // 第三个参数 now 是 System.currentTimeMillis(),用 any() 跳过时间戳比较。
        coVerify { tagRepository.applyImportedTags(7L, match { it.size == 2 }, any()) }
    }

    @Test
    fun importFromJson_duplicateCollectionName_importsIntoExisting() = runTest {
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val fieldRepository = mockk<FieldRepository>(relaxed = true)
        val collectionRepository = mockk<CollectionRepository>(relaxed = true)
        val tagRepository = mockk<TagRepository>(relaxed = true)
        coEvery { fieldRepository.getAllFieldsOnce() } returns emptyList()
        coEvery { collectionRepository.getAllCollectionsOnce() } returns listOf(CardCollection(id = 1, name = "工作"))
        coEvery { contactRepository.insertContact(any()) } returns 10L
        every { contactRepository.getAllContacts() } returns flowOf(emptyList())

        val json = gson.toJson(BadgerExport(
            version = 2,
            collections = listOf(
                CollectionExport(name = "工作", contacts = listOf(
                    ContactExport(name = "张三", fields = emptyList())
                ))
            )
        ))

        val result = importFromJson(contactRepository, fieldRepository, collectionRepository, tagRepository, json)
        assertThat(result.importedCollections).isEqualTo(1)
        assertThat(result.importedContacts).isEqualTo(1)
        // Should NOT create a new collection, but use existing id=1
        coVerify(exactly = 0) { collectionRepository.insertCollection(any()) }
        coVerify { collectionRepository.addContactToCollection(10L, 1L, "import") }
    }

    @Test
    fun importFromJson_duplicateContact_mergesIntoExisting() = runTest {
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val fieldRepository = mockk<FieldRepository>(relaxed = true)
        val collectionRepository = mockk<CollectionRepository>(relaxed = true)
        val tagRepository = mockk<TagRepository>(relaxed = true)
        coEvery { fieldRepository.getAllFieldsOnce() } returns emptyList()
        coEvery { collectionRepository.getAllCollectionsOnce() } returns listOf(CardCollection(id = 1, name = "工作"))
        val existingContact = Contact(id = 99, name = "张三", platforms = mapOf(
            "qq" to PlatformEntry(jumpLink = "https://qq.com/123456", value = "123456")
        ))
        every { contactRepository.getAllContacts() } returns flowOf(listOf(existingContact))
        coEvery { contactRepository.getContactById(99L) } returns existingContact
        coEvery { fieldRepository.getFieldValueMapByContact(99L) } returns emptyMap()
        coEvery { tagRepository.getAllTagsOnce() } returns emptyList()

        val json = gson.toJson(BadgerExport(
            version = 2,
            collections = listOf(
                CollectionExport(name = "工作", contacts = listOf(
                    ContactExport(
                        name = "张三",
                        fields = emptyList(),
                        platforms = mapOf("qq" to PlatformEntryExport(value = "123456", jumpLink = "https://qq.com/123456"))
                    )
                ))
            )
        ))

        val result = importFromJson(contactRepository, fieldRepository, collectionRepository, tagRepository, json)
        assertThat(result.importedContacts).isEqualTo(0)
        assertThat(result.mergedContacts).isEqualTo(1)
        // Should NOT create new contact, but update existing
        coVerify(exactly = 0) { contactRepository.insertContact(any()) }
        coVerify { collectionRepository.addContactToCollection(99L, 1L, "import") }
    }

    @Test
    fun importFromJson_invalidJson_throwsException() = runTest {
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val fieldRepository = mockk<FieldRepository>(relaxed = true)
        val collectionRepository = mockk<CollectionRepository>(relaxed = true)
        val tagRepository = mockk<TagRepository>(relaxed = true)
        try {
            importFromJson(contactRepository, fieldRepository, collectionRepository, tagRepository, "not valid json{{{")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("无效的 JSON 格式")
        }
    }

    @Test
    fun importFromJson_unsupportedVersion_throwsException() = runTest {
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val fieldRepository = mockk<FieldRepository>(relaxed = true)
        val collectionRepository = mockk<CollectionRepository>(relaxed = true)
        val tagRepository = mockk<TagRepository>(relaxed = true)
        val json = """{"version":99,"app":"badger","exportTime":0,"collections":[]}"""
        try {
            importFromJson(contactRepository, fieldRepository, collectionRepository, tagRepository, json)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("不支持的版本")
        }
    }

    @Test
    fun importContactsToCollection_createsContactsInExistingCollection() = runTest {
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val fieldRepository = mockk<FieldRepository>(relaxed = true)
        val collectionRepository = mockk<CollectionRepository>(relaxed = true)
        val tagRepository = mockk<TagRepository>(relaxed = true)
        val phoneField = ContactField(id = 1, fieldName = "手机", fieldKey = "phone", isSystem = true)
        coEvery { fieldRepository.getAllFieldsOnce() } returns listOf(phoneField)
        coEvery { collectionRepository.getAllCollectionsOnce() } returns emptyList()
        coEvery { contactRepository.insertContact(any()) } returns 10L
        every { contactRepository.getAllContacts() } returns flowOf(emptyList())
        coEvery { tagRepository.getAllTagsOnce() } returns emptyList()

        val json = gson.toJson(BadgerExport(
            version = 2,
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

        val count = importContactsToCollection(contactRepository, fieldRepository, collectionRepository, tagRepository, 5L, json)
        assertThat(count).isEqualTo(2)
        coVerify { collectionRepository.addContactToCollection(any(), 5L, "import") }
    }

    @Test
    fun importContactsToCollection_unsupportedVersion_throwsException() = runTest {
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val fieldRepository = mockk<FieldRepository>(relaxed = true)
        val collectionRepository = mockk<CollectionRepository>(relaxed = true)
        val tagRepository = mockk<TagRepository>(relaxed = true)
        val json = """{"version":99,"app":"badger","exportTime":0,"collections":[]}"""
        try {
            importContactsToCollection(contactRepository, fieldRepository, collectionRepository, tagRepository, 1L, json)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("不支持的版本")
        }
    }

    @Test
    fun gsonRoundTrip_preservesData() {
        val original = BadgerExport(
            version = 2,
            collections = listOf(
                CollectionExport(
                    name = "工作",
                    description = "工作联系人",
                    contacts = listOf(
                        ContactExport(
                            name = "张三",
                            avatarUrl = "https://example.com/avatar.jpg",
                            note = "测试",
                            fields = listOf(
                                FieldExport("phone", "13800138000"),
                                FieldExport("qq", "123456")
                            ),
                            tags = listOf(TagExport("同事", 0xFF1976D2L))
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