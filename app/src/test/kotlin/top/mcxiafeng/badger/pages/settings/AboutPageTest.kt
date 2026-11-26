package top.mcxiafeng.badger.pages.settings

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.BuildConfig
import top.mcxiafeng.badger.data.isDeveloperMode
import top.mcxiafeng.badger.data.setDeveloperMode
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AboutPageTest {
    @get:Rule val compose = createComposeRule()
    private val application: Application get() = RuntimeEnvironment.getApplication()

    @After
    fun resetDeveloperMode() {
        setDeveloperMode(application, false)
    }

    @Test
    fun aboutGroups_haveHeadingsAndShowRealBuildInformation() {
        showAbout()
        compose.onNodeWithContentDescription("Badger 标志").assertIsDisplayed()
        compose.onNodeWithText("Badger").assertIsDisplayed()
        listOf("版本与构建", "贡献者", "项目与支持").forEach { title ->
            scrollTo(title)
            compose.onNodeWithText(title)
                .assertIsDisplayed()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
        scrollTo("版本号")
        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertExists()
        compose.onNodeWithText("构建日期").assertHasNoClickAction()
        compose.onNodeWithText(BuildConfig.BUILD_DATE).assertExists()
        compose.onNodeWithText("数据库版本").assertDoesNotExist()
        scrollTo("夏枫大笨喵w")
        compose.onNodeWithText("请输入文本").assertDoesNotExist()
    }

    @Test
    fun pullingPastTop_stretchesContentAndSettlesWithoutMovingBackButton() {
        showAbout()
        val brand = compose.onNodeWithText("Badger")
        val back = compose.onNodeWithContentDescription("返回")
        val brandTop = brand.fetchSemanticsNode().boundsInRoot.top
        val backBounds = back.fetchSemanticsNode().boundsInRoot
        val list = compose.onNode(hasScrollToIndexAction())
        list.performTouchInput {
            down(center)
            moveBy(Offset(0f, height * FIRST_PULL_FRACTION))
            moveBy(Offset(0f, height * SECOND_PULL_FRACTION))
        }
        assertThat(brand.fetchSemanticsNode().boundsInRoot.top).isGreaterThan(brandTop)
        assertThat(back.fetchSemanticsNode().boundsInRoot).isEqualTo(backBounds)
        list.performTouchInput { up() }
        compose.waitForIdle()
        assertThat(brand.fetchSemanticsNode().boundsInRoot.top).isWithin(POSITION_TOLERANCE_PX).of(brandTop)
        assertThat(back.fetchSemanticsNode().boundsInRoot).isEqualTo(backBounds)
    }

    @Test
    fun seventhVersionTap_enablesDeveloperModeAndKeepsLogEntry() {
        val pages = mutableListOf<SettingsPageRoute>()
        showAbout(onNavigate = { pages += it })
        scrollTo("版本号")
        tapVersion(PRE_UNLOCK_TAP_COUNT)
        compose.onNodeWithText("已点击 6/7").assertExists()
        compose.runOnIdle { assertThat(isDeveloperMode(application)).isFalse() }
        tapVersion()
        compose.runOnIdle { assertThat(isDeveloperMode(application)).isTrue() }
        compose.onNodeWithText("开发者模式已打开").assertExists()
        scrollTo("软件日志")
        compose.onNodeWithText("软件日志").performClick()
        assertThat(pages).containsExactly(SettingsPageRoute.AppLog)
    }

    @Test
    fun idleTimeout_resetsProgressAndRequiresSevenFreshTaps() {
        showAbout()
        scrollTo("版本号")
        tapVersion(PROGRESS_TAP_COUNT)
        compose.onNodeWithText("已点击 4/7").assertExists()
        compose.mainClock.advanceTimeBy(PAST_TAP_TIMEOUT_MS)
        compose.onNodeWithText("已点击 4/7").assertDoesNotExist()
        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertExists()
        tapVersion(TAPS_AFTER_TIMEOUT)
        compose.runOnIdle { assertThat(isDeveloperMode(application)).isFalse() }
        tapVersion(PROGRESS_TAP_COUNT)
        compose.runOnIdle { assertThat(isDeveloperMode(application)).isTrue() }
    }

    @Test
    fun eachVersionTap_restartsTheIdleTimeout() {
        showAbout()
        scrollTo("版本号")
        tapVersion(PROGRESS_TAP_COUNT)
        compose.mainClock.advanceTimeBy(WITHIN_TAP_TIMEOUT_MS)
        tapVersion()
        compose.mainClock.advanceTimeBy(WITHIN_TAP_TIMEOUT_MS)
        compose.onNodeWithText("已点击 5/7").assertExists()
        tapVersion(REMAINING_TAPS)
        compose.runOnIdle { assertThat(isDeveloperMode(application)).isTrue() }
    }

    @Test
    fun developerSwitch_disablesModeAndHidesLogEntry() {
        showAbout(initialDevMode = true)
        scrollTo("软件日志")
        compose.onNodeWithText("软件日志").assertIsDisplayed()
        scrollTo("开发者模式")
        compose.onNodeWithText("开发者模式").performClick()
        compose.runOnIdle { assertThat(isDeveloperMode(application)).isFalse() }
        compose.onNodeWithText("软件日志").assertDoesNotExist()
        compose.onNodeWithText("开发者选项").assertDoesNotExist()
    }

    @Test
    fun contributor_showsDisplayNameAndGitHubHandleLikeOtherContributors() {
        showAbout()
        scrollTo("MarsDemon")
        compose.onNodeWithText("MarsDemon").assertIsDisplayed()
        compose.onNodeWithText("GitHub · MarsDemon0520").assertIsDisplayed()
        compose.onNodeWithContentDescription("MarsDemon 的 GitHub 头像", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("UI 设计贡献 · GitHub").assertDoesNotExist()
    }

    @Test
    fun projectAndContributorLinks_keepTheirOriginalDestinations() {
        showAbout()
        assertThat(shadowOf(application).nextStartedActivity).isNull()
        val destinations = listOf(
            "夏枫大笨喵w" to "https://github.com/mcxiafeng",
            "懒猫的盒子" to "https://github.com/YuLan888",
            "MarsDemon" to "https://github.com/MarsDemon0520",
            "本项目仓库" to "https://github.com/mcxiafeng/Badger-Android",
        )
        destinations.forEach { (title, url) ->
            scrollTo(title)
            compose.onNodeWithText(title).performClick()
            val intent = checkNotNull(shadowOf(application).nextStartedActivity)
            assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(intent.dataString).isEqualTo(url)
        }
    }

    @Test
    fun licenseContactAndBack_keepTheirExistingCallbacks() {
        val pages = mutableListOf<SettingsPageRoute>()
        var returned = false
        showAbout(onNavigate = { pages += it }, onBack = { returned = true })
        listOf("开源许可", "联系我们").forEach { title ->
            scrollTo(title)
            compose.onNodeWithText(title).performClick()
        }
        assertThat(pages).containsExactly(
            SettingsPageRoute.OpenSourceLicense, SettingsPageRoute.ContactUs,
        ).inOrder()
        compose.onNodeWithContentDescription("返回").performClick()
        assertThat(returned).isTrue()
    }

    private fun showAbout(
        initialDevMode: Boolean = false,
        onNavigate: (SettingsPageRoute) -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        setDeveloperMode(application, initialDevMode)
        val devMode = mutableStateOf(initialDevMode)
        compose.setContent {
            MiuixTheme {
                AboutPage(
                    onBack = onBack,
                    onNavigateToSubPage = onNavigate,
                    devMode = devMode.value,
                    onDevModeChange = { devMode.value = it },
                )
            }
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    private fun tapVersion(count: Int = 1) {
        repeat(count) { compose.onNodeWithText("版本号").performClick() }
    }

    private companion object {
        const val FIRST_PULL_FRACTION = 0.1f
        const val SECOND_PULL_FRACTION = 0.15f
        const val POSITION_TOLERANCE_PX = 1f
        const val PRE_UNLOCK_TAP_COUNT = 6
        const val PROGRESS_TAP_COUNT = 4
        const val TAPS_AFTER_TIMEOUT = 3
        const val REMAINING_TAPS = 2
        const val WITHIN_TAP_TIMEOUT_MS = 1_200L
        const val PAST_TAP_TIMEOUT_MS = 2_100L
    }
}
