package top.mcxiafeng.badger

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccountActivityEntryPoint {
    fun accountStore(): AccountStore
}

/** Each Activity instance and its complete ViewModelStore belong to one fixed account space. */
@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {
    private lateinit var activityScope: AccountScope
    private var switchingAccount = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val accounts = EntryPointAccessors.fromApplication(
            applicationContext, AccountActivityEntryPoint::class.java,
        ).accountStore()
        activityScope = accounts.current()
        val canRestore = savedInstanceState?.getString(SAVED_SCOPE_KEY) == activityScope.key
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(if (canRestore) savedInstanceState else null)
        if (savedInstanceState != null && !canRestore) {
            viewModelStore.clear()
            Log.d(TAG, "Discarded navigation and retained ViewModels from a different account space")
        }
        val scopedContext = AccountContext(this, activityScope)
        setContent {
            key(activityScope.key) {
                CompositionLocalProvider(LocalContext provides scopedContext) {
                    AppTheme { App() }
                }
            }
        }
        observeAccountChanges(accounts)
        Log.d(TAG, "Created account-bound Activity; restored=$canRestore")
    }

    private fun observeAccountChanges(accounts: AccountStore) {
        lifecycleScope.launch {
            accounts.scopeFlow.collect { scope ->
                if (scope.key != activityScope.key && !switchingAccount) {
                    switchingAccount = true
                    viewModelStore.clear()
                    Log.d(TAG, "Account space changed; cleared old ViewModels and recreating Activity")
                    recreate()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SAVED_SCOPE_KEY, activityScope.key)
    }

    companion object {
        private const val TAG = "MainActivityTester"
        private const val SAVED_SCOPE_KEY = "badger_account_scope_key"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    AppTheme { App() }
}
