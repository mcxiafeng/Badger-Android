package top.mcxiafeng.badger.domain

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.ShortLinkService

class SelectPlatformUseCaseTest {

    private lateinit var repository: UserProfileRepository
    private lateinit var shortLinkService: ShortLinkService
    private lateinit var context: Context
    private lateinit var useCase: SelectPlatformUseCase

    @Before
    fun setUp() {
        repository = mockk()
        shortLinkService = mockk()
        context = mockk(relaxed = true)
        useCase = SelectPlatformUseCase(repository, shortLinkService)

        coEvery { repository.getUserProfileOnce() } returns UserProfileCacheEntity(
            name = "Test",
            updateTime = 1L,
        )
        every { shortLinkService.isConfigured(context) } returns false
    }

    @Test
    fun `rapid selections persist every latest platform instead of dropping second selection`() = runTest {
        val first = PlatformEntry(jumpLink = "https://example.com/a", value = "a")
        val second = PlatformEntry(jumpLink = "https://example.com/b", value = "b")

        assertThat(useCase(context, "platformA", first)).isEqualTo(LinkUpdateResult.NO_CONFIG)
        assertThat(useCase(context, "platformB", second)).isEqualTo(LinkUpdateResult.NO_CONFIG)

        coVerify(exactly = 1) {
            repository.saveUserProfile(match { it.defaultPlatform == "platformA" })
        }
        coVerify(exactly = 1) {
            repository.saveUserProfile(match { it.defaultPlatform == "platformB" })
        }
    }
}
