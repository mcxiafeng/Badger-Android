package top.mcxiafeng.badger.pages.server

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ServerAddressesPageTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsCurrentAddressWithoutAlternatives() {
        val state = MutableStateFlow(ServerAddressesUiState())
        val viewModel = mockk<ServerAddressesViewModel> {
            every { uiState } returns state
        }
        compose.setContent {
            MiuixTheme { ServerAddressesPage(onBack = {}, viewModel = viewModel) }
        }
        compose.onNodeWithText("还没有连接地址").assertIsDisplayed()
        compose.onNodeWithText("当前地址").assertDoesNotExist()

        val currentUrl = "https://badger.example.com:51026"
        compose.runOnIdle { state.value = ServerAddressesUiState(activeBase = currentUrl) }
        compose.onNodeWithText("当前地址").assertIsDisplayed()
        compose.onNodeWithText(currentUrl).assertIsDisplayed()
        compose.onNodeWithText("还没有备用地址").assertIsDisplayed()
        compose.onNodeWithText("还没有连接地址").assertDoesNotExist()
    }
}
