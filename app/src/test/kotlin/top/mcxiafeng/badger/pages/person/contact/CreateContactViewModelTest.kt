package top.mcxiafeng.badger.pages.person.contact

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository

/**
 * [B5] CreateContactViewModel 自动抓取创建单元测试。
 *
 * 覆盖：
 * 1. createContactFromResolve 基本路径（name/bio/avatarUrl/平台/名片夹）
 * 2. avatarUrl 为 null 时仍正常创建（无头像下载）
 * 3. platformKey/platformValue 为空时不添加平台条目
 * 4. createMinimalContact 基本路径
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CreateContactViewModelTest {

    private lateinit var contactRepository: ContactRepository
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var context: Context

    // 用于捕获 insertContact 传入的实体
    private var insertedContact: ContactCacheEntity? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        contactRepository = mockk(relaxed = true)
        collectionRepository = mockk(relaxed = true)

        coEvery { contactRepository.insertContact(any()) } coAnswers {
            val contact = firstArg<ContactCacheEntity>()
            insertedContact = contact
            42L // 模拟返回本地 ID
        }
        coEvery { collectionRepository.getAllCollectionsOnce() } returns listOf(
            mockk(relaxed = true) { every { id } returns 1L }
        )

        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { contactRepository }
                    single { collectionRepository }
                },
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        runCatching { GlobalContext.stopKoin() }
    }

    private fun vm() = CreateContactViewModel()

    @Test
    fun `createMinimalContact creates contact with name and adds to collection`() = runTest(UnconfinedTestDispatcher()) {
        val id = vm().createMinimalContact("张三", 1L)
        advanceUntilIdle()

        assertThat(id).isEqualTo(42L)
        coVerify(exactly = 1) { contactRepository.insertContact(match { it.name == "张三" }) }
        coVerify(exactly = 1) { collectionRepository.addContactToCollection(42L, 1L, "manual") }
    }

    @Test
    fun `createContactFromResolve with all fields creates contact and adds platform`() = runTest(UnconfinedTestDispatcher()) {
        val id = vm().createContactFromResolve(
            name = "李四",
            bio = "开发者",
            avatarUrl = null, // 跳过头像下载（需要 mockkStatic HttpUtil）
            platformKey = "qq",
            platformValue = "12345",
            collectionId = 1L,

        )
        advanceUntilIdle()

        assertThat(id).isEqualTo(42L)
        // 验证联系人属性
        assertThat(insertedContact?.name).isEqualTo("李四")
        assertThat(insertedContact?.bio).isEqualTo("开发者")
        // 验证平台添加
        coVerify(exactly = 1) {
            contactRepository.updateContactPlatform(42L, "qq", match { it.value == "12345" })
        }
        // 验证名片夹
        coVerify(exactly = 1) { collectionRepository.addContactToCollection(42L, 1L, "auto_resolve") }
    }

    @Test
    fun `createContactFromResolve with null platform does not add platform entry`() = runTest(UnconfinedTestDispatcher()) {
        vm().createContactFromResolve(
            name = "王五",
            bio = null,
            avatarUrl = null,
            platformKey = null,
            platformValue = null,
            collectionId = null,

        )
        advanceUntilIdle()

        coVerify(exactly = 0) { contactRepository.updateContactPlatform(any(), any(), any()) }
        assertThat(insertedContact?.name).isEqualTo("王五")
        assertThat(insertedContact?.bio).isNull()
    }

    @Test
    fun `createContactFromResolve with blank bio stores null`() = runTest(UnconfinedTestDispatcher()) {
        vm().createContactFromResolve(
            name = "赵六",
            bio = "   ",
            avatarUrl = null,
            platformKey = null,
            platformValue = null,
            collectionId = null,

        )
        advanceUntilIdle()

        assertThat(insertedContact?.bio).isNull()
    }

    @Test
    fun `createContactFromResolve with blank platformKey does not add platform`() = runTest(UnconfinedTestDispatcher()) {
        vm().createContactFromResolve(
            name = "钱七",
            bio = null,
            avatarUrl = null,
            platformKey = "",
            platformValue = "some-value",
            collectionId = null,

        )
        advanceUntilIdle()

        coVerify(exactly = 0) { contactRepository.updateContactPlatform(any(), any(), any()) }
    }

    @Test
    fun `createContactFromResolve uses default collection when collectionId is null`() = runTest(UnconfinedTestDispatcher()) {
        vm().createContactFromResolve(
            name = "孙八",
            bio = null,
            avatarUrl = null,
            platformKey = null,
            platformValue = null,
            collectionId = null,

        )
        advanceUntilIdle()

        // 验证走了 ensureCollectionId → 默认名片夹 ID=1
        coVerify(exactly = 1) { collectionRepository.addContactToCollection(42L, 1L, "auto_resolve") }
    }
}
