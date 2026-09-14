package top.mcxiafeng.badger.pages.person.contact

import com.google.common.truth.Truth.assertThat
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.PersonDto
import top.mcxiafeng.badger.network.UserProfileResponse

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UserProfileDetailViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)
    private lateinit var repository: RecordingUserProfileRepository

    
    class RecordingUserProfileRepository(
        initial: UserProfileCacheEntity? = null,
    ) : UserProfileRepository {
        var lastSaved: UserProfileCacheEntity? = initial
        override fun getUserProfile() = kotlinx.coroutines.flow.flowOf(lastSaved)
        override suspend fun getUserProfileOnce(): UserProfileCacheEntity? = lastSaved
        override suspend fun saveUserProfile(profile: UserProfileCacheEntity) {
            lastSaved = profile
        }
        override suspend fun updatePlatformField(
            fieldKey: String, jumpLink: String, value: String?, displayName: String?, avatarUrl: String?, originalLink: String?
        ) {}
        override suspend fun removePlatform(platformName: String) {}
        override suspend fun editUserProfile(
            transform: (UserProfileCacheEntity) -> UserProfileCacheEntity
        ): UserProfileCacheEntity {
            val updated = transform(lastSaved ?: UserProfileCacheEntity(name = "用户", updateTime = 0L))
            lastSaved = updated
            return updated
        }
        override suspend fun applyRemoteProfile(resp: UserProfileResponse) {}
        override suspend fun applySyncedSelfPerson(person: PersonDto) {}
        override suspend fun refreshFromServer(): Boolean = true
    }

    @Before
    fun setUp() {
        
        
        Dispatchers.setMain(testDispatcher)
        repository = RecordingUserProfileRepository(seedProfile())
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(module { single<UserProfileRepository> { repository } })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        runCatching { GlobalContext.stopKoin() }
    }

    private fun seedProfile() = UserProfileCacheEntity(
        id = 1L, name = "用户", updateTime = 1000L,
    )

    private fun vm(): UserProfileDetailViewModel =
        UserProfileDetailViewModel(
            ioDispatcher = testDispatcher,
            userProfileRepository = repository,
        )

    @Test
    fun updateProfileField_sex_mapsAndClearsOnBlank() = runTest(scheduler) {
        val v = vm()
        var done: UserProfileCacheEntity? = null

        v.updateProfileField("sex", "男") { done = it }
        advanceUntilIdle()
        assertThat(repository.lastSaved?.sex).isEqualTo("男")
        assertThat(done?.sex).isEqualTo("男")

        v.updateProfileField("sex", "") { done = it }
        advanceUntilIdle()
        assertThat(repository.lastSaved?.sex).isNull()
    }

    @Test
    fun updateProfileField_birthday_mapsAndClearsOnBlank() = runTest(scheduler) {
        val v = vm()
        var done: UserProfileCacheEntity? = null

        v.updateProfileField("birthday", "2000-01-01") { done = it }
        advanceUntilIdle()
        assertThat(repository.lastSaved?.birthday).isEqualTo("2000-01-01")
        assertThat(done?.birthday).isEqualTo("2000-01-01")

        v.updateProfileField("birthday", "   ") { done = it }
        advanceUntilIdle()
        assertThat(repository.lastSaved?.birthday).isNull()
    }

    @Test
    fun updateProfileField_country_region_backgroundURL_mapCorrectly() = runTest(scheduler) {
        val v = vm()
        val doneHolder = mutableListOf<UserProfileCacheEntity?>()

        v.updateProfileField("country", "中国") { doneHolder += it }
        advanceUntilIdle()
        v.updateProfileField("region", "北京") { doneHolder += it }
        advanceUntilIdle()
        v.updateProfileField("backgroundURL", "https://x/bg.jpg") { doneHolder += it }
        advanceUntilIdle()

        assertThat(repository.lastSaved?.country).isEqualTo("中国")
        assertThat(repository.lastSaved?.region).isEqualTo("北京")
        assertThat(repository.lastSaved?.backgroundURL).isEqualTo("https://x/bg.jpg")
        assertThat(doneHolder.filterNotNull()).hasSize(3)
    }

    @Test
    fun updateProfileField_unknownKey_doesNotSave() = runTest(scheduler) {
        val v = vm()
        var called = false
        v.updateProfileField("hacked", "x") { called = true }
        advanceUntilIdle()
        assertThat(called).isFalse()
        assertThat(repository.lastSaved).isEqualTo(seedProfile())
    }

    @Test
    fun updateProfileField_preservesOtherFields() = runTest(scheduler) {
        repository.lastSaved = seedProfile().copy(bio = "原简介", sex = "女")
        val v = vm()

        v.updateProfileField("birthday", "1999-09-09") {}
        advanceUntilIdle()

        assertThat(repository.lastSaved?.bio).isEqualTo("原简介")
        assertThat(repository.lastSaved?.sex).isEqualTo("女")
        assertThat(repository.lastSaved?.birthday).isEqualTo("1999-09-09")
    }

    @Test
    fun mergeImportedProfile_overridesOnlyNonBlankFields() {
        val current = seedProfile().copy(name = "原名", bio = "原简介", avatarPath = "/old.webp")
        val merged = UserProfileDetailViewModel.mergeImportedProfile(
            current = current,
            importedName = "新名",
            importedBio = null,
            importedAvatarPath = "/new.webp",
        )
        assertThat(merged.name).isEqualTo("新名")
        assertThat(merged.bio).isEqualTo("原简介")
        assertThat(merged.avatarPath).isEqualTo("/new.webp")
    }

    @Test
    fun mergeImportedProfile_filtersUnknownNicknameAndBlank() {
        val current = seedProfile().copy(name = "原名", bio = "原简介")
        val merged = UserProfileDetailViewModel.mergeImportedProfile(
            current = current,
            importedName = "未知",
            importedBio = "   ",
            importedAvatarPath = null,
        )
        assertThat(merged.name).isEqualTo("原名")
        assertThat(merged.bio).isEqualTo("原简介")
        assertThat(merged.avatarPath).isNull()
    }

    @Test
    fun importFromPlatform_persistsMergedSnapshot() = runTest(scheduler) {
        repository.lastSaved = seedProfile().copy(name = "原名", bio = "原简介")
        val v = vm()
        var done: UserProfileCacheEntity? = null

        v.importFromPlatform("GitHub 用户", "hello", "/avatar.webp") { done = it }
        advanceUntilIdle()

        assertThat(done?.name).isEqualTo("GitHub 用户")
        assertThat(done?.bio).isEqualTo("hello")
        assertThat(done?.avatarPath).isEqualTo("/avatar.webp")
        assertThat(repository.lastSaved?.name).isEqualTo("GitHub 用户")
    }
}
