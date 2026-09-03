package top.mcxiafeng.badger.pages.person.contact

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.IdentifyResponse
import top.mcxiafeng.badger.pages.person.contact.detail.ContactDetailViewModel

/**
 * ContactDetailViewModel C2 批量解析单元测试。
 *
 * 覆盖:
 * 1. batchResolvePlatforms 成功 → 每条 URL 映射到 BatchResolvedItem
 * 2. 部分失败 → failed 条目 resolved=null
 * 3. 空列表 → 空结果
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContactDetailViewModelBatchTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { mockk<ContactRepository>(relaxed = true) }
                    single { mockk<CollectionRepository>(relaxed = true) }
                    single { mockk<FieldRepository>(relaxed = true) }
                    single { mockk<TagRepository>(relaxed = true) }
                    single { mockk<UserProfileTicker>(relaxed = true) }
                    single { mockk<top.mcxiafeng.badger.ai.AiTagGenerator>(relaxed = true) }
                },
            )
        }
        mockkObject(ContactNetworkResolver)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        runCatching { GlobalContext.stopKoin() }
    }

    private fun vm() = ContactDetailViewModel()

    @Test
    fun `batchResolvePlatforms maps all URLs to BatchResolvedItems`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { ContactNetworkResolver.identifyBatch(any()) } returns listOf(
            IdentifyResponse(kind = "qq", name = "QQ用户", avatarUrl = "https://q1.qlogo.cn/qq", description = "sig", contactMap = mapOf("qq" to "12345")),
            IdentifyResponse(kind = "bilibili", name = "B站用户", avatarUrl = null, description = null, contactMap = emptyMap()),
        )

        val results = vm().batchResolvePlatforms(listOf("https://q1.qlogo.cn/qq", "https://space.bilibili.com/99999"))

        assertThat(results).hasSize(2)
        assertThat(results[0].fieldKey).isEqualTo("qq")
        assertThat(results[0].resolved).isNotNull()
        assertThat(results[0].resolved!!.name).isEqualTo("QQ用户")
        assertThat(results[0].resolved!!.avatarUrl).isEqualTo("https://q1.qlogo.cn/qq")
        assertThat(results[1].fieldKey).isEqualTo("bilibili")
        assertThat(results[1].resolved).isNotNull()
        assertThat(results[1].resolved!!.name).isEqualTo("B站用户")
        assertThat(results[1].resolved!!.avatarUrl).isNull()
    }

    @Test
    fun `batchResolvePlatforms handles partial failure`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { ContactNetworkResolver.identifyBatch(any()) } returns listOf(
            IdentifyResponse(kind = "qq", name = "QQ用户", avatarUrl = null, description = null, contactMap = emptyMap()),
            null, // 解析失败
        )

        val results = vm().batchResolvePlatforms(listOf("https://q1.qlogo.cn/qq", "gibberish"))

        assertThat(results).hasSize(2)
        assertThat(results[0].resolved).isNotNull()
        assertThat(results[0].fieldKey).isEqualTo("qq")
        assertThat(results[1].resolved).isNull()
        assertThat(results[1].fieldKey).isEqualTo("unknown")
    }

    @Test
    fun `batchResolvePlatforms with empty list returns empty`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { ContactNetworkResolver.identifyBatch(any()) } returns emptyList()

        val results = vm().batchResolvePlatforms(emptyList())

        assertThat(results).isEmpty()
    }

    @Test
    fun `batchResolvePlatforms strips blank name and avatarUrl`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { ContactNetworkResolver.identifyBatch(any()) } returns listOf(
            IdentifyResponse(kind = "github", name = "  ", avatarUrl = "", description = null, contactMap = emptyMap()),
        )

        val results = vm().batchResolvePlatforms(listOf("https://github.com/octocat"))

        assertThat(results).hasSize(1)
        assertThat(results[0].resolved!!.name).isNull()
        assertThat(results[0].resolved!!.avatarUrl).isNull()
    }
}
