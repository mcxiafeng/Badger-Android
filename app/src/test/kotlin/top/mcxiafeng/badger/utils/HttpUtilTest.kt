package top.mcxiafeng.badger.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpUtilTest {

    @Test
    fun buildUrl_returns_base_url_without_parameters() {
        assertEquals("https://example.com/api/items", HttpUtil.buildUrl("https://example.com/api/items", emptyMap()))
    }

    @Test
    fun buildUrl_appends_query_to_url_without_existing_query() {
        assertEquals(
            "https://example.com/api/items?a=1&b=two+words",
            HttpUtil.buildUrl("https://example.com/api/items", linkedMapOf("a" to "1", "b" to "two words")),
        )
    }

    @Test
    fun buildUrl_preserves_existing_query_separator() {
        assertEquals(
            "https://example.com/api/items?existing=yes&a=1",
            HttpUtil.buildUrl("https://example.com/api/items?existing=yes", mapOf("a" to "1")),
        )
    }
}
