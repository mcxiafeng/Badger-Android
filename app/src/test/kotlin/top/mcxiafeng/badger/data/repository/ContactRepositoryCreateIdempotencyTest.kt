package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.capture
import io.mockk.mockk
import io.mockk.slot
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
import java.io.IOException

/**
 * create-on-push 客户端 UUID 生命周期回归测试。
 *
 * 关键场景：POST 已经在服务端创建成功，但响应在客户端看来像失败（超时/断线）。
 * 本地必须持久化首次请求的 client UUID，后续 create-on-push 必须复用同一个 UUID，
 * 才能触发服务端幂等重放而不是创建重复联系人。
 */
class ContactRepositoryCreateIdempotencyTest {

    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var contactFieldCacheDao: ContactFieldCacheDao
    private lateinit var contactFieldValueCacheDao: ContactFieldValueCacheDao
    private lateinit var contactPlatformCacheDao: ContactPlatformCacheDao
    private lateinit var contactTagCacheDao: ContactTagCacheDao
    private lateinit var cardCollectionCacheDao: CardCollectionCacheDao
    private lateinit var serverApi: ServerApi
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
        repository = ContactRepositoryImpl(
            contactCacheDao,
            contactFieldCacheDao,
            contactFieldValueCacheDao,
            contactPlatformCacheDao,
            contactTagCacheDao,
            cardCollectionCacheDao,
            serverApi,
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
    fun insertContact_createFails_persistsClientUuidAsPendingServerId() = runTest {
        val captured = slot<ContactCacheEntity>()
        coEvery { serverApi.createPerson(any(), any(), any()) } throws IOException("response lost")
        coEvery { contactCacheDao.insertContact(capture(captured)) } returns 42L

        repository.insertContact(contact())

        assertThat(captured.captured.id).isEqualTo(0L)
        assertThat(captured.captured.isLocalOnly).isTrue()
        assertThat(captured.captured.serverId).isNotNull()
        assertThat(captured.captured.serverId).isNotEmpty()
    }

    @Test
    fun updateContact_localPendingUuid_reusesSameUuidOnRetry() = runTest {
        val pending = contact(id = 42L, serverId = "client-uuid-42", isLocalOnly = true)
        val requestUuid = slot<String>()
        coEvery { contactCacheDao.getContactById(42L) } returns pending
        coEvery { serverApi.createPerson(any(), any(), capture(requestUuid)) } returns "server-uuid-42"

        repository.updateContact(pending.copy(name = "Alice Updated"))

        assertThat(requestUuid.captured).isEqualTo("client-uuid-42")
        coVerify {
            contactCacheDao.updateContact(
                match {
                    it.id == 42L && it.serverId == "server-uuid-42" && !it.isLocalOnly
                }
            )
        }
    }

    @Test
    fun retryAfterPendingCreate_serverResponseLoss_doesNotGenerateAnotherUuid() = runTest {
        val firstCaptured = slot<ContactCacheEntity>()
        val retryUuid = slot<String>()
        val pending = contact(id = 42L)

        coEvery { serverApi.createPerson(any(), any(), any()) } throws IOException("response lost")
        coEvery { contactCacheDao.insertContact(capture(firstCaptured)) } returns 42L
        repository.insertContact(pending)

        val persisted = firstCaptured.captured
        assertThat(persisted.serverId).isNotNull()
        coEvery { contactCacheDao.getContactById(42L) } returns persisted
        coEvery { serverApi.createPerson(any(), any(), capture(retryUuid)) } returns persisted.serverId!!

        repository.updateContact(persisted.copy(name = "Alice Retried"))

        assertThat(retryUuid.captured).isEqualTo(persisted.serverId)
        coVerify(exactly = 1) {
            serverApi.createPerson("Alice Retried", any(), persisted.serverId!!)
        }
    }
}
