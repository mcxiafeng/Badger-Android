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
import top.mcxiafeng.badger.network.NetworkConfig
import top.mcxiafeng.badger.network.WebDavConfig
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
        mockkObject(NetworkConfig)
        every { NetworkConfig.isAllowInsecureHttp() } returns true

        val client = NetworkModule.provideOkHttpClient(context)

        // General client should use OkHttp's default hostname verifier, not our custom one
        val verifierClass = client.hostnameVerifier.javaClass.name
        assertThat(verifierClass).doesNotContain("NetworkModule")
    }

    @Test
    fun `webdav client uses insecure ssl and domain-restricted verifier when enabled`() {
        mockkObject(NetworkConfig)
        every { NetworkConfig.isAllowInsecureHttp() } returns true

        mockkObject(WebDavConfig)
        every { WebDavConfig.getServerUrl(any()) } returns "https://trusted.local/webdav/"

        val client = NetworkModule.provideWebDavOkHttpClient(context)

        // WebDAV client should have our custom (trust-all) setup
        val verifierClass = client.hostnameVerifier.javaClass.name
        assertThat(verifierClass).contains("NetworkModule")

        val mockSession = mockk<SSLSession>()
        // hostname verifier allows only the configured domain
        assertThat(client.hostnameVerifier.verify("trusted.local", mockSession)).isTrue()
        assertThat(client.hostnameVerifier.verify("evil.com", mockSession)).isFalse()
    }

    @Test
    fun `webdav hostname verifier rejects all when no url configured`() {
        mockkObject(NetworkConfig)
        every { NetworkConfig.isAllowInsecureHttp() } returns true

        mockkObject(WebDavConfig)
        every { WebDavConfig.getServerUrl(any()) } returns ""

        val client = NetworkModule.provideWebDavOkHttpClient(context)

        val mockSession = mockk<SSLSession>()
        assertThat(client.hostnameVerifier.verify("trusted.local", mockSession)).isFalse()
        assertThat(client.hostnameVerifier.verify("any.host.com", mockSession)).isFalse()
    }

    @Test
    fun `webdav client does not use insecure settings when disabled`() {
        mockkObject(NetworkConfig)
        every { NetworkConfig.isAllowInsecureHttp() } returns false

        val client = NetworkModule.provideWebDavOkHttpClient(context)

        // WebDAV client should use strict SSL when insecure is disabled — no custom verifier
        val verifierClass = client.hostnameVerifier.javaClass.name
        assertThat(verifierClass).doesNotContain("NetworkModule")
    }
}
