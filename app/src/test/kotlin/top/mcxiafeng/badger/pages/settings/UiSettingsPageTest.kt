package top.mcxiafeng.badger.pages.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import top.mcxiafeng.badger.data.AppearanceMode
import top.mcxiafeng.badger.data.AppearancePreferences
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UiSettingsPageTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var viewModel: UiSettingsViewModel

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        preferences = context.getSharedPreferences("badger_settings", Context.MODE_PRIVATE)
        reset()
        viewModel = UiSettingsViewModel(context, AppearancePreferences(context))
    }

    @After
    fun reset() {
        assertThat(preferences.edit().clear().commit()).isTrue()
        NavBarConfig.initialize(context)
    }

    @Test
    fun theFloatingNavigationSwitchIsVisibleAndReachesTheConfiguration() {
        showPage()

        compose.onNodeWithText("悬浮导航栏").assertIsDisplayed().assertIsEnabled()
        assertChoices(floating = true)
        compose.onNodeWithText("悬浮导航栏").performClick()
        assertChoices(floating = false)
        compose.onNodeWithText("悬浮导航栏").performClick()
        assertChoices(floating = true)
    }

    @Test
    fun theLiquidSwitchFollowsTheFloatingOne() {
        showPage()

        // 先有悬浮再有液态：悬浮开启时液态开关才出现，且可关闭悬浮底栏的玻璃材质。
        compose.onNodeWithText("液态导航栏").assertIsDisplayed().assertIsEnabled()
        assertChoices(floating = true, liquid = true)
        compose.onNodeWithText("液态导航栏").performClick()
        assertChoices(floating = true, liquid = false)
        compose.onNodeWithText("液态导航栏").performClick()
        assertChoices(floating = true, liquid = true)

        compose.onNodeWithText("悬浮导航栏").performClick()
        assertChoices(floating = false, liquid = true)
        compose.onNodeWithText("液态导航栏").assertDoesNotExist()
        // 关闭悬浮时液态选择保留；重新开启后液态开关回到原状态。
        compose.onNodeWithText("悬浮导航栏").performClick()
        compose.onNodeWithText("液态导航栏").assertIsDisplayed()
        assertChoices(floating = true, liquid = true)
    }

    @Test
    fun recreatedViewModelReadsTheSavedChoices() {
        viewModel.setFloatingEnabled(false)
        viewModel.setLiquidEnabled(false)
        val recreated = UiSettingsViewModel(context, AppearancePreferences(context))
        assertThat(recreated.floatingEnabled.value).isFalse()
        assertThat(recreated.liquidEnabled.value).isFalse()

        recreated.setLiquidEnabled(true)
        assertThat(viewModel.liquidEnabled.value).isTrue()
        NavBarConfig.initialize(context)
        assertThat(recreated.liquidEnabled.value).isTrue()
    }

    @Test
    fun theFollowSystemSwitchDefaultsOnAndWritesAppearanceMode() {
        val appearance = AppearancePreferences(context)
        // 默认开启：未配置过外观时为跟随系统。
        assertThat(appearance.read().mode).isEqualTo(AppearanceMode.System)
        // 关闭跟随系统落到当前生效的浅色（Robolectric 系统非夜间），再开启回到跟随系统。
        viewModel.setFollowSystem(false)
        awaitMode(appearance, AppearanceMode.Light)
        viewModel.setFollowSystem(true)
        awaitMode(appearance, AppearanceMode.System)
    }

    @Test
    fun theFollowSystemSwitchIsShownOnUiSettingsPage() {
        showPage()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("深浅模式跟随系统"))
        compose.onNodeWithText("深浅模式跟随系统").assertIsDisplayed()
    }

    private fun awaitMode(prefs: AppearancePreferences, expected: AppearanceMode) {
        val deadline = System.currentTimeMillis() + 5_000
        while (prefs.read().mode != expected) {
            check(System.currentTimeMillis() < deadline) { "外观模式未变为 $expected" }
            // setMode 的写协程完成后需回到 Main 释放 writeMutex，泵送主循环让它跑完。
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
    }

    private fun showPage() {
        compose.setContent {
            MiuixTheme { UiSettingsPage(onBack = {}, viewModel = viewModel) }
        }
    }

    private fun assertChoices(floating: Boolean, liquid: Boolean = true) {
        // 只统计导航卡内的悬浮/液态开关，避免外观卡「深浅模式跟随系统」开关混入计数。
        val switchCount = if (floating) 2 else 1
        val switches = compose.onAllNodes(
            isToggleable() and hasAnyAncestor(hasTestTag("ui_settings_nav_card")),
        ).assertCountEquals(switchCount)
        switches[FLOATING_SWITCH].assertIsEnabled().apply {
            if (floating) assertIsOn() else assertIsOff()
        }
        if (floating) {
            switches[LIQUID_SWITCH].assertIsEnabled().apply {
                if (liquid) assertIsOn() else assertIsOff()
            }
        }
        compose.runOnIdle {
            assertThat(viewModel.floatingEnabled.value).isEqualTo(floating)
            assertThat(viewModel.liquidEnabled.value).isEqualTo(liquid)
            assertThat(NavBarConfig.isFloatingEnabled(context)).isEqualTo(floating)
            assertThat(NavBarConfig.isLiquidEnabled(context)).isEqualTo(liquid)
        }
    }

    private companion object {
        const val FLOATING_SWITCH = 0
        const val LIQUID_SWITCH = 1
    }
}
