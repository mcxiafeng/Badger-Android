package top.mcxiafeng.badger.pages.social

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.ui.glass.GlassStyle
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-mdpi")
class SocialReloginWarningTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun localUserDoesNotSeeReloginWarning() {
        mountScreen(reloginWarning = false)
        composeRule.onNodeWithContentDescription("需要重新登录").assertDoesNotExist()
    }

    @Test
    fun expiredCloudSessionShowsWarningThatRequestsRelogin() {
        var reloginRequests = 0
        mountScreen(reloginWarning = true) { reloginRequests++ }
        composeRule.onNodeWithContentDescription("需要重新登录").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertThat(reloginRequests).isEqualTo(1) }
    }

    private fun mountScreen(reloginWarning: Boolean, onReauthenticate: () -> Unit = {}) {
        composeRule.setContent {
            MiuixTheme {
                // 液态关闭的扁平动作组分支；玻璃材质渲染由 GlassToolbarInteractionTest 覆盖。
                CompositionLocalProvider(
                    LocalGlassStyle provides GlassStyle(enabled = true, materialEnabled = false),
                ) {
                    SocialScreen(
                        uiState = SocialUiState(profile = UserProfile(name = "名字", bio = "简介")),
                        reloginWarning = reloginWarning,
                        onReauthenticate = onReauthenticate,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}
