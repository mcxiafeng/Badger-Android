package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.sync.OutboxStore

/**
 * [T14] create-on-push 客户端 UUID 生命周期回归测试（Repository 入队侧）。
 *
 * Phase 3 起创建不再直推：本地先落 `PendingCreate` 行（clientUuid 落盘到 `serverId`）+
 * CREATE op 入队；实际 POST、uuid 复用与 400 降级由 `SyncEngineTest` 端到端覆盖。
 * 本文件验证 Repository 侧的关键不变量：**clientUuid 首次生成后持久化、后续入队复用同一 uuid**。
 */
class ContactRepositoryCreateIdempotencyTest {

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
        outboxStore = OutboxStore(mockk(relaxed = true))
        repository = ContactRepositoryImpl(
            contactCacheDao,
            contactFieldCacheDao,
            contactFieldValueCacheDao,
            contactPlatformCacheDao,
            contactTagCacheDao,
            cardCollectionCacheDao,
            serverApi,
            outboxStore,
        )
    }

    private fun contact(
        id: Long = 0L,
        serverId: String? = null,
        isLocalOnly: Boolean = true,
        name: String = "Alice",
    ) = ContactCacheEntity(
        id = id,
        serverId = serverId,
        name = name,
        createTime = 1000L,
        updateTime = 1000L,
        isLocalOnly = isLocalOnly,
    )

    @Test
    fun insertContact_persistsClientUuid_andEnqueuesCreateWithSameUuid() = runTest {
        val inserted = mutableListOf<ContactCacheEntity>()
        coEvery { contactCacheDao.insertContact(any()) } answers { inserted.add(firstArg()); 42L }

        repository.insertContact(contact())

        // 本地行先落 PendingCreate，clientUuid 落盘（幂等键）
        assertThat(inserted.single().id).isEqualTo(0L)
        assertThat(inserted.single().isLocalOnly).isTrue()
        assertThat(inserted.single().serverId).isNotEmpty()
        // CREATE 入队用的 remoteId 与落盘的 clientUuid 完全一致
        verify {
            serverApi.enqueueCreatePerson(42L, "Alice", any(), inserted.single().serverId!!)
        }
        // 不再直推 POST
        coVerify(exactly = 0) { serverApi.createPerson(any(), any(), any()) }
    }

    @Test
    fun updateContact_localPending_enqueuesCreateIdempotentlyAndPatchesWithClientUuid() = runTest {
        val pending = contact(id = 42L, serverId = "client-uuid-42", isLocalOnly = true)
        coEvery { contactCacheDao.getContactById(42L) } returns pending

        repository.updateContact(pending.copy(name = "Alice Updated"))

        // PendingCreate：CREATE 幂等再入队（mergeKey 忽略重复）+ PATCH remoteId 暂用 clientUuid
        verify { serverApi.enqueueCreatePerson(42L, "Alice Updated", any(), "client-uuid-42") }
        verify {
            serverApi.updatePerson(42L, "client-uuid-42", name = "Alice Updated", profile = any())
        }
        coVerify(exactly = 0) { serverApi.createPerson(any(), any(), any()) }
    }

    @Test
    fun updateContact_synced_skipsCreateEnqueue_andPatchesWithServerId() = runTest {
        val synced = contact(id = 42L, serverId = "server-uuid-42", isLocalOnly = false)
        coEvery { contactCacheDao.getContactById(42L) } returns synced

        repository.updateContact(synced.copy(name = "Alice Updated"))

        verify(exactly = 0) { serverApi.enqueueCreatePerson(any(), any(), any(), any()) }
        verify {
            serverApi.updatePerson(42L, "server-uuid-42", name = "Alice Updated", profile = any())
        }
    }

    @Test
    fun updateContact_unidentifiedLegacy_generatesAndPersistsUuid() = runTest {
        // 存量行（历史版本遗留）：无 serverId 且 isLocalOnly=false → Unidentified
        val legacy = contact(id = 42L, serverId = null, isLocalOnly = false)
        coEvery { contactCacheDao.getContactById(42L) } returns legacy
        val persisted = mutableListOf<ContactCacheEntity>()
        coEvery { contactCacheDao.updateContact(any()) } answers { persisted.add(firstArg()); Unit }

        repository.updateContact(legacy.copy(name = "Alice Updated"))

        // updateContact 先写 normalized 行，再由 ensureCreateEnqueued 写 uuid+isLocalOnly=true；
        // 取 last() 即 ensureCreateEnqueued 的落盘写（uuid 落盘后入队复用）
        val persistedRow = persisted.last()
        assertThat(persistedRow.serverId).isNotEmpty()
        assertThat(persistedRow.isLocalOnly).isTrue()
        verify { serverApi.enqueueCreatePerson(42L, "Alice Updated", any(), persistedRow.serverId!!) }
    }
}
