package top.mcxiafeng.badger.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafeLogTest {

    @Test
    fun user_does_not_expose_short_username() {
        assertEquals("<user:len=3>", SafeLog.user("abc"))
        assertEquals("<user:len=6,first=a>", SafeLog.user("abcdef"))
    }

    @Test
    fun email_keeps_only_safe_parts() {
        assertEquals("<email:a***@example.com>", SafeLog.email("alice@example.com"))
        assertEquals("<email:invalid>", SafeLog.email("alice"))
    }

    @Test
    fun token_does_not_expose_any_token_fragment() {
        val value = SafeLog.token("super-secret-token")
        assertEquals("<token:len=18>", value)
        assertFalse(value.contains("super"))
        assertFalse(value.contains("secret"))
    }

    @Test
    fun url_strips_path_query_and_fragment() {
        assertEquals(
            "https://example.com/<path-redacted>",
            SafeLog.url("https://example.com/api/users?token=secret#profile"),
        )
    }
}
