package top.mcxiafeng.badger.pages.settings

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.navigation.Route
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MyPageTest {
    @get:Rule val compose = createComposeRule()
    private val localAccount = SettingsAccountState.from(null)

    @OptIn(DelicateCoilApi::class)
    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    @Test
    fun myPage_routesAccountCollectionsTagsAndAbout() {
        val opened = mutableListOf<String>()
        compose.setContent {
            MiuixTheme {
                MyPage(
                    accountState = localAccount,
                    onSettings = {},
                    onAccount = { opened += "account" },
                    onManageCollections = { opened += "collections" },
                    onManageTags = { opened += "tags" },
                    onAbout = { opened += "about" },
                )
            }
        }
        compose.onNodeWithText("本地用户").performClick()
        listOf("管理名片夹", "标签管理", "关于 Badger").forEach {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(it))
            compose.onNodeWithText(it).assertIsDisplayed().performClick()
        }
        assertThat(opened).containsExactly("account", "collections", "tags", "about").inOrder()
        listOf("连接地址", "NFC 设置", "AI 配置", "UI 设置").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun myPage_refreshesAccountAndKeepsSettingsAccessibleAtLargeFont() {
        val state = mutableStateOf(localAccount)
        var settingsOpened = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MiuixTheme { MyPage(state.value, { settingsOpened = true }, {}, {}, {}, {}) }
            }
        }
        compose.runOnIdle {
            state.value = SettingsAccountState(true, "当前账号", "已登录", "当")
        }
        compose.onNodeWithText("本地用户").assertDoesNotExist()
        compose.onNodeWithText("当前账号").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(BadgerSize.minimumTouchTarget)
            .assertHeightIsAtLeast(BadgerSize.minimumTouchTarget)
            .performClick()
        assertThat(settingsOpened).isTrue()
    }

    @Test
    fun settingsPage_routesConfigurationEntriesWithoutAbout() {
        var serverOpened = false
        val pages = mutableListOf<SettingsPageRoute>()
        val serverUrl = mutableStateOf<String?>(null)
        compose.setContent {
            MiuixTheme { SettingsPage({}, { pages += it }, { serverOpened = true }, serverUrl.value) }
        }
        compose.onNodeWithText("尚未配置服务器").assertIsDisplayed()
        val configuredUrl = "https://badger.example.com:51026"
        compose.runOnIdle { serverUrl.value = configuredUrl }
        compose.onNodeWithText(configuredUrl).assertIsDisplayed()
        compose.onNodeWithText("连接地址").performClick()
        assertThat(serverOpened).isTrue()
        listOf("NFC 设置", "AI 配置", "UI 设置").forEach {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(it))
            compose.onNodeWithText(it).performClick()
        }
        assertThat(pages).containsExactly(
            SettingsPageRoute.NfcSettings, SettingsPageRoute.AiOcr,
            SettingsPageRoute.UiSettings,
        ).inOrder()
        compose.onNodeWithText("关于 Badger").assertDoesNotExist()
        compose.onNodeWithText("标签管理").assertDoesNotExist()
        compose.onNodeWithText("本地用户").assertDoesNotExist()
    }

    @Test
    fun settingsAndAbout_returnToMyPage() {
        lateinit var controller: NavController
        compose.setContent {
            MiuixTheme {
                val nav = rememberNavController<Route>(Route.MainTabs)
                controller = nav
                NavDisplay(navController = nav, onBack = { nav.pop() }) {
                    entry<Route.MainTabs> {
                        MyPage(
                            accountState = localAccount,
                            onSettings = { nav.push(Route.Settings) },
                            onAccount = {},
                            onManageCollections = {},
                            onManageTags = {},
                            onAbout = { nav.push(Route.SettingsSubPage(SettingsPageRoute.About)) },
                        )
                    }
                    entry<Route.Settings> {
                        SettingsPage({ nav.pop() }, { nav.push(Route.SettingsSubPage(it)) }, {})
                    }
                    entry<Route.SettingsSubPage> { route ->
                        SettingsSubPage(route.page, { nav.pop() }, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("UI 设置").performScrollTo().performClick()
        compose.onNodeWithText("悬浮导航栏").assertExists()
        clickVisibleBack()
        compose.onNodeWithText("连接地址").assertExists()
        clickVisibleBack()
        compose.onNodeWithText("本地用户").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("关于 Badger"))
        compose.onNodeWithText("关于 Badger").performClick()
        compose.onNodeWithText("Badger").assertIsDisplayed()
        clickVisibleBack()
        compose.onNodeWithText("关于 Badger").assertIsDisplayed()
        compose.runOnIdle { assertThat(controller.backStack.toList()).containsExactly(Route.MainTabs) }
    }

    private fun clickVisibleBack() {
        // NavDisplay keeps covered pages composed, including their offscreen toolbar semantics.
        val buttons = compose.onAllNodesWithContentDescription("返回")
        buttons.fetchSemanticsNodes().indices.map { buttons[it] }
            .single { it.isDisplayed() }.performClick()
    }
}
