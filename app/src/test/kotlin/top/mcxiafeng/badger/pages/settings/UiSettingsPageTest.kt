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
        compose.onAllNodes(isToggleable())[FLOATING_SWITCH].performClick()
        assertChoices(floating = false)
        compose.onAllNodes(isToggleable())[FLOATING_SWITCH].performClick()
        assertChoices(floating = true)
    }

    @Test
    fun preferenceClicksPersistAndReachTheViewModel() {
        showPage()
        compose.onNodeWithText("悬浮导航栏").performClick()
        assertChoices(floating = false)
        compose.onNodeWithText("悬浮导航栏").performClick()
        assertChoices(floating = true)
    }

    @Test
    fun recreatedViewModelReadsTheSavedChoice() {
        viewModel.setFloatingEnabled(false)
        val recreated = UiSettingsViewModel(context)
        assertThat(recreated.floatingEnabled.value).isFalse()

        recreated.setFloatingEnabled(true)
        assertThat(viewModel.floatingEnabled.value).isTrue()
        NavBarConfig.initialize(context)
        assertThat(recreated.floatingEnabled.value).isTrue()
    }

    private fun showPage() {
        compose.setContent {
            MiuixTheme { UiSettingsPage(onBack = {}, viewModel = viewModel) }
        }
    }

    private fun assertChoices(floating: Boolean) {
        val switches = compose.onAllNodes(isToggleable()).assertCountEquals(SWITCH_COUNT)
        switches[FLOATING_SWITCH].assertIsEnabled().apply {
            if (floating) assertIsOn() else assertIsOff()
        }
        compose.runOnIdle {
            assertThat(viewModel.floatingEnabled.value).isEqualTo(floating)
            assertThat(NavBarConfig.isFloatingEnabled(context)).isEqualTo(floating)
        }
    }

    private companion object {
        const val FLOATING_SWITCH = 0
        const val SWITCH_COUNT = 1
    }
}
