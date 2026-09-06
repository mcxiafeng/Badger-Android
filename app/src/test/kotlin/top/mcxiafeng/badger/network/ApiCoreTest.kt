package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test

class ApiCoreTest {

    private val core = ApiCore(
        baseUrl = "https://example.com/",
        transport = OkHttpApiTransport(OkHttpClient()),
        tokenProvider = { null },
    )

    @Test
    fun `urlOf normalizes slash at base path boundary`() {
        assertThat(core.urlOf("/api/auth/me")).isEqualTo("https://example.com/api/auth/me")
        assertThat(core.urlOf("api/auth/me")).isEqualTo("https://example.com/api/auth/me")
    }
}
