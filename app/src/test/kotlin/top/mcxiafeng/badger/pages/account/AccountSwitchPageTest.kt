package top.mcxiafeng.badger.pages.account

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.account.AccountScope
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountSwitchPageTest {
    @get:Rule val compose = createComposeRule()
    private val local = AccountScope.local("local-test")
    private val current = AccountScope.remote("s", "a", "e", "账号甲", "https://current.example")
    private val target = AccountScope.remote("s", "b", "e", "历史账号", "https://history.example")
    private val initial = AccountSwitchUiState(current, listOf(local, current, target))

    @Test fun selectHistoryShowsVerification() {
        var state by mutableStateOf(initial)
        var submitted = false
        compose.setContent { MiuixTheme {
            AccountSwitchScreen(state, AccountSwitchCallbacks(
                onSelect = { key -> state = state.copy(selected = state.accounts.first { it.key == key }) },
                onSubmit = { submitted = true }))
        } }
        compose.onNodeWithText(current.username).assertHasNoClickAction()
        compose.onNodeWithText("当前使用").assertExists()
        scrollTo("历史账号")
        compose.onNodeWithText("历史账号").performClick()
        compose.onNodeWithText(target.username).assertExists()
        compose.onNodeWithText("https://history.example").assertExists()
        compose.onNodeWithText("登录并切换").assertIsNotEnabled()
        assertThat(submitted).isFalse()
    }

    @Test fun passwordVisibilityAndSubmit() {
        var state by mutableStateOf(initial.copy(selected = target))
        var submitted = false
        compose.setContent { MiuixTheme {
            AccountSwitchScreen(state, AccountSwitchCallbacks(
                onPassword = { state = state.copy(password = it) },
                onTogglePassword = { state = state.copy(passwordVisible = !state.passwordVisible) },
                onSubmit = { submitted = true }))
        } }
        compose.onNodeWithContentDescription("账号密码").performTextInput("synthetic-password")
        compose.onNodeWithContentDescription("显示密码").performClick()
        compose.onNodeWithContentDescription("隐藏密码").assertExists()
        compose.onNodeWithContentDescription("账号密码").assertTextContains("synthetic-password")
        compose.onNodeWithContentDescription("隐藏密码").performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).assertExists()
        scrollTo("登录并切换")
        compose.onNodeWithText("登录并切换").performClick()
        assertThat(submitted).isTrue()
    }

    @Test fun localConfirmationHasCancelAndConfirm() {
        var state by mutableStateOf(initial)
        var confirmed = false
        compose.setContent { MiuixTheme {
            AccountSwitchScreen(state, AccountSwitchCallbacks(
                onSelect = { state = state.copy(showLocalConfirmation = true) },
                onDismissLocal = { state = state.copy(showLocalConfirmation = false) },
                onConfirmLocal = { state = state.copy(showLocalConfirmation = false); confirmed = true }))
        } }
        compose.onNodeWithText("本地用户").performClick()
        compose.onNodeWithText("切换到本地用户？").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("切换到本地用户？").assertDoesNotExist()
        assertThat(confirmed).isFalse()
        compose.onNodeWithText("本地用户").performClick()
        compose.onNodeWithText("切换").performClick()
        compose.onNodeWithText("切换到本地用户？").assertDoesNotExist()
        assertThat(confirmed).isTrue()
    }

    @Test fun busyCanReturnAndCannotResubmit() {
        var returned = false
        compose.setContent { MiuixTheme {
            AccountSwitchScreen(initial.copy(selected = target, password = "synthetic-password", busy = true),
                AccountSwitchCallbacks(onBack = { returned = true }))
        } }
        scrollTo("正在验证…")
        compose.onNodeWithText("正在验证…").assertIsNotEnabled()
        compose.onNodeWithContentDescription("返回").performClick()
        assertThat(returned).isTrue()
    }

    @Test fun largeFontKeepsLongIdentityAndActions() {
        val longTarget = target.copy(username = "很长的完整账号名称用于核对正在切换的用户身份",
            baseUrl = "https://history.example/very-long-server-address/for-this-account")
        var newLogin = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MiuixTheme { AccountSwitchScreen(initial.copy(selected = longTarget,
                    error = "账号资料已变化，请选择登录其他账号重新登录。"),
                    AccountSwitchCallbacks(onLoginNewAccount = { newLogin = true })) }
            }
        }
        compose.onNodeWithText(longTarget.username).assertExists()
        compose.onNodeWithText(longTarget.baseUrl).assertExists()
        scrollTo("登录其他账号")
        compose.onNodeWithText("登录其他账号").performClick()
        assertThat(newLogin).isTrue()
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }
}
