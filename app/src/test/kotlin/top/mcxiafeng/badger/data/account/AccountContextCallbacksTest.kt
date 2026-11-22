package top.mcxiafeng.badger.data.account

import android.app.Application
import android.content.ComponentCallbacks
import android.content.ContextWrapper
import android.content.res.Configuration
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountContextCallbacksTest {
    @Test fun componentCallbacksDelegateToTheCapturedBase() {
        var registered: ComponentCallbacks? = null
        var removed: ComponentCallbacks? = null
        val base = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun registerComponentCallbacks(callback: ComponentCallbacks) { registered = callback }
            override fun unregisterComponentCallbacks(callback: ComponentCallbacks) { removed = callback }
        }
        val scope = AccountScope.local(UUID.randomUUID().toString())
        val context = AccountContext(base, scope)
        val callback = Callback()
        context.registerComponentCallbacks(callback)
        context.unregisterComponentCallbacks(callback)
        assertThat(registered).isSameInstanceAs(callback)
        assertThat(removed).isSameInstanceAs(callback)
        assertThat(AccountContext.find(context.applicationContext)?.scope).isEqualTo(scope)
    }

    @Test fun selfApplicationContextRegistersAndRemovesCallbacksWithoutRecursion() {
        val application = RuntimeEnvironment.getApplication()
        val context = AccountContext(application, AccountScope.local(UUID.randomUUID().toString()))
        val callback = Callback()
        assertThat(context.applicationContext).isSameInstanceAs(context)
        context.registerComponentCallbacks(callback)
        application.onLowMemory()
        assertThat(callback.lowMemoryCalls).isEqualTo(1)
        context.unregisterComponentCallbacks(callback)
        application.onLowMemory()
        assertThat(callback.lowMemoryCalls).isEqualTo(1)
    }

    private class Callback : ComponentCallbacks {
        var lowMemoryCalls = 0
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() { lowMemoryCalls++ }
    }
}
