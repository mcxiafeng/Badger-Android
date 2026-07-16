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
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.data.CloudSyncConfig
import java.io.File

/**
 * 网络模块相关单元测试。
 *
 * 覆盖 2 类核心契约：
 * 1. OkHttp 客户端在没有 hostname-verifier override 的前提下能正常 build；
 * 2. [CloudSyncConfig.isConfigured] 现在基于 [AuthPrefs.readServerUrl]，而非
 *    已被废弃的独立 `server_url` 字段。
 *
 * 历史说明：旧版还有一个「允许不安全 HTTP」开关 `NetworkConfig.isAllowInsecureHttp`，
 * 经过审计发现该字段在 [NetworkModule.baseClient] 里**完全没有被读取**，纯 UI 空壳
 * 已删除。这条用例随之移除。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
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
    fun `cloud-sync is configured when shared server url is set`() {
        mockkObject(AuthPrefs)
        every { AuthPrefs.readServerUrl(any()) } returns "https://example.com"

        assertThat(CloudSyncConfig.isConfigured(context)).isTrue()
    }

    @Test
    fun `cloud-sync is not configured when shared server url is empty`() {
        mockkObject(AuthPrefs)
        every { AuthPrefs.readServerUrl(any()) } returns ""

        assertThat(CloudSyncConfig.isConfigured(context)).isFalse()
    }

    @Test
    fun `cloud-sync ignores legacy server_url field`() {
        // 即便旧版本残留 server_url 字段被读出来,新逻辑 isConfigured 也不应该看它。
        mockkObject(AuthPrefs)
        every { AuthPrefs.readServerUrl(any()) } returns ""
        mockkObject(CloudSyncConfig)
        every { CloudSyncConfig.readLegacyServerUrl(any()) } returns "https://legacy.example.com"

        assertThat(CloudSyncConfig.isConfigured(context)).isFalse()
    }
}