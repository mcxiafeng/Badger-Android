package top.mcxiafeng.badger.data.repository

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GaodeRequestTest {
    private val config = mockk<GaodeConfig>()

    @Before
    fun setUp() {
        coEvery { config.getApiKey() } returns TEST_KEY
        ShadowLog.clear()
    }

    @Test
    fun timeoutAndExternalErrorDetailsStayOutOfLogsAndUi() = runTest {
        val timeout = repository { chain -> throw SocketTimeoutException(chain.request().url.toString()) }
        val malformed = repository { chain -> response(chain.request(), """["$TEST_KEY"]""") }
        val httpError = repository { chain -> response(chain.request(), TEST_KEY, status = 502) }
        val providerError = repository { chain ->
            response(chain.request(), """{"status":"0","info":"$TEST_KEY","infocode":"10001"}""")
        }

        for (repo in listOf(timeout, malformed, httpError, providerError)) {
            val failure = runCatching { repo.queryCountry("中国") }.exceptionOrNull()
            assertThat(failure).isInstanceOf(GaodeException::class.java)
            assertThat(failure?.message).doesNotContain(TEST_KEY)
        }
        assertThat(ShadowLog.getLogsForTag("GaodeRegionRepositoryTester")).isNotEmpty()
        assertThat(ShadowLog.getLogs().joinToString { "${it.msg} ${it.throwable}" }).doesNotContain(TEST_KEY)
    }

    @Test
    fun queriesOnlyCatalogCountriesAndKeepsIdenticalDistrictNamesScopedByCode() = runTest {
        val requests = mutableListOf<Request>()
        val repo = repository { chain ->
            val request = chain.request()
            requests.add(request)
            val code = request.url.queryParameter("keywords") ?: error("keyword required")
            val name = when (code) {
                "100000" -> "中国"
                "110105", "220104" -> "朝阳区"
                else -> error("unexpected keyword")
            }
            val level = if (code == "100000") "country" else "district"
            response(request, """{"status":"1","districts":[{"name":"$name","adcode":"$code","level":"$level"}]}""")
        }

        val manual = runCatching { repo.queryCountry("联系人手填的地区") }
        assertThat(manual.exceptionOrNull()).isInstanceOf(GaodeException::class.java)
        assertThat(requests).isEmpty()
        assertThat(repo.queryCountry("中国").top?.adcode).isEqualTo("100000")
        repo.queryChildren(GaodeDistrict("朝阳区", "110105", "district"))
        repo.queryChildren(GaodeDistrict("朝阳区", "220104", "district"))
        assertThat(requests.map { it.url.queryParameter("keywords") })
            .containsExactly("100000", "110105", "220104").inOrder()
    }

    @Test
    fun rejectsMismatchedRootAndChildResults() = runTest {
        val repo = repository { chain ->
            response(chain.request(), """{"status":"1","districts":[{"name":"朝阳区","adcode":"220104","level":"district"}]}""")
        }

        assertThat(runCatching { repo.queryCountry("日本") }.exceptionOrNull()).isInstanceOf(GaodeException::class.java)
        val beijing = GaodeDistrict("朝阳区", "110105", "district")
        assertThat(runCatching { repo.queryChildren(beijing) }.exceptionOrNull()).isInstanceOf(GaodeException::class.java)
    }

    private fun repository(interceptor: Interceptor): GaodeRegionRepository =
        GaodeRegionRepository(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

    private fun response(request: Request, body: String, status: Int = 200): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(status)
        .message("Fixture response")
        .body(body.toResponseBody())
        .build()

    private companion object {
        const val TEST_KEY = "synthetic-key-for-redaction-test"
    }
}
