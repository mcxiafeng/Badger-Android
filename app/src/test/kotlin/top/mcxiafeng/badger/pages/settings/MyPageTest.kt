package top.mcxiafeng.badger.pages.settings

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.AppTheme
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.data.AppearanceAccent
import top.mcxiafeng.badger.data.AppearanceMode
import top.mcxiafeng.badger.data.AppearancePreferences
import top.mcxiafeng.badger.data.AppearanceState
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.glass.GlassStyle
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.navigation.Route
import top.mcxiafeng.badger.ui.navigation.pushUnique
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
        listOf("名片夹管理", "标签管理", "关于 Badger").forEach {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(it))
            compose.onNodeWithText(it).assertIsDisplayed().performClick()
        }
        assertThat(opened).containsExactly("account", "collections", "tags", "about").inOrder()
        listOf("连接地址", "NFC 设置", "AI 配置", "UI 设置").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun myPage_exposesAppearanceEntryAndOptionsWithConsistentTouchTargets() {
        val appearance = mutableStateOf(AppearanceState(AppearanceMode.System, AppearanceAccent.Badger))
        val modes = mutableListOf<AppearanceMode>()
        compose.setContent {
            MiuixTheme {
                MyPage(
                    accountState = localAccount,
                    onSettings = {},
                    onAccount = {},
                    onManageCollections = {},
                    onManageTags = {},
                    onAbout = {},
                    appearanceState = AppearanceUiState(appearance.value),
                    onAppearanceModeChange = {
                        modes += it
                        appearance.value = appearance.value.copy(mode = it)
                    },
                )
            }
        }
        // 顶栏切换按钮在设置按钮左侧，点一下切换深浅并换图标。
        val toggle = compose.onNodeWithContentDescription("切换深色模式")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(BadgerSize.minimumTouchTarget)
            .assertHeightIsAtLeast(BadgerSize.minimumTouchTarget)
        val toggleBounds = toggle.fetchSemanticsNode().boundsInRoot
        val settingsBounds = compose.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot
        assertThat(toggleBounds.right).isAtMost(settingsBounds.left)
        toggle.performClick()
        compose.onNodeWithContentDescription("切换浅色模式").assertIsDisplayed()
        compose.runOnIdle { assertThat(modes).containsExactly(AppearanceMode.Dark) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("强调色"))
        compose.onNodeWithText("强调色")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(BadgerSize.minimumTouchTarget)
            .assertHeightIsAtLeast(BadgerSize.minimumTouchTarget)
        compose.onNodeWithText("强调色").performClick()
        compose.onNodeWithText("森林绿").assertIsDisplayed().performClick()
        compose.onNodeWithText("深浅模式").assertDoesNotExist()
    }

    @Test
    fun appearanceDialogs_useTheElevatedPanelBaseInLightAndDarkThemes() {
        val appearance = mutableStateOf(AppearanceState(mode = AppearanceMode.Light))
        compose.setContent {
            AppTheme(appearance.value) {
                CompositionLocalProvider(LocalGlassStyle provides GlassStyle(
                    enabled = true, dark = appearance.value.mode == AppearanceMode.Dark,
                )) {
                    MyPage(localAccount, {}, {}, {}, {}, {}, AppearanceUiState(appearance.value))
                }
            }
        }
        listOf(AppearanceMode.Light, AppearanceMode.Dark).forEach { mode ->
            compose.runOnIdle { appearance.value = appearance.value.copy(mode = mode) }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("强调色"))
            compose.onNodeWithText("强调色").performClick()
            compose.onNodeWithText("森林绿").assertIsDisplayed()
            compose.onNodeWithText("森林绿").performClick()
            compose.onNodeWithText("森林绿").assertDoesNotExist()
        }
    }

    @Test
    fun appearanceOptions_preserveSelectionAndCloseBeforeReopening() {
        val appearance = mutableStateOf(AppearanceState())
        val accents = mutableListOf<AppearanceAccent>()
        compose.setContent {
            MiuixTheme {
                AppearanceSettings(AppearanceUiState(appearance.value), onAccentChange = {
                    accents += it
                    appearance.value = appearance.value.copy(accent = it)
                })
            }
        }
        compose.onNodeWithText("强调色").performClick()
        compose.onNodeWithText("鸢尾蓝").assertIsSelected()
        compose.onNodeWithText("森林绿").performClick()
        compose.onNodeWithText("森林绿").assertDoesNotExist()
        compose.onNodeWithText("强调色").performClick()
        compose.onNodeWithText("森林绿").assertIsSelected().performClick()
        compose.onNodeWithText("森林绿").assertDoesNotExist()
        compose.runOnIdle {
            assertThat(accents).containsExactly(AppearanceAccent.Forest, AppearanceAccent.Forest).inOrder()
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
        listOf("NFC 设置", "AI 配置", "高德地图配置", "UI 设置").forEach {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(it))
            compose.onNodeWithText(it).performClick()
        }
        assertThat(pages).containsExactly(
            SettingsPageRoute.NfcSettings, SettingsPageRoute.AiOcr,
            SettingsPageRoute.Gaode, SettingsPageRoute.UiSettings,
        ).inOrder()
        compose.onNodeWithText("关于 Badger").assertDoesNotExist()
        compose.onNodeWithText("标签管理").assertDoesNotExist()
        compose.onNodeWithText("本地用户").assertDoesNotExist()
    }

    @Test
    fun settingsAndAbout_returnToMyPage() {
        lateinit var controller: NavController
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val uiSettings = UiSettingsViewModel(appContext, AppearancePreferences(appContext))
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
                        // This plain ComponentActivity has no Hilt graph; inject the real settings VM.
                        if (route.page == SettingsPageRoute.UiSettings) UiSettingsPage({ nav.pop() }, uiSettings)
                        else SettingsSubPage(route.page, { nav.pop() }, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText("连接地址")))
            .performScrollToNode(hasText("UI 设置"))
        compose.onNodeWithText("UI 设置").performClick()
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

    @Test
    fun repeatedNfcSettingsRequest_opensOnlyOnePageAndCanReopenAfterBack() {
        mockkObject(ShortLinkService)
        try {
            // Robolectric does not provide AndroidKeyStore. Use an unconfigured short-link account.
            every { ShortLinkService.getApiKey(any()) } returns ""
            verifyNfcNavigation()
        } finally {
            unmockkObject(ShortLinkService)
        }
    }

    private fun verifyNfcNavigation() {
        val nfcRoute = Route.SettingsSubPage(SettingsPageRoute.NfcSettings)
        lateinit var controller: NavController
        compose.setContent {
            MiuixTheme {
                val nav = rememberNavController<Route>(Route.MainTabs, Route.Settings)
                controller = nav
                NavDisplay(navController = nav) {
                    entry<Route.MainTabs> {}
                    entry<Route.Settings> {
                        SettingsPage(onBack = { nav.pop() }, onNavigateToSubPage = { page ->
                            // Reproduce two navigation requests before the next frame.
                            nav.pushUnique(Route.SettingsSubPage(page))
                            nav.pushUnique(Route.SettingsSubPage(page))
                        }, onNavigateToServerAddresses = {})
                    }
                    entry<Route.SettingsSubPage> { route ->
                        check(route == nfcRoute)
                        SettingsSubPage(route.page, { nav.pop() }, {})
                    }
                }
            }
        }
        repeat(2) {
            compose.onNodeWithText("NFC 设置").performClick()
            compose.runOnIdle {
                assertThat(controller.backStack.toList()).containsExactly(Route.MainTabs, Route.Settings, nfcRoute).inOrder()
            }
            clickVisibleBack()
            compose.runOnIdle {
                assertThat(controller.backStack.toList()).containsExactly(Route.MainTabs, Route.Settings).inOrder()
            }
        }
    }

    private fun clickVisibleBack() {
        // NavDisplay keeps covered pages composed, including their offscreen toolbar semantics.
        val buttons = compose.onAllNodesWithContentDescription("返回")
        buttons.fetchSemanticsNodes().indices.map { buttons[it] }
            .single { it.isDisplayed() }.performClick()
    }
}
