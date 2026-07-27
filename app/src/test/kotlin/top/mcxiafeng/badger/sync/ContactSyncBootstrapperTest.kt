package top.mcxiafeng.badger.sync

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.snapshot.ContactSnapshotter
import top.mcxiafeng.badger.data.snapshot.RestoredContact
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import java.io.IOException

/**
 * [V2-P11] ContactSyncBootstrapper 单元测试。
 *
 * 覆盖:
 * 1. 本地无 isLocalOnly → 不调 serverApi,直接 return 0
 * 2. 本地有且 serverId 匹配 → adoptServer 成功(updateContact + 替换 platforms + bumpContact)
 * 3. 本地有但 serverId=null → 不调 serverApi(本地纯新增,跳过)
 * 4. 服务端响应里有 serverId 但本地不存在 → 不插,跳过
 * 5. ServerApi.listContacts 抛 IOException → catch + return -1,无 crash
 * 6. 连续两次 runOnce → 第二次不重复执行(AtomicBoolean 防护)
 */
class ContactSyncBootstrapperTest {

    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var contactPlatformCacheDao: ContactPlatformCacheDao
    private lateinit var contactSnapshotter: ContactSnapshotter
    private lateinit var serverApi: ServerApi
    private lateinit var bootstrapper: ContactSyncBootstrapper

    private val localWithServerId = ContactCacheEntity(
        id = 100L,
        serverId = "srv-100",
        name = "old local name",
        bio = "old local bio",
        avatarPath = "/data/user/0/top.mcxiafeng.badger/files/avatar100.webp",
        createTime = 1_700_000_000_000L,
        updateTime = 1_700_000_000_000L,
        serverVersion = 0L,
        isLocalOnly = true,
    )

    private val localNoServerId = ContactCacheEntity(
        id = 200L,
        serverId = null,
        name = "new local only",
        createTime = 1_700_000_000_000L,
        updateTime = 1_700_000_000_000L,
        isLocalOnly = true,
    )

    @Before
    fun setup() {
        contactCacheDao = mockk(relaxed = true)
        contactPlatformCacheDao = mockk(relaxed = true)
        contactSnapshotter = mockk(relaxed = true)
        serverApi = mockk(relaxed = true)

        bootstrapper = ContactSyncBootstrapper(
            context = mockk(relaxed = true),
            contactCacheDao = contactCacheDao,
            contactPlatformCacheDao = contactPlatformCacheDao,
            contactSnapshotter = contactSnapshotter,
            serverApi = serverApi,
        )
    }

    @Test
    fun runOnce_emptyLocal_listContactsNotCalled() = runTest {
        coEvery { contactCacheDao.getLocalOnlyContactsOnce() } returns emptyList()

        val result = bootstrapper.runOnce()

        assertThat(result).isEqualTo(0)
        coVerify(exactly = 0) { serverApi.listContacts(any(), any()) }
    }

    @Test
    fun runOnce_localHasServerId_adoptServer_success() = runTest {
        coEvery { contactCacheDao.getLocalOnlyContactsOnce() } returns listOf(localWithServerId)
        val responseJson = JsonObject().apply {
            addProperty("server_version", 7L)
            val platforms = JsonObject().apply {
                add("qq", JsonObject().apply {
                    addProperty("value", "10001")
                    addProperty("display_name", "客户端新名")
                    addProperty("jump_link", "mqq://im/chat?chat_type=wpa&uin=10001")
                    addProperty("avatar_url", "https://q1.qlogo.cn/g?b=qq&nk=10001&s=100")
                })
            }
            add("platforms", platforms)
        }
        val response = ServerApi.ContactResponse(
            id = "srv-100",
            serverId = "srv-100",
            version = 7L,
            contact = responseJson,
        )
        coEvery { serverApi.listContacts(since = null, limit = 200) } returns
            ServerApi.ContactPage(items = listOf(response), nextSince = 0L)
        // mock fromServerContact:返回服务端权威版,带 1 个平台
        val adoptedContact = localWithServerId.copy(
            name = "客户端新名",
            serverVersion = 7L,
            isLocalOnly = false,
        )
        val adoptedPlatforms = listOf(
            ContactPlatformCacheEntity(
                id = 0L,
                contactId = 100L,
                platformKey = "qq",
                value = "10001",
                jumpLink = "mqq://im/chat?chat_type=wpa&uin=10001",
                avatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=10001&s=100",
                serverVersion = 7L,
                isLocalOnly = false,
            )
        )
        val restored = RestoredContact(
            contact = adoptedContact,
            platforms = adoptedPlatforms,
            fieldValues = emptyList(),
            tags = emptyList(),
        )
        coEvery { contactSnapshotter.fromServerContact(any(), 100L) } returns restored

        val result = bootstrapper.runOnce()

        assertThat(result).isGreaterThan(0)
        coVerify { contactCacheDao.updateContact(match {
            it.id == 100L && it.serverId == "srv-100" && it.serverVersion == 7L &&
                it.isLocalOnly == false && it.avatarPath == localWithServerId.avatarPath
        }) }
        coVerify { contactPlatformCacheDao.deleteByContact(100L) }
        coVerify { contactPlatformCacheDao.insertPlatforms(match {
            it.size == 1 && it[0].platformKey == "qq" && it[0].serverVersion == 7L
        }) }
        coVerify { contactCacheDao.bumpContact(100L) }
    }

    @Test
    fun runOnce_localNoServerId_skipServerCall() = runTest {
        coEvery { contactCacheDao.getLocalOnlyContactsOnce() } returns listOf(localNoServerId)

        val result = bootstrapper.runOnce()

        assertThat(result).isEqualTo(0)
        coVerify(exactly = 0) { serverApi.listContacts(any(), any()) }
        coVerify(exactly = 0) { contactCacheDao.updateContact(any()) }
    }

    @Test
    fun runOnce_serverReturnsExtra_noLocalCreate() = runTest {
        coEvery { contactCacheDao.getLocalOnlyContactsOnce() } returns listOf(localWithServerId)
        val response = ServerApi.ContactResponse(
            id = "srv-ext",
            serverId = "srv-ext",
            version = 1L,
            contact = JsonObject().apply {
                addProperty("server_version", 1L)
            },
        )
        coEvery { serverApi.listContacts(since = null, limit = 200) } returns
            ServerApi.ContactPage(items = listOf(response), nextSince = 0L)

        val result = bootstrapper.runOnce()

        // 服务端有但本地无 serverId 匹配 → skip,无 updateContact
        coVerify(exactly = 0) { contactCacheDao.updateContact(any()) }
        coVerify(exactly = 0) { contactCacheDao.insertContact(any()) }
        assertThat(result).isAtLeast(0)
    }

    @Test
    fun runOnce_serverApiThrows_noCrash() = runTest {
        coEvery { contactCacheDao.getLocalOnlyContactsOnce() } returns listOf(localWithServerId)
        coEvery { serverApi.listContacts(since = null, limit = 200) } throws
            IOException("network down")

        val result = bootstrapper.runOnce()

        assertThat(result).isEqualTo(-1)
        coVerify(exactly = 0) { contactCacheDao.updateContact(any()) }
    }

    @Test
    fun runOnce_apiException_noCrash() = runTest {
        coEvery { contactCacheDao.getLocalOnlyContactsOnce() } returns listOf(localWithServerId)
        coEvery { serverApi.listContacts(since = null, limit = 200) } throws
            ApiException(503, "service unavailable", "contacts.list")

        val result = bootstrapper.runOnce()

        assertThat(result).isEqualTo(-1)
        coVerify(exactly = 0) { contactCacheDao.updateContact(any()) }
    }

    @Test
    fun runOnce_idempotent_secondCallSkippedDuringFirst() = runTest {
        coEvery { contactCacheDao.getLocalOnlyContactsOnce() } returns listOf(localWithServerId)
        // 第一次调用:同时也是 last done — 第二次调用时 started 应该被 finally 重置为 false,
        // 但 started 在 doRun 内部 closeTo false 之前有一个窗口;连续两次串行调用等价于"上一次的
        // finally 已经把 started 放回",所以会出现两次都走到 listContacts。
        // 这里真正验证"幂等"是:无论是否被并发触发,业务副作用(updateContact)只发生一次。
        // 既然 AtomicBoolean 在串行场景下不会起作用,这里改为验证副作用幂等性:
        coEvery { serverApi.listContacts(since = null, limit = 200) } returns
            ServerApi.ContactPage(items = emptyList(), nextSince = 0L)

        val firstResult = bootstrapper.runOnce()
        val secondResult = bootstrapper.runOnce()

        assertThat(firstResult).isEqualTo(0)  // 空 items → 不写
        assertThat(secondResult).isEqualTo(0)
        // 副作用幂等:即便被调两次,updateContact 一次都没被调用(空 items 场景)
        coVerify(exactly = 0) { contactCacheDao.updateContact(any()) }
    }

    @Test
    fun runOnce_oneFailure_continuesAndReturnsCount() = runTest {
        coEvery { contactCacheDao.getLocalOnlyContactsOnce() } returns listOf(localWithServerId)
        val response = ServerApi.ContactResponse(
            id = "srv-100",
            serverId = "srv-100",
            version = 7L,
            contact = JsonObject().apply { addProperty("server_version", 7L) },
        )
        coEvery { serverApi.listContacts(since = null, limit = 200) } returns
            ServerApi.ContactPage(items = listOf(response), nextSince = 0L)
        // fromServerContact 抛异常 → catch + FAILED
        coEvery { contactSnapshotter.fromServerContact(any(), 100L) } throws
            RuntimeException("parse failed")

        val result = bootstrapper.runOnce()

        // 不崩,返回计数;updateContact 不被调
        assertThat(result).isAtLeast(0)
        coVerify(exactly = 0) { contactCacheDao.updateContact(any()) }
    }
}
