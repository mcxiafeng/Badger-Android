package top.mcxiafeng.badger.ui.navigation

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NavBarConfigTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        reset()
    }

    @After
    fun reset() {
        assertThat(preferences.edit().clear().commit()).isTrue()
        NavBarConfig.initialize(context)
    }

    @Test
    fun defaultsKeepFloatingNavigationEnabled() {
        assertThat(NavBarConfig.floatingFlow.value).isTrue()
        assertThat(NavBarConfig.isFloatingEnabled(context)).isTrue()
    }

    @Test
    fun existingFloatingPreferenceIsPreserved() {
        assertThat(preferences.edit().putBoolean(KEY_FLOATING, false).commit()).isTrue()

        NavBarConfig.initialize(context)

        assertThat(NavBarConfig.floatingFlow.value).isFalse()
        assertThat(NavBarConfig.isFloatingEnabled(context)).isFalse()
    }

    @Test
    fun eachChoicePersistsAndReloads() {
        listOf(false, true).forEach { floating ->
            NavBarConfig.saveFloatingEnabled(context, floating)
            assertChoices(floating)
            assertThat(preferences.getBoolean(KEY_FLOATING, !floating)).isEqualTo(floating)

            NavBarConfig.initialize(context)
            assertChoices(floating)
        }
    }

    @Test
    fun initializeReloadsTheStoredValueRatherThanKeepingThePreviousFlowValue() {
        NavBarConfig.saveFloatingEnabled(context, false)
        assertThat(preferences.edit().putBoolean(KEY_FLOATING, true).commit()).isTrue()
        assertThat(NavBarConfig.floatingFlow.value).isFalse()

        NavBarConfig.initialize(context)

        assertChoices(true)
    }

    @Test
    fun initializeRestoresTheDefaultIfThePreferenceIsRemoved() {
        NavBarConfig.saveFloatingEnabled(context, false)
        assertThat(preferences.edit().remove(KEY_FLOATING).commit()).isTrue()

        NavBarConfig.initialize(context)

        assertChoices(true)
    }

    private fun assertChoices(floating: Boolean) {
        assertThat(NavBarConfig.floatingFlow.value).isEqualTo(floating)
        assertThat(NavBarConfig.isFloatingEnabled(context)).isEqualTo(floating)
    }

    private companion object {
        const val PREFS_NAME = "badger_settings"
        const val KEY_FLOATING = "nav_bar_floating"
    }
}
