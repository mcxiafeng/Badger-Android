package top.mcxiafeng.badger.data.account

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountStoreRegistryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("account_spaces", Context.MODE_PRIVATE)
    private val gson = Gson()
    private lateinit var accounts: AccountStore

    @Before fun setup() {
        prefs.edit().clear().commit()
        accounts = AccountStore(context)
    }

    @Test fun stableLocalFirstOrder() {
        val z = accounts.activateRemote("s", "z", "e", "Z", "https://b.example")
        val b = accounts.activateRemote("s", "b", "e", "B", "https://a.example")
        val a = accounts.activateRemote("s", "a", "e", "A", "https://a.example")
        assertThat(accounts.registeredScopes()).containsExactly(accounts.localScope(), a, b, z).inOrder()
        assertThat(AccountStore(context).registeredScopes()).isEqualTo(accounts.registeredScopes())
        assertThat(accounts.current()).isEqualTo(a)
    }

    @Test fun rebuiltStoragePaths() {
        val remote = accounts.activateRemote("s", "u", "e", "账号", "https://a.example")
        put(remote.key, gson.toJson(remote.copy(databaseName = "../../another.db")))
        val restored = AccountStore(context)
        assertThat(restored.current()).isEqualTo(remote)
        assertThat(restored.registeredScopes()).containsExactly(restored.localScope(), remote).inOrder()
    }

    @Test fun corruptRecordsAreRetained() {
        val good = accounts.activateRemote("s", "a", "e", "A", "https://a.example")
        val damaged = AccountScope.remote("s", "b", "e", "B", "https://b.example")
        put(damaged.key, gson.toJson(damaged.copy(userId = "different")))
        put("malformed", "{")
        put("null", "null")
        put("wrong-type", 1)
        val badUrl = AccountScope.remote("s", "c", "e", "C", "https://user@c.example")
        put(badUrl.key, gson.toJson(badUrl))
        assertThat(accounts.registeredScopes()).containsExactly(accounts.localScope(), good).inOrder()
        assertThat(prefs.getString("registered_${damaged.key}", null)).isNotNull()
        assertThat(prefs.contains("registered_malformed")).isTrue()
        assertThat(prefs.contains("registered_null")).isTrue()
        assertThat(prefs.contains("registered_wrong-type")).isTrue()
        assertThat(prefs.contains("registered_${badUrl.key}")).isTrue()
        assertThat(accounts.current()).isEqualTo(good)
    }

    @Test fun corruptActiveUsesSameValidation() {
        val remote = accounts.activateRemote("s", "a", "e", "A", "https://a.example")
        put(remote.key, gson.toJson(remote.copy(key = "forged")))
        val restored = AccountStore(context)
        assertThat(restored.current()).isEqualTo(restored.localScope())
        assertThat(restored.registeredScopes()).containsExactly(restored.localScope())
        assertThat(prefs.getString("registered_${remote.key}", null)).isNotNull()
    }

    @Test fun epochsRemainDistinctAndDeterministic() {
        val first = accounts.activateRemote("s", "a", "old", "A", "https://a.example")
        val second = accounts.activateRemote("s", "a", "new", "A", "https://a.example")
        val expected = listOf(first, second).sortedBy { it.key }
        assertThat(accounts.registeredScopes().drop(1)).containsExactlyElementsIn(expected).inOrder()
    }

    private fun put(key: String, value: String) {
        prefs.edit().putString("registered_$key", value).commit()
    }

    private fun put(key: String, value: Int) {
        prefs.edit().putInt("registered_$key", value).commit()
    }
}
