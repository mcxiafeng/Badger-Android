package top.mcxiafeng.badger.data.dao

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.UserProfileDao
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UserProfileDaoTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule(org.robolectric.RuntimeEnvironment.getApplication())

    private lateinit var dao: UserProfileDao

    @Before
    fun setup() {
        dao = dbRule.db.userProfileDao()
    }

    @Test
    fun getProfile_initialSeed_returnsDefaultProfile() = runTest {
        val profile = dao.getProfile().first()
        assertThat(profile).isNotNull()
        assertThat(profile!!.name).isEqualTo("用户")
    }

    @Test
    fun saveProfile_replaceOnConflict() = runTest {
        dao.saveProfile(UserProfile(id = 1L, name = "新名字"))
        val profile = dao.getProfile().first()
        assertThat(profile!!.name).isEqualTo("新名字")
    }

    @Test
    fun getProfileOnce_returnsProfile() = runTest {
        val profile = dao.getProfileOnce()
        assertThat(profile).isNotNull()
    }

    @Test
    fun saveProfile_updatesExistingProfile() = runTest {
        dao.saveProfile(UserProfile(id = 1L, name = "初始名字"))
        dao.saveProfile(UserProfile(id = 1L, name = "更新名字", bio = "新签名"))
        val updated = dao.getProfileOnce()
        assertThat(updated!!.name).isEqualTo("更新名字")
        assertThat(updated.bio).isEqualTo("新签名")
    }

    @Test
    fun saveProfile_withPlatforms_roundTrip() = runTest {
        val platforms = mapOf(
            "qq" to PlatformEntry(displayName = "QQ", jumpLink = "https://qq.com/123", value = "123")
        )
        dao.saveProfile(UserProfile(id = 1L, name = "测试", platforms = platforms))
        val profile = dao.getProfileOnce()
        assertThat(profile!!.platforms).hasSize(1)
        assertThat(profile.platforms!!["qq"]?.value).isEqualTo("123")
    }
}