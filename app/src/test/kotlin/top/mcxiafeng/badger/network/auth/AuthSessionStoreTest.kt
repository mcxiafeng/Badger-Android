package top.mcxiafeng.badger.network.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 会话载体读写 + sessionFlow 响应式更新（登录/登出即时驱动设置页账号区）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthSessionStoreTest {

    private lateinit var store: AuthSessionStore

    @Before
    fun setup() {
        store = AuthSessionStore(RuntimeEnvironment.getApplication())
    }

    @Test
    fun save_persistsUsernameAndUpdatesFlow() {
        store.save(AuthSession(userUuid = "u-1", token = "t-1", username = "夏枫喵"))

        val session = store.read()
        assertThat(session).isNotNull()
        assertThat(session!!.username).isEqualTo("夏枫喵")
        assertThat(session.userUuid).isEqualTo("u-1")
        assertThat(session.token).isEqualTo("t-1")
        assertThat(store.sessionFlow.value).isEqualTo(session)
    }

    @Test
    fun clear_resetsReadAndFlowToNull() {
        store.save(AuthSession(userUuid = "u-1", token = "t-1", username = "夏枫喵"))
        store.clear()

        assertThat(store.read()).isNull()
        assertThat(store.sessionFlow.value).isNull()
    }

    @Test
    fun read_missingToken_returnsNullEvenIfOtherKeysExist() {
        store.save(AuthSession(userUuid = "u-1", token = "t-1", username = "夏枫喵"))

        // 模拟异常状态：token 缺失视为未登录
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("auth_session", android.content.Context.MODE_PRIVATE)
            .edit().remove("token").commit()

        assertThat(store.read()).isNull()
        // 注意：read() 是一次性读取，sessionFlow 仍持有 save 时写入的值
        assertThat(store.sessionFlow.value).isNotNull()
    }
}
