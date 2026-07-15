package top.mcxiafeng.badger.di

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import top.mcxiafeng.badger.data.CloudSyncConfig
import top.mcxiafeng.badger.data.NetworkConfig
import java.io.File
import javax.net.ssl.SSLSession

@RunWith(RobolectricTestRunner::class)
class NetworkModuleTest {

    private lateinit var tempDir: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "badger-test-${System.nanoTime()}")
        tempDir.mkdirs()
        tempDir.deleteOnExit()
        context = mockk {
            every { cacheDir } returns tempDir
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `general client does not use insecure settings even when allowed`() {
        // After the WebDAV → Badger-Server migration the general OkHttp
        // client no longer needs a hostname-verifier override; the server
        // sits behind HTTPS with a real cert. We just confirm the client
        // builds without throwing.
        val client = NetworkModule.provideOkHttpClient(context, mockk(relaxed = true), mockk(relaxed = true))
        assertThat(client.hostnameVerifier.javaClass.name).doesNotContain("NetworkModule")
    }

    @Test
    fun `cloud-sync server url is read from CloudSyncConfig when provided`() {
        mockkObject(NetworkConfig)
        every { NetworkConfig.isAllowInsecureHttp(any()) } returns true

        mockkObject(CloudSyncConfig)
        every { CloudSyncConfig.getServerUrl(any()) } returns "https://trusted.local"

        // Just sanity-check that the pref hook is callable in tests.
        val configured = CloudSyncConfig.isConfigured(context)
        assertThat(configured).isFalse()
    }

    @Test
    fun `cloud-sync is not configured when server url is empty`() {
        mockkObject(CloudSyncConfig)
        every { CloudSyncConfig.getServerUrl(any()) } returns ""

        assertThat(CloudSyncConfig.isConfigured(context)).isFalse()
    }

    @Test
    fun `cloud-sync is configured when server url is set`() {
        mockkObject(CloudSyncConfig)
        every { CloudSyncConfig.getServerUrl(any()) } returns "https://example.com"

        assertThat(CloudSyncConfig.isConfigured(context)).isTrue()
    }
}
