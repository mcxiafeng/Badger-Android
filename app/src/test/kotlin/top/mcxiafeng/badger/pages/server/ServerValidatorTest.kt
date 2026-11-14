package top.mcxiafeng.badger.pages.server

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerValidatorTest {

    // —— normalizeInput：scheme 剥离与采纳 ——

    @Test
    fun normalizeInput_stripsHttpsAndAdoptsScheme() {
        val (text, https) = ServerValidator.normalizeInput("https://badger.example.com/", currentHttps = false)
        assertThat(text).isEqualTo("badger.example.com")
        assertThat(https).isTrue()
    }

    @Test
    fun normalizeInput_stripsHttp() {
        val (text, https) = ServerValidator.normalizeInput("http://192.168.10.19:8080", currentHttps = true)
        assertThat(text).isEqualTo("192.168.10.19:8080")
        assertThat(https).isFalse()
    }

    @Test
    fun normalizeInput_plainHostKeepsCurrentScheme() {
        val (text, https) = ServerValidator.normalizeInput(" badger.example.com ", currentHttps = true)
        assertThat(text).isEqualTo("badger.example.com")
        assertThat(https).isTrue()
    }

    // —— blockReason：拦截规则 ——

    @Test
    fun blockReason_blankInput() {
        assertThat(ServerValidator.blockReason("")).isEqualTo("请输入地址")
    }

    @Test
    fun blockReason_bareIpv6Rejected() {
        assertThat(ServerValidator.blockReason("::1:8080")).contains("方括号")
    }

    @Test
    fun blockReason_wrappedIpv6Accepted() {
        assertThat(ServerValidator.blockReason("[::1]:8080")).isNull()
    }

    @Test
    fun blockReason_portOutOfRange() {
        assertThat(ServerValidator.blockReason("host:99999")).contains("端口")
    }

    @Test
    fun blockReason_validHostPort() {
        assertThat(ServerValidator.blockReason("badger.example.com:51026")).isNull()
    }

    // —— composeUrl / withDefaultPort ——

    @Test
    fun composeUrl_https() {
        assertThat(ServerValidator.composeUrl("host:51026", https = true)).isEqualTo("https://host:51026")
    }

    @Test
    fun composeUrl_stripsTrailingSlash() {
        assertThat(ServerValidator.composeUrl("host/", https = false)).isEqualTo("http://host")
    }

    @Test
    fun withDefaultPort_appendsDefaultWhenMissing() {
        assertThat(ServerValidator.withDefaultPort("host")).isEqualTo("host:51026")
        assertThat(ServerValidator.withDefaultPort("host:8080")).isEqualTo("host:8080")
    }

    @Test
    fun withDefaultPort_ipv6LiteralGetsBracketedPort() {
        assertThat(ServerValidator.withDefaultPort("[::1]")).isEqualTo("[::1]:51026")
    }

    // —— SRV 服务发现判定 ——

    @Test
    fun srvQueryName_skipsExplicitPortAndIp() {
        assertThat(ServerValidator.srvQueryName("host:8080")).isNull()
        assertThat(ServerValidator.srvQueryName("192.168.10.19")).isNull()
        assertThat(ServerValidator.srvQueryName("host/path")).isNull()
    }

    @Test
    fun srvQueryName_plainHostnameReturnsQuery() {
        assertThat(ServerValidator.srvQueryName("badger.example.com")).isEqualTo("_badger._tcp.badger.example.com")
    }
}
