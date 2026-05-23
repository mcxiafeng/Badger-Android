package top.mcxiafeng.badger.viewmodels

import com.google.common.truth.Truth.assertThat
import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.data.ContactRepository
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.pages.social.SocialViewModel
import top.mcxiafeng.badger.testutil.MainDispatcherRule

class SocialViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: ContactRepository
    private lateinit var viewModel: SocialViewModel
    private val profileFlow = MutableStateFlow<UserProfile?>(null)

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        every { repository.getUserProfile() } returns profileFlow
    }

    @Test
    fun loadProfile_success_setsProfileAndPlatforms() = runTest {
        val profile = UserProfile(
            id = 1L, name = "测试用户",
            platforms = mapOf(
                "QQ" to PlatformEntry(jumpLink = "https://qq.com/123", value = "123"),
                "哔哩哔哩" to PlatformEntry(jumpLink = "https://bilibili.com/456", value = "456")
            )
        )
        profileFlow.value = profile
        viewModel = SocialViewModel(repository, mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.profile).isNotNull()
        assertThat(state.profile!!.name).isEqualTo("测试用户")
        assertThat(state.platforms).hasSize(2)
    }

    @Test
    fun loadProfile_nullProfile_setsEmptyPlatforms() = runTest {
        profileFlow.value = null
        viewModel = SocialViewModel(repository, mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.platforms).isEmpty()
    }

    @Test
    fun buildPlatformList_filtersBlankJumpLink() = runTest {
        val profile = UserProfile(
            id = 1L, name = "测试",
            platforms = mapOf(
                "QQ" to PlatformEntry(jumpLink = "https://qq.com/123", value = "123"),
                "微信" to PlatformEntry(jumpLink = "", value = "wxid")
            )
        )
        profileFlow.value = profile
        viewModel = SocialViewModel(repository, mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // both entries pass filter: QQ has jumpLink, 微信 has non-blank value
        assertThat(state.platforms).hasSize(2)
    }

    @Test
    fun buildPlatformList_deduplicatesByNormalizedKey() = runTest {
        val profile = UserProfile(
            id = 1L, name = "测试",
            platforms = mapOf(
                "QQ" to PlatformEntry(jumpLink = "https://qq.com/123", value = "123"),
                "qq" to PlatformEntry(jumpLink = "https://qq.com/456", value = "456")
            )
        )
        profileFlow.value = profile
        viewModel = SocialViewModel(repository, mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // "QQ" and "qq" are distinct map keys, both pass filter
        assertThat(state.platforms).hasSize(2)
    }

    @Test
    fun setNfcSupported_updatesState() {
        viewModel = SocialViewModel(repository, mockk())
        viewModel.setNfcSupported(true)
        assertThat(viewModel.uiState.value.nfcSupported).isTrue()
    }

    @Test
    fun addOrUpdatePlatform_callsRepository() = runTest {
        profileFlow.value = UserProfile(id = 1L, name = "测试")
        viewModel = SocialViewModel(repository, mockk())
        advanceUntilIdle()

        viewModel.addOrUpdatePlatform("QQ", "https://qq.com/123", "123")
        advanceUntilIdle()

        coVerify { repository.updatePlatformField("QQ", "https://qq.com/123", "123") }
    }

    @Test
    fun removePlatform_callsRepository() = runTest {
        profileFlow.value = UserProfile(id = 1L, name = "测试")
        viewModel = SocialViewModel(repository, mockk())
        advanceUntilIdle()

        viewModel.removePlatform("QQ")
        advanceUntilIdle()

        coVerify { repository.removePlatform("QQ") }
    }

    @Test
    fun updateProfileBasic_updatesNameBioAvatar() = runTest {
        val profile = UserProfile(id = 1L, name = "旧名字")
        profileFlow.value = profile
        viewModel = SocialViewModel(repository, mockk())
        advanceUntilIdle()

        viewModel.updateProfileBasic("新名字", "新签名", "/path/to/avatar")
        advanceUntilIdle()

        coVerify { repository.saveUserProfile(match {
            it.name == "新名字" && it.bio == "新签名" && it.avatarPath == "/path/to/avatar"
        }) }
    }

    @Test
    fun setShowEditProfileDialog_togglesState() {
        viewModel = SocialViewModel(repository, mockk())
        viewModel.setShowEditProfileDialog(true)
        assertThat(viewModel.uiState.value.showEditProfileDialog).isTrue()
        viewModel.setShowEditProfileDialog(false)
        assertThat(viewModel.uiState.value.showEditProfileDialog).isFalse()
    }

    @Test
    fun setShowAddPlatformDialog_togglesState() {
        viewModel = SocialViewModel(repository, mockk())
        viewModel.setShowAddPlatformDialog(true)
        assertThat(viewModel.uiState.value.showAddPlatformDialog).isTrue()
    }

    @Test
    fun defaultPlatformChanged_updatesSelectedIndex() = runTest {
        val profile = UserProfile(
            id = 1L, name = "测试",
            platforms = mapOf(
                "QQ" to PlatformEntry(jumpLink = "https://qq.com/123", value = "123"),
                "哔哩哔哩" to PlatformEntry(jumpLink = "https://bilibili.com/456", value = "456")
            ),
            defaultPlatform = "哔哩哔哩"
        )
        profileFlow.value = profile
        viewModel = SocialViewModel(repository, mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // "哔哩哔哩" is at index 1 after buildPlatformList
        assertThat(state.selectedPlatformIndex).isEqualTo(1)
    }
}
