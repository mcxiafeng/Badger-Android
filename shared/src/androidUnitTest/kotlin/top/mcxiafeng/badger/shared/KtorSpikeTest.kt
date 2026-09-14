package top.mcxiafeng.badger.shared.net

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

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
            
            val echoed = client.postEcho("https://httpbin.org/post", """{"probe":"badger-k02"}""")
            assertTrue("expected echo of posted body", echoed.contains("badger-k02"))
        } finally {
            client.close()
        }
    }
}
