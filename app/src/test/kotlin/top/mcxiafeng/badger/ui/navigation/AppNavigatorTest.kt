package top.mcxiafeng.badger.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppNavigatorTest {

    @Test
    fun `navigating to current route does not grow back stack`() {
        val navigator = AppNavigator()

        navigator.navigate(Route.ContactDetail(42L))
        navigator.navigate(Route.ContactDetail(42L))

        assertThat(navigator.currentRoute.value).isEqualTo(Route.ContactDetail(42L))
        assertThat(navigator.navigateBack()).isTrue()
        assertThat(navigator.currentRoute.value).isEqualTo(Route.MainTabs)
        assertThat(navigator.navigateBack()).isFalse()
    }

    @Test
    fun `different routes still preserve back navigation`() {
        val navigator = AppNavigator()

        navigator.navigate(Route.Scanner())
        navigator.navigate(Route.ContactDetail(42L))

        assertThat(navigator.navigateBack()).isTrue()
        assertThat(navigator.currentRoute.value).isEqualTo(Route.Scanner())
        assertThat(navigator.navigateBack()).isTrue()
        assertThat(navigator.currentRoute.value).isEqualTo(Route.MainTabs)
        assertThat(navigator.navigateBack()).isFalse()
    }

    @Test
    fun `reset clears the complete navigation history`() {
        val navigator = AppNavigator()

        navigator.navigate(Route.Scanner())
        navigator.navigate(Route.ContactDetail(42L))
        navigator.resetToMain()

        assertThat(navigator.currentRoute.value).isEqualTo(Route.MainTabs)
        assertThat(navigator.navigateBack()).isFalse()
    }
}
