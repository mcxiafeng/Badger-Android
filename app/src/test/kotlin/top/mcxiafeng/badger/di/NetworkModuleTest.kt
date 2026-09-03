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
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.di.NetworkModule
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import java.io.File

/**
 * 网络模块相关单元测试。
 *
 * 覆盖 1 类核心契约：
 * 1. OkHttp 客户端在没有 hostname-verifier override 的前提下能正常 build；
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
        // [§14.2] Robolectric 测试不走 BadgerApplication.onCreate;这里确保
        // GlobalContext 已 startKoin,避免依赖旧 service locator 的路径炸
        // KoinApplicationAlreadyStartedException(因为可能上一个测试已 startKoin)。
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { context }
                },
            )
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        unmockkAll()
    }

    @Test
    fun `general client does not use insecure settings even when allowed`() {
        // After the WebDAV → Badger-Server migration the general OkHttp
        // client no longer needs a hostname-verifier override; the server
        // sits behind HTTPS with a real cert. We just confirm the client
        // builds without throwing.
        // [修复防御]: provideOkHttpClient 当前会调 AuthPrefs.readServerUrl 初始化
        // ServerApi 的 baseUrl；mock Context 没有 stub getSharedPreferences,否则
        // 会撞 mockk no-answer。给 AuthPrefs 加 mock 桩,避免 mockk 跳到真实 sp()
        // 走 Robolectric SQLite。
        mockkObject(AuthPrefs)
        every { AuthPrefs.readServerUrl(any()) } returns "https://badger.example.com"
        val client = NetworkModule.provideOkHttpClient(context, mockk(relaxed = true))
        assertThat(client.hostnameVerifier.javaClass.name).doesNotContain("NetworkModule")
    }

}