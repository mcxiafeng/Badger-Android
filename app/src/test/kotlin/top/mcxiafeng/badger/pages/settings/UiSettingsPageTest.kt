package top.mcxiafeng.badger.pages.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
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
        viewModel = UiSettingsViewModel(context)
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
        val recreated = UiSettingsViewModel(context)
        assertThat(recreated.floatingEnabled.value).isFalse()
        assertThat(recreated.liquidEnabled.value).isFalse()

        recreated.setLiquidEnabled(true)
        assertThat(viewModel.liquidEnabled.value).isTrue()
        NavBarConfig.initialize(context)
        assertThat(recreated.liquidEnabled.value).isTrue()
    }

    private fun showPage() {
        compose.setContent {
            MiuixTheme { UiSettingsPage(onBack = {}, viewModel = viewModel) }
        }
    }

    private fun assertChoices(floating: Boolean, liquid: Boolean = true) {
        val switchCount = if (floating) 2 else 1
        val switches = compose.onAllNodes(isToggleable()).assertCountEquals(switchCount)
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
