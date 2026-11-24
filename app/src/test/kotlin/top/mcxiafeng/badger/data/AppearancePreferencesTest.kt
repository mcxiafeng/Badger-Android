package top.mcxiafeng.badger.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountScope

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AppearancePreferencesTest {
    private lateinit var context: Context
    private lateinit var stored: SharedPreferences
    private lateinit var preferences: AppearancePreferences

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertThat(stored.edit().clear().commit()).isTrue()
        preferences = AppearancePreferences(context)
    }

    @Test
    fun defaultsBeforeFirstSelection() = runTest {
        assertThat(preferences.read()).isEqualTo(AppearanceState())
        assertThat(preferences.changes.first()).isEqualTo(AppearanceState())
    }

    @Test
    fun persistsBothChoicesAcrossInstancesAndAccounts() = runTest {
        preferences.setMode(AppearanceMode.Dark)
        preferences.setAccent(AppearanceAccent.Forest)
        val remote = AccountScope.remote("server", "user", "epoch", "账号", "https://example.test")
        val anotherAccount = AppearancePreferences(AccountContext(context, remote))
        assertThat(anotherAccount.read()).isEqualTo(
            AppearanceState(AppearanceMode.Dark, AppearanceAccent.Forest),
        )
        anotherAccount.setMode(AppearanceMode.Light)
        assertThat(AppearancePreferences(context).read()).isEqualTo(
            AppearanceState(AppearanceMode.Light, AppearanceAccent.Forest),
        )
        // 保持迁移来源的文件名、键名和枚举值兼容。
        assertThat(stored.getString(KEY_MODE, null)).isEqualTo("Light")
        assertThat(stored.getString(KEY_ACCENT, null)).isEqualTo("Forest")
    }

    @Test
    fun observesOtherWritersAndPreferenceRemoval() = runTest {
        preferences.changes.test {
            assertThat(awaitItem()).isEqualTo(AppearanceState())
            val writer = AppearancePreferences(context)
            writer.setMode(AppearanceMode.Dark)
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(awaitItem()).isEqualTo(AppearanceState(mode = AppearanceMode.Dark))
            writer.setAccent(AppearanceAccent.Rose)
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(awaitItem()).isEqualTo(AppearanceState(AppearanceMode.Dark, AppearanceAccent.Rose))
            assertThat(stored.edit().remove(KEY_MODE).remove(KEY_ACCENT).commit()).isTrue()
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(awaitItem()).isEqualTo(AppearanceState())
            cancelAndIgnoreRemainingEvents()
        }
        preferences.setAccent(AppearanceAccent.Ocean)
        assertThat(preferences.changes.first()).isEqualTo(AppearanceState(accent = AppearanceAccent.Ocean))
    }

    @Test
    fun restoresSourceValuesAndFallsBackPerUnknownOption() {
        assertThat(stored.edit().putString(KEY_MODE, "Dark").putString(KEY_ACCENT, "Amber").commit()).isTrue()
        assertThat(preferences.read()).isEqualTo(AppearanceState(AppearanceMode.Dark, AppearanceAccent.Amber))
        assertThat(stored.edit().putString(KEY_MODE, "future-mode").commit()).isTrue()
        assertThat(preferences.read()).isEqualTo(AppearanceState(accent = AppearanceAccent.Amber))
        assertThat(stored.edit().putString(KEY_MODE, "Light").putString(KEY_ACCENT, "future-accent").commit()).isTrue()
        assertThat(preferences.read()).isEqualTo(AppearanceState(mode = AppearanceMode.Light))
    }

    @Test
    fun corruptValueTypesDoNotDiscardTheOtherChoice() {
        assertThat(stored.edit().putBoolean(KEY_MODE, true).putString(KEY_ACCENT, "Rose").commit()).isTrue()
        assertThat(preferences.read()).isEqualTo(AppearanceState(accent = AppearanceAccent.Rose))
        assertThat(stored.edit().putString(KEY_MODE, "Dark").putBoolean(KEY_ACCENT, true).commit()).isTrue()
        assertThat(preferences.read()).isEqualTo(AppearanceState(mode = AppearanceMode.Dark))
    }

    private companion object {
        const val PREFS_NAME = "badger_appearance"
        const val KEY_MODE = "color_mode"
        const val KEY_ACCENT = "accent"
    }
}
