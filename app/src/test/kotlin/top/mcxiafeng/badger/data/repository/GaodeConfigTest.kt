package top.mcxiafeng.badger.data.repository

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GaodeConfigTest {
    @Test
    fun unavailableEncryptionRejectsReadAndWriteWithoutReportingSuccess() = runTest {
        val config = GaodeConfig { throw IllegalStateException("Keystore unavailable") }

        val read = runCatching { config.getApiKey() }
        val write = runCatching { config.setApiKey("candidate-key") }

        assertThat(read.exceptionOrNull()).isInstanceOf(GaodeException::class.java)
        assertThat(read.exceptionOrNull()?.message).contains("无法读取高德 Key")
        assertThat(write.exceptionOrNull()).isInstanceOf(GaodeException::class.java)
        assertThat(write.exceptionOrNull()?.message).contains("无法保存高德 Key")
    }

    @Test
    fun failedDiskWriteKeepsLastSavedKey_andRetryCanSucceed() = runTest {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString("api_key", "") } returns "previous-key"
        every { prefs.edit() } returns editor
        every { editor.commit() } returns false andThen true andThen true
        val config = GaodeConfig { prefs }

        val failed = runCatching { config.setApiKey("candidate-key") }
        assertThat(failed.exceptionOrNull()).isInstanceOf(GaodeException::class.java)
        assertThat(config.getApiKey()).isEqualTo("previous-key")

        config.setApiKey("candidate-key")
        assertThat(config.getApiKey()).isEqualTo("candidate-key")
        config.setApiKey("")
        assertThat(config.getApiKey()).isEmpty()
        verify(exactly = 2) { editor.putString("api_key", "candidate-key") }
        verify(exactly = 1) { editor.remove("api_key") }
    }
}
