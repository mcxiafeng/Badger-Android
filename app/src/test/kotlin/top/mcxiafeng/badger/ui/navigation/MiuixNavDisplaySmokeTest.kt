package top.mcxiafeng.badger.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavController

/**
 * Miuix Nav 运行时兼容性冒烟测试。
 *
 * 背景：miuix-nav 0.9.4 编译自 compose 1.12，而本项目把 compose 解析固定回 1.11.2
 * （见 app/build.gradle.kts 的 resolutionStrategy）。编译期无法发现 miuix-nav 对
 * compose 1.12 新 API 的静态调用（会在运行时抛 NoSuchMethodError），
 * 因此在 Robolectric 上真实渲染 NavDisplay 跑通 push/pop 转场管线作为回归门禁。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MiuixNavDisplaySmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Serializable
    private sealed class SmokeRoute : NavKey {
        @Serializable data object Home : SmokeRoute()
        @Serializable data class Detail(val id: Long) : SmokeRoute()
    }

    private var capturedController: NavController? = null

    @Test
    fun duplicateDestinationBelowTopDoesNotChangeStack() {
        val nfcRoute = Route.SettingsSubPage(SettingsPage.NfcSettings)
        val uiRoute = Route.SettingsSubPage(SettingsPage.UiSettings)
        val aiRoute = Route.SettingsSubPage(SettingsPage.AiOcr)
        val controller = NavController(navBackStackOf(Route.MainTabs, nfcRoute, uiRoute))

        controller.pushUnique(nfcRoute)
        controller.replaceUnique(nfcRoute)
        assertEquals(listOf(Route.MainTabs, nfcRoute, uiRoute), controller.backStack.toList())

        controller.replaceUnique(aiRoute)
        assertEquals(listOf(Route.MainTabs, nfcRoute, aiRoute), controller.backStack.toList())
    }

    @Test
    fun navDisplay_pushPop_runsOnPinnedCompose() {
        composeRule.setContent {
            val navController = rememberNavController<SmokeRoute>(SmokeRoute.Home)
            capturedController = navController
            NavDisplay(navController = navController) {
                entry<SmokeRoute.Home> {
                    TextButton(onClick = { navController.push(SmokeRoute.Detail(id = 1L)) }) {
                        Text("打开详情")
                    }
                }
                entry<SmokeRoute.Detail> { route ->
                    TextButton(onClick = { navController.pop() }) {
                        Text("详情${route.id}返回")
                    }
                }
            }
        }

        composeRule.onNodeWithText("打开详情").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("详情1返回").assertExists()

        composeRule.onNodeWithText("详情1返回").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("打开详情").assertExists()

        // pop 到栈底后栈中只剩 Home，pop() 不应继续出栈
        val stack = capturedController?.backStack
        checkNotNull(stack)
        check(stack.size == 1)
    }

    @Test
    fun navDisplay_onBackPopsTopEntry() {
        composeRule.setContent {
            val navController = rememberNavController<SmokeRoute>(SmokeRoute.Home)
            capturedController = navController
            NavDisplay(
                navController = navController,
                onBack = { navController.pop() },
            ) {
                entry<SmokeRoute.Home> { Text("主页") }
                entry<SmokeRoute.Detail> { route -> Text("详情${route.id}") }
            }
        }

        composeRule.runOnIdle { capturedController?.push(SmokeRoute.Detail(id = 2L)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("详情2").assertExists()

        composeRule.runOnIdle { capturedController?.pop() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("主页").assertExists()
    }
}
