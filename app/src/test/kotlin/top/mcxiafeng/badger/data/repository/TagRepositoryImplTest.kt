package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.network.ServerApi
import java.io.IOException

/**
 * [Phase 3] TagRepositoryImpl 直推版单元测试。
 *
 * 覆盖新直推语义：
 * - upsertTag：同名复用 / 新建 + `POST /api/user/tags` uuid 回填 / 离线 isLocalOnly 兜底
 * - renameTag / setTagColor：`PUT /api/user/tags/{uuid}` 直推（colorHash 转换）
 * - deleteTag：`DELETE /api/user/tags/{uuid}` 直推
 * - add/removeTagToContact：本地 cross-ref + 成员子接口直推
 */
class TagRepositoryImplTest {

    private lateinit var tagDao: TagCacheDao
    private lateinit var contactTagDao: ContactTagCacheDao
    private lateinit var contactDao: ContactCacheDao
    private lateinit var db: AppDatabase
    private lateinit var serverApi: ServerApi
    private lateinit var repository: TagRepositoryImpl

    @Before
    fun setup() {
        tagDao = mockk(relaxed = true)
        contactTagDao = mockk(relaxed = true)
        contactDao = mockk(relaxed = true)
        db = mockk(relaxed = true)
        serverApi = mockk(relaxed = true)
        repository = TagRepositoryImpl(tagDao, contactTagDao, contactDao, db, serverApi)
    }

    private fun tag(
        id: Long = 1L,
        name: String = "朋友",
        serverId: String? = null,
        color: Long = 0xFF1976D2L,
        isLocalOnly: Boolean = true,
    ) = TagCacheEntity(
        id = id,
        serverId = serverId,
        name = name,
        color = color,
        pinyinInitial = "P",
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

    // ============ upsertTag — 同名复用 ============

    @Test
    fun upsertTag_existingName_reusesId_noHttp() = runTest {
        coEvery { tagDao.getTagByName("朋友") } returns tag(serverId = "t-1", isLocalOnly = false)
        // [迁移适配] 新实现 syncTagCreate 先 getTagById 再决定是否推服务器;
        // 已同步(serverId 非空 && !isLocalOnly)→ 不发 HTTP。
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1", isLocalOnly = false)

        val id = repository.upsertTag("朋友", color = 0xFF1976D2L, source = "manual")

        assertThat(id).isEqualTo(1L)
        coVerify(exactly = 0) { tagDao.insertTag(any()) }
        coVerify(exactly = 0) { serverApi.createTag(any(), any(), any()) }
    }

    // ============ upsertTag — 新建 + uuid 回填 ============

    @Test
    fun upsertTag_new_createsLocally_andPushesAndBackfillsServerId() = runTest {
        coEvery { tagDao.getTagByName("新标签") } returns null
        coEvery { tagDao.insertTag(any()) } returns 7L
        // [迁移适配] syncTagCreate 需要能取回新建的本地行
        coEvery { tagDao.getTagById(7L) } returns tag(id = 7L, name = "新标签", serverId = null)
        coEvery { serverApi.createTag("新标签", any(), null) } returns "uuid-1"

        val id = repository.upsertTag("新标签", color = 0xFF1976D2L, source = "manual")

        assertThat(id).isEqualTo(7L)
        coVerify { serverApi.createTag(eq("新标签"), any(), null) }
        coVerify { tagDao.updateTag(match { it.serverId == "uuid-1" && !it.isLocalOnly }) }
    }

    // ============ upsertTag — 离线 isLocalOnly 兜底 ============

    @Test
    fun upsertTag_createTagFails_keepsLocalOnly() = runTest {
        coEvery { tagDao.getTagByName("离线标签") } returns null
        coEvery { tagDao.insertTag(any()) } returns 8L
        coEvery { serverApi.createTag(any(), any(), any()) } throws IOException("offline")

        val id = repository.upsertTag("离线标签", color = 0xFF1976D2L, source = "manual")

        assertThat(id).isEqualTo(8L)
        coVerify(exactly = 0) { tagDao.updateTag(any()) }
    }

    // ============ renameTag → patchTag 直推 ============

    @Test
    fun renameTag_pushesPatchToServer() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1")

        repository.renameTag(1L, "新名字")

        coVerify { tagDao.renameTag(1L, "新名字", any()) }
        coVerify { serverApi.patchTag("t-1", name = "新名字", colorHash = null) }
    }

    // ============ deleteTag → DELETE 直推 ============

    @Test
    fun deleteTag_withServerId_pushesDelete() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1")

        repository.deleteTag(1L)

        coVerify { tagDao.deleteTagById(1L) }
        coVerify { serverApi.deleteTag("t-1") }
    }

    @Test
    fun deleteTag_withoutServerId_skipsHttp() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = null)

        repository.deleteTag(1L)

        coVerify(exactly = 0) { serverApi.deleteTag(any()) }
    }

    // ============ setTagColor → colorHash 直推 ============

    @Test
    fun setTagColor_pushesColorHash() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1", color = 0xFF000000L)

        repository.setTagColor(1L, 0xFF1976D2L)

        coVerify { tagDao.updateTag(match { it.color == 0xFF1976D2L }) }
        coVerify { serverApi.patchTag("t-1", name = null, colorHash = "0x1976D2FF") }
    }

    @Test
    fun setTagColor_sameColor_skipsHttp() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1", color = 0xFF1976D2L)

        repository.setTagColor(1L, 0xFF1976D2L)

        coVerify(exactly = 0) { serverApi.patchTag(any(), any(), any()) }
    }

    // ============ 成员关联直推 ============

    @Test
    fun addTagToContact_addsLocalRef_andPushesMember() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1")
        coEvery { contactDao.getContactById(9L) } returns contact(serverId = "p-1")

        repository.addTagToContact(9L, 1L)

        coVerify { contactTagDao.insertCrossRef(any()) }
        coVerify { contactDao.bumpContact(9L) }
        coVerify { serverApi.addTagMember("t-1", "p-1") }
    }

    @Test
    fun removeTagFromContact_removesLocalRef_andPushesMemberRemove() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1")
        coEvery { contactDao.getContactById(9L) } returns contact(serverId = "p-1")

        repository.removeTagFromContact(9L, 1L)

        coVerify { contactTagDao.removeCrossRef(9L, 1L) }
        coVerify { contactDao.bumpContact(9L) }
        coVerify { serverApi.removeTagMember("t-1", "p-1") }
    }

    @Test
    fun addTagToContact_memberUuidMissing_skipsPushButKeepsLocal() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1")
        coEvery { contactDao.getContactById(9L) } returns contact(serverId = null)

        repository.addTagToContact(9L, 1L)

        coVerify { contactTagDao.insertCrossRef(any()) }
        coVerify(exactly = 0) { serverApi.addTagMember(any(), any()) }
    }
}
