package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.network.ServerApi
import java.io.IOException

/**
 * [Phase 3] UserProfileRepositoryImpl 直推版单元测试。
 *
 * 覆盖新直推语义（`PUT /api/user/profile`）：
 * - saveUserProfile dirty → 直推；无变化 → 跳过
 * - updatePlatformField / removePlatform / updateAvatarPath → 本地落库 + 直推
 * - 直推失败不阻塞本地保存（有日志降级）
 */
class UserProfileRepositoryImplTest {

    private lateinit var userProfileCacheDao: UserProfileCacheDao
    private lateinit var serverApi: ServerApi
    private lateinit var repository: UserProfileRepositoryImpl

    @Before
    fun setup() {
        userProfileCacheDao = mockk(relaxed = true)
        serverApi = mockk(relaxed = true)
        repository = UserProfileRepositoryImpl(userProfileCacheDao, serverApi)
    }

    private fun profile(
        name: String = "用户",
        platformsJson: String = "{}",
        avatarPath: String? = null,
        bio: String? = null,
        sex: String? = null,
        country: String? = null,
        region: String? = null,
        birthday: String? = null,
        backgroundURL: String? = null,
        extra: String? = null,
    ) = UserProfileCacheEntity(
        id = 1L,
        name = name,
        avatarPath = avatarPath,
        bio = bio,
        platformsJson = platformsJson,
        updateTime = 1000L,
        sex = sex,
        country = country,
        region = region,
        birthday = birthday,
        backgroundURL = backgroundURL,
        extra = extra,
    )

    // ============ saveUserProfile — dirty → 直推 ============

    @Test
    fun saveUserProfile_dirty_pushesProfile() = runTest {
        coEvery { userProfileCacheDao.getProfileOnce() } returns profile(name = "旧名")

        repository.saveUserProfile(profile(name = "新名"))

        coVerify { userProfileCacheDao.saveProfile(any()) }
        coVerify { userProfileCacheDao.bumpProfile() }
        coVerify { serverApi.patchProfile(eq("新名"), any()) }
    }

    // ============ saveUserProfile — 无变化 → 跳过推送 ============

    @Test
    fun saveUserProfile_noChange_skipsPush() = runTest {
        val same = profile(name = "用户")
        coEvery { userProfileCacheDao.getProfileOnce() } returns same

        repository.saveUserProfile(same)

        coVerify(exactly = 0) { serverApi.patchProfile(any(), any()) }
    }

    // ============ updatePlatformField — 加平台 → contactMap 直推 ============

    @Test
    fun updatePlatformField_addsToContactMap_andPushes() = runTest {
        coEvery { userProfileCacheDao.getProfileOnce() } returns profile()

        repository.updatePlatformField("qq", jumpLink = "https://tool.gljlw.com/qq/?qq=123", value = "123")

        coVerify { userProfileCacheDao.saveProfile(any()) }
        coVerify {
            serverApi.patchProfile(null, match { it.contactMap == mapOf("qq" to "123") })
        }
    }

    // ============ updatePlatformField — 空值 → 移除 ============

    @Test
    fun updatePlatformField_blankValue_removesKey() = runTest {
        // [修复防御]: 模拟真实 cache —— saveProfile 后 getProfileOnce 读到最新值
        var current = profile()
        coEvery { userProfileCacheDao.getProfileOnce() } coAnswers { current }
        coEvery { userProfileCacheDao.saveProfile(any()) } coAnswers { current = firstArg() }

        // 先加一个平台，再清空
        repository.updatePlatformField("qq", jumpLink = "https://x", value = "123")
        repository.updatePlatformField("qq", jumpLink = "", value = null)

        coVerify {
            serverApi.patchProfile(null, match { it.contactMap.isEmpty() })
        }
    }

    // ============ removePlatform ============

    @Test
    fun removePlatform_removesFromContactMap_andPushes() = runTest {
        // [修复防御]: 模拟真实 cache —— saveProfile 后 getProfileOnce 读到最新值
        var current = profile()
        coEvery { userProfileCacheDao.getProfileOnce() } coAnswers { current }
        coEvery { userProfileCacheDao.saveProfile(any()) } coAnswers { current = firstArg() }

        repository.updatePlatformField("qq", jumpLink = "https://x", value = "123")
        repository.removePlatform("qq")

        coVerify {
            serverApi.patchProfile(null, match { it.contactMap.isEmpty() })
        }
    }

    @Test
    fun removePlatform_missingKey_skipsPush() = runTest {
        coEvery { userProfileCacheDao.getProfileOnce() } returns profile()

        repository.removePlatform("not_exist")

        coVerify(exactly = 0) { serverApi.patchProfile(any(), any()) }
    }

    // ============ updateAvatarPath → avatarURL 直推 ============

    @Test
    fun updateAvatarPath_pushesAvatarUrl() = runTest {
        coEvery { userProfileCacheDao.getProfileOnce() } returns profile(avatarPath = null)

        repository.updateAvatarPath("/data/avatar.webp")

        coVerify { serverApi.patchProfile(null, match { it.avatarURL == "/data/avatar.webp" }) }
    }

    @Test
    fun updateAvatarPath_samePath_skipsPush() = runTest {
        coEvery { userProfileCacheDao.getProfileOnce() } returns profile(avatarPath = "/a.webp")

        repository.updateAvatarPath("/a.webp")

        coVerify(exactly = 0) { serverApi.patchProfile(any(), any()) }
    }

    // ============ 直推失败 → 本地已保存不崩 ============

    @Test
    fun saveUserProfile_pushFails_keepsLocalState() = runTest {
        coEvery { userProfileCacheDao.getProfileOnce() } returns profile(name = "旧名")
        coEvery { serverApi.patchProfile(any(), any()) } throws IOException("offline")

        repository.saveUserProfile(profile(name = "新名"))

        coVerify { userProfileCacheDao.saveProfile(any()) }
    }

    // ============ [Phase 2] saveUserProfile — 新字段变化 → 直推 ============

    @Test
    fun saveUserProfile_newFieldsDirty_pushesProfile() = runTest {
        coEvery { userProfileCacheDao.getProfileOnce() } returns profile()

        repository.saveUserProfile(profile(sex = "male", country = "CN", region = "Beijing", birthday = "2000-01-01"))

        coVerify { serverApi.patchProfile(any(), match {
            it.sex == "male" && it.country == "CN" && it.region == "Beijing" && it.birthday == "2000-01-01"
        }) }
    }

    @Test
    fun saveUserProfile_backgroundURLDirty_pushesProfile() = runTest {
        coEvery { userProfileCacheDao.getProfileOnce() } returns profile()

        repository.saveUserProfile(profile(backgroundURL = "https://example.com/bg.jpg"))

        coVerify { serverApi.patchProfile(any(), match { it.backgroundURL == "https://example.com/bg.jpg" }) }
    }

    @Test
    fun saveUserProfile_extraDirty_pushesProfile() = runTest {
        coEvery { userProfileCacheDao.getProfileOnce() } returns profile()

        repository.saveUserProfile(profile(extra = """{"key":{"nested":"value"}}"""))

        coVerify { serverApi.patchProfile(any(), match {
            it.extra != null && it.extra!!.getAsJsonObject("key")?.get("nested")?.asString == "value"
        }) }
    }
}
