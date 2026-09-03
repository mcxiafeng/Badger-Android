package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.CollectionMemberCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.network.ServerApi

/**
 * [Phase 3] CollectionRepositoryImpl 单元测试。
 *
 * 覆盖语义（Phase 3/T14 起创建走 CREATE 入队，PATCH/MEMBER/DELETE 走 Outbox 入队）：
 * - insertCollection：本地落 PendingCreate 行（serverId=clientUuid）+ CREATE 入队
 * - updateCollection：变化 → PATCH 入队（Synced 实体不重复入队 CREATE）
 * - deleteCollection：DELETE 入队
 * - add/removeContactFromCollection：本地 collection_member_cache + 成员子接口入队
 *
 * [Phase 4 Task #20] 从 ScanResultDao 迁移到 CollectionMemberCacheDao。
 */
class CollectionRepositoryImplTest {

    private lateinit var cardCollectionCacheDao: CardCollectionCacheDao
    private lateinit var collectionMemberCacheDao: CollectionMemberCacheDao
    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var serverApi: ServerApi
    private lateinit var repository: CollectionRepositoryImpl

    @Before
    fun setup() {
        cardCollectionCacheDao = mockk(relaxed = true)
        collectionMemberCacheDao = mockk(relaxed = true)
        contactCacheDao = mockk(relaxed = true)
        serverApi = mockk(relaxed = true)
        repository = CollectionRepositoryImpl(cardCollectionCacheDao, collectionMemberCacheDao, contactCacheDao, serverApi)
    }

    private fun collection(
        id: Long = 1L,
        name: String = "默认名片夹",
        serverId: String? = null,
        isLocalOnly: Boolean = true,
    ) = CardCollectionCacheEntity(
        id = id,
        serverId = serverId,
        name = name,
        createTime = 1000L,
        isLocalOnly = isLocalOnly,
    )

    private fun contact(id: Long = 9L, serverId: String? = "p-1") = ContactCacheEntity(
        id = id,
        serverId = serverId,
        name = "张三",
        createTime = 1L,
        updateTime = 1L,
    )

    // ============ insertCollection — 本地 PendingCreate + CREATE 入队（[T14]）============

    @Test
    fun insertCollection_createsPendingRow_andEnqueuesCreateWithClientUuid() = runTest {
        val inserted = mutableListOf<CardCollectionCacheEntity>()
        coEvery { cardCollectionCacheDao.insertCollection(any()) } answers { inserted.add(firstArg()); 5L }

        val id = repository.insertCollection(collection(name = "新名片夹"))

        assertThat(id).isEqualTo(5L)
        // 本地行先落 PendingCreate（clientUuid 落盘，CREATE 重放/重试必须复用）
        assertThat(inserted.single().id).isGreaterThan(0L)
        assertThat(inserted.single().isLocalOnly).isTrue()
        assertThat(inserted.single().serverId).isNotEmpty()
        coVerify {
            serverApi.enqueueCreateCollection(5L, "新名片夹", null, null, inserted.single().serverId!!)
        }
    }

    @Test
    fun insertCollection_enqueueFails_keepsLocalRow() = runTest {
        coEvery { cardCollectionCacheDao.insertCollection(any()) } returns 6L
        coEvery { serverApi.enqueueCreateCollection(any(), any(), any(), any(), any()) } throws IllegalStateException("db down")

        val id = repository.insertCollection(collection(name = "离线名片夹"))

        // 入队失败不阻塞本地保存；CREATE 由 syncOnce 的 T16c 回填补建
        assertThat(id).isEqualTo(6L)
        coVerify(exactly = 0) { cardCollectionCacheDao.updateCollection(any()) }
    }

    // ============ updateCollection → patch 入队 ============

    @Test
    fun updateCollection_changed_pushesPatch() = runTest {
        val current = collection(serverId = "col-1", isLocalOnly = false)
        val updated = collection(serverId = "col-1", isLocalOnly = false).copy(name = "改名")
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns current

        repository.updateCollection(updated)

        coVerify { cardCollectionCacheDao.updateCollection(updated) }
        coVerify { serverApi.patchCollection(1L, "col-1", name = "改名", description = null, backgroundURL = null) }
    }

    @Test
    fun updateCollection_noChange_skipsPush() = runTest {
        val current = collection(serverId = "col-1", isLocalOnly = false)
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns current

        repository.updateCollection(current)

        coVerify(exactly = 0) { serverApi.patchCollection(any(), any(), any(), any(), any()) }
    }

    // ============ [F3/T08] 投影 round-trip 不得抹掉 identity ============

    @Test
    fun updateCollection_projectionRoundTrip_keepsServerId() = runTest {
        val existing = collection(id = 1, serverId = "col-1", isLocalOnly = false).copy(
            personMembers = """["p-1"]""",
            createTime = 777L,
        )
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns existing
        // 模拟 UI 投影 round-trip：CardCollectionWithCount.toCacheEntity() 不带 serverId/personMembers
        val projection = top.mcxiafeng.badger.data.CardCollectionWithCount(
            id = 1L,
            name = "改名",
            description = "新描述",
            backgroundImagePath = null,
            dominantColor = null,
            coverAvatarUrl = null,
            createTime = 0L,
            isLocalOnly = true,
            contactCount = 3,
        )

        repository.updateCollection(projection.toCacheEntity())

        // identity 字段以 DB 为准，业务字段按入参更新
        coVerify {
            cardCollectionCacheDao.updateCollection(match {
                it.serverId == "col-1"
                    && it.personMembers == """["p-1"]"""
                    && !it.isLocalOnly
                    && it.createTime == 777L
                    && it.name == "改名"
                    && it.description == "新描述"
            })
        }
    }

    @Test
    fun deleteCollection_projection_keepsServerIdAndPushesDelete() = runTest {
        val existing = collection(id = 1, serverId = "col-1", isLocalOnly = false).copy(
            personMembers = """["p-1"]""",
            createTime = 777L,
        )
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns existing
        val projection = top.mcxiafeng.badger.data.CardCollectionWithCount(
            id = 1L,
            name = "工作",
            description = null,
            backgroundImagePath = null,
            dominantColor = null,
            coverAvatarUrl = "https://example.com/cover.png",
            createTime = 0L,
            isLocalOnly = true,
            contactCount = 3,
        )

        repository.deleteCollection(projection.toCacheEntity())

        // 清封面的整行更新不得抹掉 identity；DELETE 仍按 existing 的 serverId 直推
        coVerify {
            cardCollectionCacheDao.updateCollection(match {
                it.serverId == "col-1"
                    && it.personMembers == """["p-1"]"""
                    && !it.isLocalOnly
                    && it.createTime == 777L
                    && it.coverAvatarUrl == null
            })
        }
        coVerify { serverApi.deleteCollection(1L, "col-1") }
    }

    // ============ deleteCollection → DELETE 入队 ============

    @Test
    fun deleteCollection_withServerId_pushesDelete() = runTest {
        val current = collection(serverId = "col-1")
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns current

        repository.deleteCollection(current)

        coVerify { cardCollectionCacheDao.updateCollection(match { it.coverAvatarUrl == null }) }
        coVerify { serverApi.deleteCollection(1L, "col-1") }
    }

    @Test
    fun deleteCollection_withoutServerId_skipsHttp() = runTest {
        val current = collection(serverId = null)
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns current

        repository.deleteCollection(current)

        coVerify(exactly = 0) { serverApi.deleteCollection(any(), any()) }
    }

    // ============ 成员关联入队 ============

    @Test
    fun addContactToCollection_addsMember_andPushesMember() = runTest {
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns collection(serverId = "col-1")
        coEvery { contactCacheDao.getContactById(9L) } returns contact(serverId = "p-1")

        repository.addContactToCollection(9L, 1L, sourceType = "scan")

        coVerify { collectionMemberCacheDao.insert(any()) }
        coVerify { serverApi.addCollectionMember(1L, "col-1", "p-1") }
    }

    @Test
    fun removeContactFromCollection_removesLocal_andPushesMemberRemove() = runTest {
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns collection(serverId = "col-1")
        coEvery { contactCacheDao.getContactById(9L) } returns contact(serverId = "p-1")

        repository.removeContactFromCollection(9L, 1L)

        coVerify { collectionMemberCacheDao.delete(9L, 1L) }
        coVerify { serverApi.removeCollectionMember(1L, "col-1", "p-1") }
    }

    @Test
    fun addContactToCollection_memberUuidMissing_keepsLocalOnly() = runTest {
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns collection(serverId = "col-1")
        coEvery { contactCacheDao.getContactById(9L) } returns contact(serverId = null)

        repository.addContactToCollection(9L, 1L, sourceType = "scan")

        coVerify { collectionMemberCacheDao.insert(any()) }
        coVerify(exactly = 0) { serverApi.addCollectionMember(any(), any(), any()) }
    }
}
