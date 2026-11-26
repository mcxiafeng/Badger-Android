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
    fun defaultsKeepFloatingNavigationAndGlassMaterialEnabled() {
        assertThat(NavBarConfig.floatingFlow.value).isTrue()
        assertThat(NavBarConfig.glassMaterialFlow.value).isTrue()
        assertThat(NavBarConfig.isFloatingEnabled(context)).isTrue()
        assertThat(NavBarConfig.isGlassMaterialEnabled(context)).isTrue()
    }

    @Test
    fun existingFloatingPreferenceIsPreservedWhenMaterialKeyIsAbsent() {
        assertThat(preferences.edit().putBoolean(KEY_FLOATING, false).commit()).isTrue()

        NavBarConfig.initialize(context)

        assertThat(NavBarConfig.floatingFlow.value).isFalse()
        assertThat(NavBarConfig.isFloatingEnabled(context)).isFalse()
        assertThat(NavBarConfig.glassMaterialFlow.value).isTrue()
        assertThat(NavBarConfig.isGlassMaterialEnabled(context)).isTrue()
        assertThat(preferences.contains(KEY_GLASS)).isFalse()
    }

    @Test
    fun eachCombinationPersistsAndNeitherWriterChangesTheOtherChoice() {
        listOf(false, true).forEach { floating ->
            listOf(false, true).forEach { glass ->
                val previousGlass = NavBarConfig.glassMaterialFlow.value
                NavBarConfig.saveFloatingEnabled(context, floating)
                assertThat(NavBarConfig.glassMaterialFlow.value).isEqualTo(previousGlass)
                assertThat(NavBarConfig.isGlassMaterialEnabled(context)).isEqualTo(previousGlass)
                NavBarConfig.saveGlassMaterialEnabled(context, glass)
                assertChoices(floating, glass)
                assertThat(preferences.getBoolean(KEY_FLOATING, !floating)).isEqualTo(floating)
                assertThat(preferences.getBoolean(KEY_GLASS, !glass)).isEqualTo(glass)

                NavBarConfig.initialize(context)
                assertChoices(floating, glass)
            }
        }
    }

    @Test
    fun initializeReloadsBothStoredValuesRatherThanKeepingPreviousFlowValues() {
        NavBarConfig.saveFloatingEnabled(context, false)
        NavBarConfig.saveGlassMaterialEnabled(context, false)
        assertThat(preferences.edit().putBoolean(KEY_FLOATING, true).putBoolean(KEY_GLASS, true).commit()).isTrue()
        assertThat(NavBarConfig.floatingFlow.value).isFalse()
        assertThat(NavBarConfig.glassMaterialFlow.value).isFalse()

        NavBarConfig.initialize(context)

        assertChoices(floating = true, glass = true)
    }

    @Test
    fun initializeRestoresMaterialDefaultIfItsPreferenceIsRemoved() {
        NavBarConfig.saveFloatingEnabled(context, false)
        NavBarConfig.saveGlassMaterialEnabled(context, false)
        assertThat(preferences.edit().remove(KEY_GLASS).commit()).isTrue()

        NavBarConfig.initialize(context)

        assertChoices(floating = false, glass = true)
    }

    private fun assertChoices(floating: Boolean, glass: Boolean) {
        assertThat(NavBarConfig.floatingFlow.value).isEqualTo(floating)
        assertThat(NavBarConfig.glassMaterialFlow.value).isEqualTo(glass)
        assertThat(NavBarConfig.isFloatingEnabled(context)).isEqualTo(floating)
        assertThat(NavBarConfig.isGlassMaterialEnabled(context)).isEqualTo(glass)
    }

    private companion object {
        const val PREFS_NAME = "badger_settings"
        const val KEY_FLOATING = "nav_bar_floating"
        const val KEY_GLASS = "glass_material_enabled"
    }
}
