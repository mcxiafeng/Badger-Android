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

/**
 * [A5] 字段级更新 + [A6] 平台导入合并 单元测试。
 *
 * 覆盖：
 * 1. sex/birthday/country/region/backgroundURL 各自正确映射且只改目标字段
 * 2. 空字符串被折叠为 null（与 UI 清除语义一致）
 * 3. 未知 fieldKey → 不落库（onDone 不被调用，无副作用）
 * 4. 连续字段写入基于最新快照累加，不互相覆盖
 * 5. [A6] mergeImportedProfile 仅覆盖非空解析字段，"未知" 昵称过滤
 *
 * [修复防御]: ViewModel `withContext(ioDispatcher)` 通过构造器注入测试调度器，
 * Fake 仓库自身持有最新快照，不再依赖 DAO stub。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UserProfileDetailViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)
    private lateinit var repository: RecordingUserProfileRepository

    /** 内存 Fake：get/save 读写同一份快照，模拟真实 cache。 */
    class RecordingUserProfileRepository(
        initial: UserProfileCacheEntity? = null,
    ) : UserProfileRepository {
        var lastSaved: UserProfileCacheEntity? = initial
        override fun getUserProfile() = kotlinx.coroutines.flow.flowOf(lastSaved)
        override suspend fun getUserProfileOnce(): UserProfileCacheEntity? = lastSaved
        override suspend fun saveUserProfile(profile: UserProfileCacheEntity) {
            lastSaved = profile
        }
        override suspend fun updateAvatarPath(avatarPath: String?) {}
        override suspend fun updatePlatformField(
            fieldKey: String, jumpLink: String, value: String?, displayName: String?, avatarUrl: String?, originalLink: String?
        ) {}
        override suspend fun removePlatform(platformName: String) {}
    }

    @Before
    fun setUp() {
        // [修复防御]: viewModelScope 走 Dispatchers.Main，withContext 走 ioDispatcher，
        // 两者必须共享同一 TestCoroutineScheduler，否则 advanceUntilIdle 驱动不到 IO 块。
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
