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
    fun bothSwitchesAreVisibleAndGlassStaysEnabledWithFloatingNavigationOff() {
        viewModel.setFloatingEnabled(false)
        showPage()

        compose.onNodeWithText("悬浮导航栏").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("玻璃材质").assertIsDisplayed().assertIsEnabled()
        assertChoices(floating = false, glass = true)
        compose.onAllNodes(isToggleable())[GLASS_SWITCH].performClick()
        assertChoices(floating = false, glass = false)
        compose.onAllNodes(isToggleable())[GLASS_SWITCH].performClick()
        assertChoices(floating = false, glass = true)
    }

    @Test
    fun preferenceClicksReachAllFourIndependentCombinationsAndPersistThem() {
        showPage()
        assertChoices(floating = true, glass = true)
        compose.onNodeWithText("悬浮导航栏").performClick()
        assertChoices(floating = false, glass = true)
        compose.onNodeWithText("玻璃材质").performClick()
        assertChoices(floating = false, glass = false)
        compose.onNodeWithText("悬浮导航栏").performClick()
        assertChoices(floating = true, glass = false)
        compose.onNodeWithText("玻璃材质").performClick()
        assertChoices(floating = true, glass = true)
    }

    @Test
    fun recreatedViewModelReadsSavedChoicesAndSharesConfigurationUpdates() {
        viewModel.setFloatingEnabled(false)
        viewModel.setGlassMaterialEnabled(false)
        val recreated = UiSettingsViewModel(context)
        assertThat(recreated.floatingEnabled.value).isFalse()
        assertThat(recreated.glassMaterialEnabled.value).isFalse()

        recreated.setGlassMaterialEnabled(true)
        assertThat(viewModel.glassMaterialEnabled.value).isTrue()
        assertThat(viewModel.floatingEnabled.value).isFalse()
        NavBarConfig.initialize(context)
        assertThat(recreated.glassMaterialEnabled.value).isTrue()
        assertThat(recreated.floatingEnabled.value).isFalse()
    }

    private fun showPage() {
        compose.setContent {
            MiuixTheme { UiSettingsPage(onBack = {}, viewModel = viewModel) }
        }
    }

    private fun assertChoices(floating: Boolean, glass: Boolean) {
        val switches = compose.onAllNodes(isToggleable()).assertCountEquals(SWITCH_COUNT)
        switches[FLOATING_SWITCH].assertIsEnabled().apply {
            if (floating) assertIsOn() else assertIsOff()
        }
        switches[GLASS_SWITCH].assertIsEnabled().apply {
            if (glass) assertIsOn() else assertIsOff()
        }
        compose.runOnIdle {
            assertThat(viewModel.floatingEnabled.value).isEqualTo(floating)
            assertThat(viewModel.glassMaterialEnabled.value).isEqualTo(glass)
            assertThat(NavBarConfig.isFloatingEnabled(context)).isEqualTo(floating)
            assertThat(NavBarConfig.isGlassMaterialEnabled(context)).isEqualTo(glass)
        }
    }

    private companion object {
        const val FLOATING_SWITCH = 0
        const val GLASS_SWITCH = 1
        const val SWITCH_COUNT = 2
    }
}
