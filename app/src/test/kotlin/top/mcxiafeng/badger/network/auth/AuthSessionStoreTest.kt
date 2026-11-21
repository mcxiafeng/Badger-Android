package top.mcxiafeng.badger.network.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 测试注入独立偏好，不访问真实 KeyStore、用户会话或网络。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthSessionStoreTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var store: AuthSessionStore

    @Before
    fun setup() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("auth_session_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = AuthSessionStore(prefs)
    }

    @Test
    fun save_persistsCompleteBindingAndUpdatesFlow() {
        val expected = session()
        store.save(expected)
        assertThat(store.read()).isEqualTo(expected)
        assertThat(AuthSessionStore(prefs).snapshot()).isEqualTo(expected)
        assertThat(store.sessionFlow.value).isEqualTo(expected)
    }

    @Test
    fun clear_resetsReadSnapshotAndFlow() {
        store.save(session())
        store.clear()
        assertThat(store.read()).isNull()
        assertThat(store.snapshot()).isNull()
        assertThat(store.sessionFlow.value).isNull()
        assertThat(prefs.all).isEmpty()
    }

    @Test
    fun legacySessionWithoutInstanceBinding_cannotBeUsedForCloud() {
        prefs.edit().putString("token", TOKEN).putString("user_uuid", USER_ID)
            .putString("username", "legacy").putLong("sync_version", 900L).commit()
        assertThat(store.read()).isNull()
        assertThat(AuthSessionStore(prefs).sessionFlow.value).isNull()
    }

    @Test
    fun read_missingToken_returnsNull() {
        store.save(session())
        prefs.edit().remove("token").commit()
        assertThat(store.snapshot()).isNull()
    }

    @Test
    fun read_expiredOrInvalidBinding_returnsNull() {
        store.save(session())
        prefs.edit().putString("expires_at", "2000-01-01T00:00:00.000Z").commit()
        assertThat(store.snapshot()).isNull()
        prefs.edit().putString("expires_at", EXPIRY).putString("server_id", "").commit()
        assertThat(store.read()).isNull()
    }

    @Test
    fun save_changingAccountOrEpochCannotInheritCursor() {
        store.save(session())
        store.save(session().copy(syncVersion = 200L, lastSyncTime = 100L))
        assertThat(store.read()!!.syncVersion).isEqualTo(200L)
        store.save(session().copy(userUuid = OTHER_ID, syncVersion = 200L, lastSyncTime = 100L))
        assertThat(store.read()!!.syncVersion).isEqualTo(0L)
        assertThat(store.read()!!.lastSyncTime).isEqualTo(0L)
        store.save(session().copy(dataEpoch = OTHER_ID, syncVersion = 200L, lastSyncTime = 100L))
        assertThat(store.read()!!.syncVersion).isEqualTo(0L)
    }

    @Test
    fun invalidAndCredentialContainingUrls_areRejected() {
        for (url in listOf("https://user@example.test", "https://example.test?token=x", "file:///tmp/db")) {
            assertThat(session().copy(baseUrl = url).isUsable()).isFalse()
        }
    }

    @Test
    fun stringRepresentation_redactsCredentialsAndIdentity() {
        val value = session().toString()
        assertThat(value).doesNotContain(TOKEN)
        assertThat(value).doesNotContain(USER_ID)
    }

    private fun session() = AuthSession(
        userUuid = USER_ID, token = TOKEN, username = "测试账号", serverId = SERVER_ID,
        dataEpoch = EPOCH, baseUrl = "https://example.test", expiresAt = EXPIRY,
    )

    companion object {
        private const val USER_ID = "00000000-0000-0000-0000-000000000001"
        private const val SERVER_ID = "00000000-0000-0000-0000-000000000002"
        private const val EPOCH = "00000000-0000-0000-0000-000000000003"
        private const val OTHER_ID = "00000000-0000-0000-0000-000000000004"
        private const val TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val EXPIRY = "2099-01-01T00:00:00.000Z"
    }
}
