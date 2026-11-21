package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.network.auth.AuthSession

/** 设置页账号区映射规则：未登录/旧会话/正常会话三种展示。 */
class SettingsAccountStateTest {

    @Test
    fun from_noSession_showsNotLoggedInWithDefaultAvatar() {
        val state = SettingsAccountState.from(null)

        assertThat(state.loggedIn).isFalse()
        assertThat(state.displayName).isEqualTo("本地用户")
        assertThat(state.subtitle).isEqualTo("资料保存在本机")
        assertThat(state.avatarInitial).isNull()
    }

    @Test
    fun from_sessionWithUsername_showsUsernameWithInitialAvatar() {
        val state = SettingsAccountState.from(
            AuthSession(userUuid = "u-1", token = "t-1", username = "夏枫喵")
        )

        assertThat(state.loggedIn).isTrue()
        assertThat(state.displayName).isEqualTo("夏枫喵")
        assertThat(state.subtitle).isEqualTo("已登录")
        assertThat(state.avatarInitial).isEqualTo("夏")
    }

    @Test
    fun expiredSessionRetainsRemoteIdentity() {
        val scope = AccountScope("cloud", "server", "user", "epoch", "账号A", "https://example.test", "cloud.db")
        val state = SettingsAccountState.from(null, scope)
        assertThat(state.loggedIn).isFalse()
        assertThat(state.displayName).isEqualTo("账号A")
        assertThat(state.subtitle).contains("重新登录")
    }

    @Test
    fun sessionFromAnotherAccountCannotAuthenticateCurrentSpace() {
        val scope = AccountScope("cloud", "server", "user-A", "epoch", "账号A", "https://example.test", "cloud.db")
        val session = AuthSession("user-B", "token", "账号B", serverId = "server", dataEpoch = "epoch")
        val state = SettingsAccountState.from(session, scope)
        assertThat(state.displayName).isEqualTo("账号A")
        assertThat(state.loggedIn).isFalse()
    }

    @Test
    fun localSpaceDoesNotDisplayStagedRemoteSession() {
        val scope = AccountScope("local", null, null, null, "本地用户", "", "badger_database")
        val state = SettingsAccountState.from(AuthSession("user-B", "token", "账号B"), scope)
        assertThat(state.displayName).isEqualTo("本地用户")
        assertThat(state.loggedIn).isFalse()
    }

    @Test
    fun from_legacySessionWithoutUsername_fallsBackToLoggedInLabel() {
        val state = SettingsAccountState.from(AuthSession(userUuid = "u-1", token = "t-1"))

        assertThat(state.loggedIn).isTrue()
        assertThat(state.displayName).isEqualTo("已登录")
        assertThat(state.avatarInitial).isNull()
    }
}
