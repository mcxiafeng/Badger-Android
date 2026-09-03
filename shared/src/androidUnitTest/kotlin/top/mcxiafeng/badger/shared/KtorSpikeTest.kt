package top.mcxiafeng.badger.shared.net

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [K02 spike] Ktor + CIO 引擎在 JVM（Android target 单测）下的真实请求行为。
 * iOS target 由 :shared:compileKotlinIosSimulatorArm64 验证 klib 编译（Darwin 引擎）；
 * iOS 运行时行为留 K16 模拟器验证。结论记入 docs/kmp-dependency-matrix.md §3。
 */
class KtorSpikeTest {

    @Test
    fun `GET real endpoint returns 200 with body`() = runTest {
        val client = KtorSpikeClient()
        try {
            val bodyLength = client.getAndAssertOk("https://example.com")
            assertTrue("expected non-empty body, got $bodyLength", bodyLength > 0)
        } finally {
            client.close()
        }
    }

    @Test
    fun `POST with JSON body works`() = runTest {
        val client = KtorSpikeClient()
        try {
            // httpbin.org/post 原样回显请求体（临时验证端点，spike 专用）
            val echoed = client.postEcho("https://httpbin.org/post", """{"probe":"badger-k02"}""")
            assertTrue("expected echo of posted body", echoed.contains("badger-k02"))
        } finally {
            client.close()
        }
    }
}
