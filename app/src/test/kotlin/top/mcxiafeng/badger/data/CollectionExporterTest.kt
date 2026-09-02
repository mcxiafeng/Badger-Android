package top.mcxiafeng.badger.data

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository

/**
 * [F6/F7] 导入冲突动作表回归：动作表必须按 rowId 取值，同名联系人/名片夹各自独立。
 */
class CollectionExporterTest {

    private lateinit var contactRepository: ContactRepository
    private lateinit var fieldRepository: FieldRepository
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var tagRepository: TagRepository

    @Before
    fun setup() {
        contactRepository = mockk(relaxed = true)
        fieldRepository = mockk(relaxed = true)
        collectionRepository = mockk(relaxed = true)
        tagRepository = mockk(relaxed = true)
    }

    private fun existingContact(id: Long) = ContactCacheEntity(
        id = id,
        serverId = "srv-$id",
        name = "张伟",
        createTime = 1L,
        updateTime = 1L,
    )

    /** 两个同名"张伟"冲突，动作表分别给 MERGE / SKIP。 */
    private fun twoSameNameConflict(): ImportConflict {
        val contactA = existingContact(1L)
        val contactB = existingContact(2L)
        val ccA = ContactConflict(
            rowId = 0,
            contactExport = ContactExport(name = "张伟", fields = emptyList()),
            existingContact = contactA,
            similarityScore = 1f,
            matchFields = listOf("name"),
        )
        val ccB = ContactConflict(
            rowId = 1,
            contactExport = ContactExport(name = "张伟", fields = emptyList()),
            existingContact = contactB,
            similarityScore = 1f,
            matchFields = listOf("name"),
        )
        return ImportConflict(
            rowId = 0,
            collectionExport = CollectionExport(
                name = "工作",
                contacts = listOf(ccA.contactExport, ccB.contactExport),
            ),
            existingCollection = CardCollectionCacheEntity(id = 5L, name = "工作", createTime = 1L),
            contactConflicts = listOf(ccA, ccB),
        )
    }

    @Test
    fun executeImport_twoSameNameContacts_independentActions() = runTest {
        coEvery { fieldRepository.getAllFieldsOnce() } returns emptyList()
        coEvery { contactRepository.getContactById(1L) } returns existingContact(1L)
        coEvery { fieldRepository.getFieldValueMapByContact(1L) } returns emptyMap()
        coEvery { contactRepository.getContactPlatformKeys(1L) } returns emptySet()
        coEvery { collectionRepository.existsContactInCollection(1L, 5L) } returns false

        val result = executeImport(
            contactRepository = contactRepository,
            fieldRepository = fieldRepository,
            collectionRepository = collectionRepository,
            tagRepository = tagRepository,
            conflicts = listOf(twoSameNameConflict()),
            collectionActions = emptyMap(),
            // [F6/F7] rowId=0 合并、rowId=1 跳过 —— 同名互不影响
            contactActions = mapOf(0 to ContactConflictAction.MERGE, 1 to ContactConflictAction.SKIP),
            renamedCollectionNames = emptyMap(),
        )

        assertThat(result.mergedContacts).isEqualTo(1)
        assertThat(result.importedContacts).isEqualTo(0)
        coVerify(exactly = 1) { contactRepository.updateContact(match { it.id == 1L }) }
        coVerify(exactly = 0) { contactRepository.getContactById(2L) }
        coVerify(exactly = 1) { collectionRepository.addContactToCollection(1L, 5L, "import") }
        coVerify(exactly = 0) { collectionRepository.addContactToCollection(2L, any(), any()) }
    }

    @Test
    fun executeImport_twoSameNameCollections_skipOnlySecond() = runTest {
        coEvery { fieldRepository.getAllFieldsOnce() } returns emptyList()
        val first = twoSameNameConflict()
        // 第二个同名名片夹，rowId=1，动作 SKIP
        val second = first.copy(
            rowId = 1,
            existingCollection = CardCollectionCacheEntity(id = 6L, name = "工作", createTime = 2L),
        )

        val result = executeImport(
            contactRepository = contactRepository,
            fieldRepository = fieldRepository,
            collectionRepository = collectionRepository,
            tagRepository = tagRepository,
            conflicts = listOf(first, second),
            collectionActions = mapOf(0 to CollectionConflictAction.MERGE, 1 to CollectionConflictAction.SKIP),
            contactActions = mapOf(0 to ContactConflictAction.SKIP),
            renamedCollectionNames = emptyMap(),
        )

        // 第一个 MERGE 进 existing colId=5；第二个 SKIP 不触发任何集合写入
        assertThat(result.importedCollections).isEqualTo(1)
        coVerify(exactly = 0) { collectionRepository.insertCollection(any()) }
    }

    @Test
    fun analyzeImportConflicts_twoSameNameContacts_getDistinctRowIds() = runTest {
        coEvery { contactRepository.getAllContacts() } returns kotlinx.coroutines.flow.flowOf(
            listOf(existingContact(1L), existingContact(2L))
        )
        coEvery { contactRepository.getAllContactPlatformsGrouped() } returns emptyMap()
        coEvery { collectionRepository.getAllCollectionsOnce() } returns emptyList()
        val json = """
            {
              "version": 3,
              "collections": [
                {"name": "新夹", "contacts": [{"name": "张伟", "fields": []}, {"name": "张伟", "fields": []}]}
              ]
            }
        """.trimIndent()

        val conflicts = analyzeImportConflicts(contactRepository, fieldRepository, collectionRepository, json)

        val contactRowIds = conflicts.flatMap { it.contactConflicts }.map { it.rowId }
        // 同名联系人的 rowId 必须互不相同，UI 才能独立勾选
        assertThat(contactRowIds).containsNoDuplicates()
        assertThat(contactRowIds).hasSize(2)
    }
}
