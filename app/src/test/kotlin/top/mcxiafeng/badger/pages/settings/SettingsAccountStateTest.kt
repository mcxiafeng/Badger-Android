package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.network.auth.AuthSession

/** 设置页账号区映射规则：未登录/旧会话/正常会话三种展示。 */
class SettingsAccountStateTest {

    @Test
    fun from_noSession_showsNotLoggedInWithDefaultAvatar() {
        val state = SettingsAccountState.from(null)

        assertThat(state.loggedIn).isFalse()
        assertThat(state.displayName).isEqualTo("未登录")
        assertThat(state.subtitle).isEqualTo("点击登录，同步云端数据")
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
    fun from_legacySessionWithoutUsername_fallsBackToLoggedInLabel() {
        val state = SettingsAccountState.from(AuthSession(userUuid = "u-1", token = "t-1"))

        assertThat(state.loggedIn).isTrue()
        assertThat(state.displayName).isEqualTo("已登录")
        assertThat(state.avatarInitial).isNull()
    }
}
