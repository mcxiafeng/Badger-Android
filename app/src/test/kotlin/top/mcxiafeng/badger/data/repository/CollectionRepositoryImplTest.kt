package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.network.ServerApi
import java.io.IOException

/**
 * [Phase 3] CollectionRepositoryImpl 直推版单元测试。
 *
 * 覆盖新直推语义：
 * - insertCollection：本地插入 + `POST /api/user/collections` uuid 回填 / 离线 isLocalOnly 兜底
 * - updateCollection：变化 → `PUT /api/user/collections/{uuid}` 直推
 * - deleteCollection：`DELETE /api/user/collections/{uuid}` 直推
 * - add/removeContactFromCollection：本地 scan_results + 成员子接口直推
 */
class CollectionRepositoryImplTest {

    private lateinit var cardCollectionCacheDao: CardCollectionCacheDao
    private lateinit var scanResultDao: ScanResultDao
    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var serverApi: ServerApi
    private lateinit var repository: CollectionRepositoryImpl

    @Before
    fun setup() {
        cardCollectionCacheDao = mockk(relaxed = true)
        scanResultDao = mockk(relaxed = true)
        contactCacheDao = mockk(relaxed = true)
        serverApi = mockk(relaxed = true)
        repository = CollectionRepositoryImpl(cardCollectionCacheDao, scanResultDao, contactCacheDao, serverApi)
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

    // ============ insertCollection — 新建 + uuid 回填 ============

    @Test
    fun insertCollection_createsLocally_andPushesAndBackfillsServerId() = runTest {
        coEvery { cardCollectionCacheDao.insertCollection(any()) } returns 5L
        coEvery { serverApi.createCollection("新名片夹", null, null, null) } returns "col-1"

        val id = repository.insertCollection(collection(name = "新名片夹"))

        assertThat(id).isEqualTo(5L)
        coVerify { serverApi.createCollection("新名片夹", null, null, null) }
        coVerify { cardCollectionCacheDao.updateCollection(match { it.serverId == "col-1" && !it.isLocalOnly }) }
    }

    // ============ insertCollection — 离线 isLocalOnly 兜底 ============

    @Test
    fun insertCollection_createFails_keepsLocalOnly() = runTest {
        coEvery { cardCollectionCacheDao.insertCollection(any()) } returns 6L
        coEvery { serverApi.createCollection(any(), any(), any(), any()) } throws IOException("offline")

        val id = repository.insertCollection(collection(name = "离线名片夹"))

        assertThat(id).isEqualTo(6L)
        coVerify(exactly = 0) { cardCollectionCacheDao.updateCollection(any()) }
    }

    // ============ updateCollection → patch 直推 ============

    @Test
    fun updateCollection_changed_pushesPatch() = runTest {
        val current = collection(serverId = "col-1")
        val updated = collection(serverId = "col-1").copy(name = "改名")
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns current

        repository.updateCollection(updated)

        coVerify { cardCollectionCacheDao.updateCollection(updated) }
        coVerify { serverApi.patchCollection("col-1", name = "改名", description = null, backgroundURL = null) }
    }

    @Test
    fun updateCollection_noChange_skipsPush() = runTest {
        val current = collection(serverId = "col-1")
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns current

        repository.updateCollection(current)

        coVerify(exactly = 0) { serverApi.patchCollection(any(), any(), any(), any()) }
    }

    // ============ deleteCollection → DELETE 直推 ============

    @Test
    fun deleteCollection_withServerId_pushesDelete() = runTest {
        val current = collection(serverId = "col-1")

        repository.deleteCollection(current)

        coVerify { cardCollectionCacheDao.updateCollection(match { it.coverAvatarUrl == null }) }
        coVerify { serverApi.deleteCollection("col-1") }
    }

    @Test
    fun deleteCollection_withoutServerId_skipsHttp() = runTest {
        val current = collection(serverId = null)

        repository.deleteCollection(current)

        coVerify(exactly = 0) { serverApi.deleteCollection(any()) }
    }

    // ============ 成员关联直推 ============

    @Test
    fun addContactToCollection_addsScanResult_andPushesMember() = runTest {
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns collection(serverId = "col-1")
        coEvery { contactCacheDao.getContactById(9L) } returns contact(serverId = "p-1")

        repository.addContactToCollection(9L, 1L, sourceType = "scan")

        coVerify { scanResultDao.insertScanResult(any()) }
        coVerify { serverApi.addCollectionMember("col-1", "p-1") }
    }

    @Test
    fun removeContactFromCollection_removesLocal_andPushesMemberRemove() = runTest {
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns collection(serverId = "col-1")
        coEvery { contactCacheDao.getContactById(9L) } returns contact(serverId = "p-1")

        repository.removeContactFromCollection(9L, 1L)

        coVerify { scanResultDao.deleteScanResultsByContactAndCollection(9L, 1L) }
        coVerify { serverApi.removeCollectionMember("col-1", "p-1") }
    }

    @Test
    fun addContactToCollection_memberUuidMissing_keepsLocalOnly() = runTest {
        coEvery { cardCollectionCacheDao.getCollectionById(1L) } returns collection(serverId = "col-1")
        coEvery { contactCacheDao.getContactById(9L) } returns contact(serverId = null)

        repository.addContactToCollection(9L, 1L, sourceType = "scan")

        coVerify { scanResultDao.insertScanResult(any()) }
        coVerify(exactly = 0) { serverApi.addCollectionMember(any(), any()) }
    }
}
