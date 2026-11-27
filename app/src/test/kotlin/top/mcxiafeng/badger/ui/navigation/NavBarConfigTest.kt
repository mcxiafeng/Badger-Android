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
    fun defaultsKeepFloatingNavigationAndLiquidEnabled() {
        assertThat(NavBarConfig.floatingFlow.value).isTrue()
        assertThat(NavBarConfig.liquidFlow.value).isTrue()
        assertThat(NavBarConfig.isFloatingEnabled(context)).isTrue()
        assertThat(NavBarConfig.isLiquidEnabled(context)).isTrue()
    }

    @Test
    fun existingPreferencesArePreserved() {
        assertThat(preferences.edit()
            .putBoolean(KEY_FLOATING, false).putBoolean(KEY_LIQUID, false).commit()).isTrue()

        NavBarConfig.initialize(context)

        assertThat(NavBarConfig.floatingFlow.value).isFalse()
        assertThat(NavBarConfig.isFloatingEnabled(context)).isFalse()
        assertThat(NavBarConfig.liquidFlow.value).isFalse()
        assertThat(NavBarConfig.isLiquidEnabled(context)).isFalse()
    }

    @Test
    fun eachChoicePersistsAndNeitherWriterChangesTheOther() {
        listOf(false, true).forEach { floating ->
            listOf(false, true).forEach { liquid ->
                val previousLiquid = NavBarConfig.liquidFlow.value
                NavBarConfig.saveFloatingEnabled(context, floating)
                assertThat(NavBarConfig.liquidFlow.value).isEqualTo(previousLiquid)
                NavBarConfig.saveLiquidEnabled(context, liquid)
                assertChoices(floating, liquid)
                assertThat(preferences.getBoolean(KEY_FLOATING, !floating)).isEqualTo(floating)
                assertThat(preferences.getBoolean(KEY_LIQUID, !liquid)).isEqualTo(liquid)

                NavBarConfig.initialize(context)
                assertChoices(floating, liquid)
            }
        }
    }

    @Test
    fun initializeReloadsTheStoredValuesRatherThanKeepingThePreviousFlowValues() {
        NavBarConfig.saveFloatingEnabled(context, false)
        NavBarConfig.saveLiquidEnabled(context, false)
        assertThat(preferences.edit()
            .putBoolean(KEY_FLOATING, true).putBoolean(KEY_LIQUID, true).commit()).isTrue()
        assertThat(NavBarConfig.floatingFlow.value).isFalse()
        assertThat(NavBarConfig.liquidFlow.value).isFalse()

        NavBarConfig.initialize(context)

        assertChoices(floating = true, liquid = true)
    }

    @Test
    fun initializeRestoresDefaultsIfPreferencesAreRemoved() {
        NavBarConfig.saveFloatingEnabled(context, false)
        NavBarConfig.saveLiquidEnabled(context, false)
        assertThat(preferences.edit().remove(KEY_FLOATING).remove(KEY_LIQUID).commit()).isTrue()

        NavBarConfig.initialize(context)

        assertChoices(floating = true, liquid = true)
    }

    private fun assertChoices(floating: Boolean, liquid: Boolean) {
        assertThat(NavBarConfig.floatingFlow.value).isEqualTo(floating)
        assertThat(NavBarConfig.liquidFlow.value).isEqualTo(liquid)
        assertThat(NavBarConfig.isFloatingEnabled(context)).isEqualTo(floating)
        assertThat(NavBarConfig.isLiquidEnabled(context)).isEqualTo(liquid)
    }

    private companion object {
        const val PREFS_NAME = "badger_settings"
        const val KEY_FLOATING = "nav_bar_floating"
        const val KEY_LIQUID = "nav_liquid_enabled"
    }
}
