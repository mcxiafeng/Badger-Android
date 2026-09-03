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

/**
 * [Phase 3] TagRepositoryImpl 单元测试。
 *
 * 覆盖语义（Phase 3/T14 起创建走 CREATE 入队，PATCH/MEMBER/DELETE 走 Outbox 入队）：
 * - upsertTag：同名复用 / 新建落 PendingCreate 行（serverId=clientUuid）+ CREATE 入队
 * - renameTag / setTagColor：PATCH 入队（colorHash 转换）
 * - deleteTag：DELETE 入队
 * - add/removeTagToContact：本地 cross-ref + 成员子接口入队
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
        // 已 Synced → ensureTagCreateEnqueued 不入队、不写回
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1", isLocalOnly = false)

        val id = repository.upsertTag("朋友", color = 0xFF1976D2L, source = "manual")

        assertThat(id).isEqualTo(1L)
        coVerify(exactly = 0) { tagDao.insertTag(any()) }
        coVerify(exactly = 0) { serverApi.enqueueCreateTag(any(), any(), any(), any()) }
    }

    // ============ upsertTag — 新建落 PendingCreate + CREATE 入队（[T14]）============

    @Test
    fun upsertTag_new_createsPendingRow_andEnqueuesCreateWithClientUuid() = runTest {
        val inserted = mutableListOf<TagCacheEntity>()
        coEvery { tagDao.getTagByName("新标签") } returns null
        coEvery { tagDao.insertTag(any()) } answers { inserted.add(firstArg()); 7L }
        coEvery { tagDao.getTagById(7L) } answers { inserted.single().copy(id = 7L) }

        val id = repository.upsertTag("新标签", color = 0xFF1976D2L, source = "manual")

        assertThat(id).isEqualTo(7L)
        // clientUuid 首次创建即生成并落盘（isLocalOnly 默认 true），CREATE 重放/重试必须复用
        assertThat(inserted.single().serverId).isNotEmpty()
        assertThat(inserted.single().isLocalOnly).isTrue()
        coVerify { serverApi.enqueueCreateTag(7L, "新标签", any(), inserted.single().serverId!!) }
        coVerify(exactly = 0) { tagDao.updateTag(any()) }
    }

    @Test
    fun upsertTag_enqueueFails_keepsLocalRow() = runTest {
        coEvery { tagDao.getTagByName("离线标签") } returns null
        coEvery { tagDao.insertTag(any()) } returns 8L
        coEvery { tagDao.getTagById(8L) } returns tag(id = 8L, name = "离线标签", serverId = "client-x")
        coEvery { serverApi.enqueueCreateTag(any(), any(), any(), any()) } throws IllegalStateException("db down")

        val id = repository.upsertTag("离线标签", color = 0xFF1976D2L, source = "manual")

        // 入队失败不阻塞本地保存；CREATE 由 syncOnce 的 T16c 回填补建
        assertThat(id).isEqualTo(8L)
        coVerify(exactly = 0) { tagDao.updateTag(any()) }
    }

    // ============ renameTag → patchTag 入队 ============

    @Test
    fun renameTag_pushesPatchToServer() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1")

        repository.renameTag(1L, "新名字")

        // [T08] renameTag 改走全行 rebaseTag 写路径
        coVerify { tagDao.updateTag(match { it.name == "新名字" && it.serverId == "t-1" }) }
        coVerify { serverApi.patchTag(1L, "t-1", name = "新名字", colorHash = null) }
    }

    @Test
    fun renameTag_roundTrip_keepsIdentityFields() = runTest {
        // [T08] 更新后投影 round-trip：serverId / personMembers / isLocalOnly / createTime 不变
        val current = tag(id = 1, serverId = "t-1", isLocalOnly = false).copy(
            personMembers = """["p-1"]""",
            createTime = 777L,
        )
        coEvery { tagDao.getTagById(1L) } returns current

        repository.renameTag(1L, "新名字")

        coVerify {
            tagDao.updateTag(match {
                it.serverId == "t-1"
                    && !it.isLocalOnly
                    && it.personMembers == """["p-1"]"""
                    && it.createTime == 777L
                    && it.name == "新名字"
            })
        }
    }

    // ============ deleteTag → DELETE 入队 ============

    @Test
    fun deleteTag_withServerId_pushesDelete() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1")

        repository.deleteTag(1L)

        coVerify { tagDao.deleteTagById(1L) }
        coVerify { serverApi.deleteTag(1L, "t-1") }
    }

    @Test
    fun deleteTag_withoutServerId_skipsHttp() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = null)

        repository.deleteTag(1L)

        coVerify(exactly = 0) { serverApi.deleteTag(any(), any()) }
    }

    // ============ setTagColor → colorHash 入队 ============

    @Test
    fun setTagColor_pushesColorHash() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1", color = 0xFF000000L)

        repository.setTagColor(1L, 0xFF1976D2L)

        coVerify { tagDao.updateTag(match { it.color == 0xFF1976D2L }) }
        coVerify { serverApi.patchTag(1L, "t-1", name = null, colorHash = "0x1976D2FF") }
    }

    @Test
    fun setTagColor_sameColor_skipsHttp() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1", color = 0xFF1976D2L)

        repository.setTagColor(1L, 0xFF1976D2L)

        coVerify(exactly = 0) { serverApi.patchTag(any(), any(), any(), any()) }
    }

    // ============ 成员关联入队 ============

    @Test
    fun addTagToContact_addsLocalRef_andPushesMember() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1")
        coEvery { contactDao.getContactById(9L) } returns contact(serverId = "p-1")

        repository.addTagToContact(9L, 1L)

        coVerify { contactTagDao.insertCrossRef(any()) }
        coVerify { contactDao.bumpContact(9L) }
        coVerify { serverApi.addTagMember(1L, "t-1", "p-1") }
    }

    @Test
    fun removeTagFromContact_removesLocalRef_andPushesMemberRemove() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1")
        coEvery { contactDao.getContactById(9L) } returns contact(serverId = "p-1")

        repository.removeTagFromContact(9L, 1L)

        coVerify { contactTagDao.removeCrossRef(9L, 1L) }
        coVerify { contactDao.bumpContact(9L) }
        coVerify { serverApi.removeTagMember(1L, "t-1", "p-1") }
    }

    @Test
    fun addTagToContact_memberUuidMissing_skipsPushButKeepsLocal() = runTest {
        coEvery { tagDao.getTagById(1L) } returns tag(serverId = "t-1")
        coEvery { contactDao.getContactById(9L) } returns contact(serverId = null)

        repository.addTagToContact(9L, 1L)

        coVerify { contactTagDao.insertCrossRef(any()) }
        coVerify(exactly = 0) { serverApi.addTagMember(any(), any(), any()) }
    }
}
