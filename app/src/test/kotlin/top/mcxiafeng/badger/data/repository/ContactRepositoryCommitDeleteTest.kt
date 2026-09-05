package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.OutboxStore
import java.io.IOException

/**
 * [Phase 3] ContactRepositoryImpl.commitDelete / commitMerge 直推单测。
 *
 * 覆盖新直推语义（`docs/api-handover-migration-plan.md` §C2/C3）：
 * - commitDelete：软删 → 直发 `DELETE /api/user/persons/{uuid}`；200/404 → hardDelete；
 *   其他失败 → 恢复软删（UI 重新可见，可重试）；isLocalOnly 跳过 HTTP。
 * - commitMerge：直调 `POST /api/user/persons/{targetUuid}/merge`（merged_ids）；
 *   merged 行服务端删除，客户端 hardDelete；404 不视为成功，保留本地数据。
 */
class ContactRepositoryCommitDeleteTest {

    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var contactFieldCacheDao: ContactFieldCacheDao
    private lateinit var contactFieldValueCacheDao: ContactFieldValueCacheDao
    private lateinit var contactPlatformCacheDao: ContactPlatformCacheDao
    private lateinit var contactTagCacheDao: ContactTagCacheDao
    private lateinit var cardCollectionCacheDao: CardCollectionCacheDao
    private lateinit var serverApi: ServerApi
    private lateinit var outboxStore: OutboxStore
    private lateinit var repository: ContactRepositoryImpl

    @Before
    fun setup() {
        contactCacheDao = mockk(relaxed = true)
        contactFieldCacheDao = mockk(relaxed = true)
        contactFieldValueCacheDao = mockk(relaxed = true)
        contactPlatformCacheDao = mockk(relaxed = true)
        contactTagCacheDao = mockk(relaxed = true)
        cardCollectionCacheDao = mockk(relaxed = true)
        serverApi = mockk(relaxed = true)
        // [T14] OutboxStore 用 relaxed mock：cancelEntity / enqueue 仅记录调用供 verify
        outboxStore = mockk(relaxed = true)
        repository = ContactRepositoryImpl(
            contactCacheDao,
            contactFieldCacheDao,
            contactFieldValueCacheDao,
            contactPlatformCacheDao,
            contactTagCacheDao,
            cardCollectionCacheDao,
            serverApi,
            outboxStore,
            avatarFetcher = { _, _ -> null },
        )
    }

    private fun existingContact(
        id: Long = 1L,
        serverId: String? = "srv-1",
        isLocalOnly: Boolean = false,
    ) = ContactCacheEntity(
        id = id,
        name = "Bob",
        serverId = serverId,
        isLocalOnly = isLocalOnly,
        isDeleted = false,
        createTime = 1000L,
        updateTime = 1000L,
    )

    @Test
    fun commitDelete_200_success_hardDelete() = runTest {
        val contact = existingContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deletePerson("srv-1") } returns true

        val result = repository.commitDelete(1L)

        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        coVerify { contactCacheDao.setDeleted(1L, deleted = true, any()) }
        coVerify { serverApi.deletePerson("srv-1") }
        coVerify { contactCacheDao.deleteById(1L) }
        coVerify { contactPlatformCacheDao.deleteByContact(1L) }
        coVerify { contactFieldValueCacheDao.deleteByContact(1L) }
        coVerify { contactTagCacheDao.clearContactTags(1L) }
    }

    @Test
    fun commitDelete_404_idempotent_hardDelete() = runTest {
        val contact = existingContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deletePerson("srv-1") } throws ApiException(404, "not found", "persons.delete")

        val result = repository.commitDelete(1L)

        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        coVerify { contactCacheDao.deleteById(1L) }
    }

    @Test
    fun commitDelete_5xx_restoreSoftDeleted() = runTest {
        val contact = existingContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deletePerson("srv-1") } throws ApiException(503, "service unavailable", "persons.delete")

        val result = repository.commitDelete(1L)

        assertThat(result).isInstanceOf(CommitResult.SentFailed::class.java)
        assertThat((result as CommitResult.SentFailed).reason).contains("HTTP 503")
        coVerify { contactCacheDao.setDeleted(1L, deleted = false, any()) }
        coVerify(exactly = 0) { contactCacheDao.deleteById(1L) }
    }

    @Test
    fun commitDelete_ioException_restoreSoftDeleted() = runTest {
        val contact = existingContact()
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deletePerson("srv-1") } throws IOException("network unreachable")

        val result = repository.commitDelete(1L)

        assertThat(result).isInstanceOf(CommitResult.SentFailed::class.java)
        coVerify { contactCacheDao.setDeleted(1L, deleted = false, any()) }
        coVerify(exactly = 0) { contactCacheDao.deleteById(1L) }
    }

    @Test
    fun commitDelete_isLocalOnly_skipsHttpAndHardDeletes() = runTest {
        val contact = existingContact(serverId = null, isLocalOnly = true)
        coEvery { contactCacheDao.getContactById(1L) } returns contact

        val result = repository.commitDelete(1L)

        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        coVerify { contactCacheDao.deleteById(1L) }
        coVerify(exactly = 0) { serverApi.deletePerson(any()) }
    }

    @Test
    fun commitDelete_pendingCreate_cancelsOutboxEnqueuesDeleteAndHardDeletes() = runTest {
        // [T14] 本地新建未确认上云（serverId=clientUuid）：取消未发 CREATE/PATCH 防复活，
        // DELETE 入队兜底未知结局（服务端可能已建），本地立即硬删，不直推
        val contact = existingContact(serverId = "client-uuid-1", isLocalOnly = true)
        coEvery { contactCacheDao.getContactById(1L) } returns contact

        val result = repository.commitDelete(1L)

        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        coVerify { outboxStore.cancelEntity(EntityKind.PERSON, 1L) }
        coVerify { serverApi.enqueueDeletePerson(1L, "client-uuid-1") }
        coVerify { contactCacheDao.deleteById(1L) }
        coVerify(exactly = 0) { serverApi.deletePerson(any()) }
    }

    @Test
    fun commitDelete_contactNotFound_returnsNotFound() = runTest {
        coEvery { contactCacheDao.getContactById(99L) } returns null

        val result = repository.commitDelete(99L)

        assertThat(result).isEqualTo(CommitResult.NotFound)
        coVerify(exactly = 0) { serverApi.deletePerson(any()) }
    }

    // ============ [T09] 硬删回收本地头像文件 ============

    @Test
    fun commitDelete_hardDelete_removesAvatarFile() = runTest {
        val tmpDir = kotlin.io.path.createTempDirectory("avatar-harddelete").toFile()
        val avatar = java.io.File(tmpDir, "contact_1_avatar.webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val contact = existingContact().copy(avatarPath = avatar.absolutePath)
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deletePerson("srv-1") } returns true

        val result = repository.commitDelete(1L)

        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        assertThat(avatar.exists()).isFalse()
        tmpDir.deleteRecursively()
    }

    @Test
    fun commitDelete_localOnly_hardDelete_removesAvatarFile() = runTest {
        val tmpDir = kotlin.io.path.createTempDirectory("avatar-localonly").toFile()
        val avatar = java.io.File(tmpDir, "contact_1_avatar.webp").apply { writeBytes(byteArrayOf(1)) }
        val contact = existingContact(serverId = null, isLocalOnly = true).copy(avatarPath = avatar.absolutePath)
        coEvery { contactCacheDao.getContactById(1L) } returns contact

        val result = repository.commitDelete(1L)

        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        assertThat(avatar.exists()).isFalse()
        tmpDir.deleteRecursively()
    }

    @Test
    fun commitDelete_hardDelete_withoutAvatarPath_doesNotCrash() = runTest {
        val contact = existingContact().copy(avatarPath = null)
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { serverApi.deletePerson("srv-1") } returns true

        val result = repository.commitDelete(1L)

        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        coVerify { contactCacheDao.deleteById(1L) }
    }

    @Test
    fun commitMerge_200_hardDeleteMerged() = runTest {
        val target = existingContact(id = 1L)
        val merged1 = existingContact(id = 2L, serverId = "srv-2")
        val merged2 = existingContact(id = 3L, serverId = "srv-3")
        coEvery { contactCacheDao.getContactById(1L) } returns target
        coEvery { contactCacheDao.getContactById(2L) } returns merged1
        coEvery { contactCacheDao.getContactById(3L) } returns merged2
        coEvery { serverApi.mergePersons("srv-1", listOf("srv-2", "srv-3")) } returns "srv-1"

        val result = repository.commitMerge(1L, listOf(2L, 3L))

        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        coVerify { serverApi.mergePersons("srv-1", listOf("srv-2", "srv-3")) }
        coVerify { contactCacheDao.deleteById(2L) }
        coVerify { contactCacheDao.deleteById(3L) }
        coVerify { contactCacheDao.bumpContact(1L) }
    }

    @Test
    fun commitMerge_404_keepsLocalData() = runTest {
        val target = existingContact(id = 1L)
        val merged1 = existingContact(id = 2L, serverId = "srv-2")
        coEvery { contactCacheDao.getContactById(1L) } returns target
        coEvery { contactCacheDao.getContactById(2L) } returns merged1
        coEvery { serverApi.mergePersons(any(), any()) } throws ApiException(404, "not found", "persons.merge")

        val result = repository.commitMerge(1L, listOf(2L))

        assertThat(result).isInstanceOf(CommitResult.SentFailed::class.java)
        coVerify(exactly = 0) { contactCacheDao.deleteById(2L) }
    }

    @Test
    fun commitMerge_5xx_returnsFailed() = runTest {
        val target = existingContact(id = 1L)
        val merged1 = existingContact(id = 2L, serverId = "srv-2")
        coEvery { contactCacheDao.getContactById(1L) } returns target
        coEvery { contactCacheDao.getContactById(2L) } returns merged1
        coEvery { serverApi.mergePersons(any(), any()) } throws ApiException(503, "unavailable", "persons.merge")

        val result = repository.commitMerge(1L, listOf(2L))

        assertThat(result).isInstanceOf(CommitResult.SentFailed::class.java)
        assertThat((result as CommitResult.SentFailed).reason).contains("HTTP 503")
        coVerify(exactly = 0) { contactCacheDao.deleteById(2L) }
    }

    @Test
    fun commitMerge_targetLocalOnly_returnsFailed() = runTest {
        val target = existingContact(id = 1L, serverId = null, isLocalOnly = true)
        coEvery { contactCacheDao.getContactById(1L) } returns target

        val result = repository.commitMerge(1L, listOf(2L))

        assertThat(result).isInstanceOf(CommitResult.SentFailed::class.java)
        coVerify(exactly = 0) { serverApi.mergePersons(any(), any()) }
    }

    @Test
    fun commitMerge_allMergedLocalOnly_clearsLocal() = runTest {
        val target = existingContact(id = 1L)
        val merged1 = existingContact(id = 2L, serverId = null, isLocalOnly = true)
        coEvery { contactCacheDao.getContactById(1L) } returns target
        coEvery { contactCacheDao.getContactById(2L) } returns merged1

        val result = repository.commitMerge(1L, listOf(2L))

        assertThat(result).isEqualTo(CommitResult.SentSuccess)
        coVerify { contactCacheDao.deleteById(2L) }
        coVerify(exactly = 0) { serverApi.mergePersons(any(), any()) }
    }

    @Test
    fun commitMerge_emptyMergedIds_returnsNotFound() = runTest {
        val target = existingContact(id = 1L)
        coEvery { contactCacheDao.getContactById(1L) } returns target

        val result = repository.commitMerge(1L, emptyList())

        assertThat(result).isEqualTo(CommitResult.NotFound)
        coVerify(exactly = 0) { serverApi.mergePersons(any(), any()) }
    }

    @Test
    fun commitMerge_targetNotFound_returnsNotFound() = runTest {
        coEvery { contactCacheDao.getContactById(99L) } returns null

        val result = repository.commitMerge(99L, listOf(2L))

        assertThat(result).isEqualTo(CommitResult.NotFound)
        coVerify(exactly = 0) { serverApi.mergePersons(any(), any()) }
    }
}
