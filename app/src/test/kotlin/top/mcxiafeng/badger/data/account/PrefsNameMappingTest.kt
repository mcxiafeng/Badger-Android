package top.mcxiafeng.badger.data.account

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** EncryptedPrefs 实例缓存键依赖 resolvePrefsName，键必须与 AccountContext 实际打开的物理文件一致。 */
class PrefsNameMappingTest {

    private val local = AccountScope.local("test-space")
    private val remote = AccountScope.remote("server-1", "user-1", "epoch-1", "远程用户", "https://example.test")

    @Test
    fun businessPreferencesStayUnmappedForLocalScope() {
        assertThat(AccountContext.resolvePrefsName(local, "ai_ocr_credentials"))
            .isEqualTo("ai_ocr_credentials")
    }

    @Test
    fun businessPreferencesMapPerRemoteScope() {
        assertThat(AccountContext.resolvePrefsName(remote, "ai_ocr_credentials"))
            .isEqualTo("${remote.key}_ai_ocr_credentials")
        assertThat(AccountContext.resolvePrefsName(remote, "short_link_credentials"))
            .isEqualTo("${remote.key}_short_link_credentials")
    }

    @Test
    fun nonBusinessPreferencesNeverMap() {
        assertThat(AccountContext.resolvePrefsName(remote, "auth_session_encrypted"))
            .isEqualTo("auth_session_encrypted")
        assertThat(AccountContext.resolvePrefsName(remote, "device_prefs")).isEqualTo("device_prefs")
    }

    @Test
    fun nullScopeFallsBackToPhysicalName() {
        assertThat(AccountContext.resolvePrefsName(null, "short_link_credentials"))
            .isEqualTo("short_link_credentials")
    }
}
