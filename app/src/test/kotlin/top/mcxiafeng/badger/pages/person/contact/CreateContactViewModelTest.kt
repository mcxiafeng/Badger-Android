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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CreateContactViewModelTest {

    private lateinit var contactRepository: ContactRepository
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var context: Context

    
    private var insertedContact: ContactCacheEntity? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        contactRepository = mockk(relaxed = true)
        collectionRepository = mockk(relaxed = true)

        coEvery { contactRepository.insertContact(any()) } coAnswers {
            val contact = firstArg<ContactCacheEntity>()
            insertedContact = contact
            42L 
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
            avatarUrl = null, 
            platformKey = "qq",
            platformValue = "12345",
            collectionId = 1L,

        )
        advanceUntilIdle()

        assertThat(id).isEqualTo(42L)
        
        assertThat(insertedContact?.name).isEqualTo("李四")
        assertThat(insertedContact?.bio).isEqualTo("开发者")
        
        coVerify(exactly = 1) {
            contactRepository.updateContactPlatform(42L, "qq", match { it.value == "12345" })
        }
        
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

        
        coVerify(exactly = 1) { collectionRepository.addContactToCollection(42L, 1L, "auto_resolve") }
    }
}
