package top.mcxiafeng.badger.pages.social

import android.app.Application
import androidx.activity.ComponentDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import top.mcxiafeng.badger.ui.glass.RetainedPopupHost
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NfcWriteDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun preparingAndReadyStillBlockBackWhileSuccessAllowsDismissal() {
        val state = mutableStateOf(NfcWriteState.PREPARING)
        var dismissed = 0
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    NfcWriteDialog(state.value, null, null, true,
                        onDismiss = { dismissed++ }, onRetry = {}, onOpenNfcSettings = {}, onOpenShortLinkSettings = {})
                }
            }
        }
        compose.onNodeWithText("NFC 标签写入").assertExists()
        val dialog = compose.runOnIdle { checkNotNull(ShadowDialog.getLatestDialog()) as ComponentDialog }
        compose.runOnIdle { dialog.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertThat(dismissed).isEqualTo(0); state.value = NfcWriteState.READY }
        compose.runOnIdle { dialog.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertThat(dismissed).isEqualTo(0); state.value = NfcWriteState.SUCCESS }
        compose.onNodeWithText("写入成功").assertExists()
        compose.runOnIdle { dialog.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertThat(dismissed).isEqualTo(1) }
    }

    @Test
    fun unconfiguredErrorKeepsSettingsAndCloseActionsIndependent() {
        var dismissed = 0
        var retries = 0
        var settings = 0
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    NfcWriteDialog(NfcWriteState.ERROR, "链接未配置", null, true, isShortLinkConfigured = false,
                        onDismiss = { dismissed++ }, onRetry = { retries++ }, onOpenNfcSettings = {},
                        onOpenShortLinkSettings = { settings++ })
                }
            }
        }
        compose.onNodeWithText("去设置").performClick()
        compose.runOnIdle { assertThat(settings).isEqualTo(1); assertThat(retries).isEqualTo(0) }
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { assertThat(dismissed).isEqualTo(1) }
    }
}
