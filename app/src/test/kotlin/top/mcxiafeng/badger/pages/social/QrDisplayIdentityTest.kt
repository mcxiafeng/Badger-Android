package top.mcxiafeng.badger.pages.social

import android.app.Application
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.domain.PlatformProfile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class QrDisplayIdentityTest {
    @Test
    fun savedNicknameWinsAndBlankNicknameAllowsFetchedNameThenGlobalName() {
        val state = SocialUiState(
            profile = UserProfile(name = "个人资料名字"),
            platforms = listOf("qq" to PlatformEntry(value = "123456789", displayName = " 自设名字 ")),
            platformProfiles = mapOf("qq" to PlatformProfile("抓取的用户名", null)),
        )
        assertThat(state.qrUserNameFor("qq")).isEqualTo("自设名字")
        val withoutSaved = state.copy(platforms = listOf("qq" to PlatformEntry(value = "123456789", displayName = "  ")))
        assertThat(withoutSaved.qrUserNameFor("qq")).isEqualTo("抓取的用户名")
        assertThat(withoutSaved.copy(platformProfiles = emptyMap()).qrUserNameFor("qq")).isEqualTo("个人资料名字")
    }

    @Test
    fun missingNamesDoNotFallBackToPlatformOrAccountAndSyntheticQqNameDoesNotHideGlobalName() {
        val state = SocialUiState(platforms = listOf("qq" to PlatformEntry(value = "123456789")))
        assertThat(state.qrUserNameFor("qq")).isNull()
        assertThat(state.qrUserNameFor("bilibili")).isNull()
        val synthetic = state.copy(profile = UserProfile(name = "手动名字"),
            platformProfiles = mapOf("qq" to PlatformProfile("QQ用户123456789", null)))
        assertThat(synthetic.qrUserNameFor("qq")).isEqualTo("手动名字")
    }

    @Test
    fun captionUsesSelectedSharingAccountAndRefreshesWithFetchedProfile() {
        val state = SocialUiState(
            platforms = listOf("qq" to PlatformEntry(value = "123"), "bilibili" to PlatformEntry(value = "456")),
            platformProfiles = mapOf("qq" to PlatformProfile("QQ用户昵称", null), "bilibili" to PlatformProfile("B站昵称", null)),
        )
        assertThat(state.qrUserNameFor("bilibili")).isEqualTo("B站昵称")
        val updated = state.copy(platformProfiles = state.platformProfiles + ("bilibili" to PlatformProfile("更新昵称", null)))
        assertThat(updated.qrUserNameFor("bilibili")).isEqualTo("更新昵称")
    }
}
