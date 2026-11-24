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
    fun providerErrorsLogCodeAndExplainKeyPlatformMismatch() = runTest {
        val repo = repository { chain ->
            response(chain.request(), """{"status":"0","info":"$TEST_KEY","infocode":10009}""")
        }

        val failure = runCatching { repo.queryCountry("中国") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(GaodeException::class.java)
        assertThat(failure?.message).contains("Web 服务")
        val logs = ShadowLog.getLogsForTag("GaodeRegionRepositoryTester").joinToString { it.msg }
        assertThat(logs).contains("infocode=10009")
        assertThat(logs).doesNotContain(TEST_KEY)
    }

    @Test
    fun providerErrorWithNonCodeValueIsRedacted() = runTest {
        val repo = repository { chain ->
            response(chain.request(), """{"status":"0","info":"$TEST_KEY","infocode":"$TEST_KEY"}""")
        }

        val failure = runCatching { repo.queryCountry("中国") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(GaodeException::class.java)
        assertThat(failure?.message).doesNotContain(TEST_KEY)
        val logs = ShadowLog.getLogsForTag("GaodeRegionRepositoryTester").joinToString { it.msg }
        assertThat(logs).contains("infocode=unknown")
        assertThat(logs).doesNotContain(TEST_KEY)
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
    fun legacyLookupMatchesOnlyUniquePathsAndKeepsTheSavedTextOffTheWire() = runTest {
        val repo = repository { chain ->
            val request = chain.request()
            assertThat(request.url.queryParameter("keywords")).isEqualTo("100000")
            assertThat(request.url.queryParameter("subdistrict")).isEqualTo("3")
            response(request, """
                {"status":"1","districts":[{"name":"中国","adcode":"100000","level":"country","districts":[
                    {"name":"北京市","adcode":"110000","level":"province","districts":[
                        {"name":"朝阳区","adcode":"110105","level":"district"},
                        {"name":"东城区","adcode":"110101","level":"district"}
                    ]},
                    {"name":"吉林省","adcode":"220000","level":"province","districts":[
                        {"name":"长春市","adcode":"220100","level":"city","districts":[
                            {"name":"朝阳区","adcode":"220104","level":"district"}
                        ]}
                    ]}
                ]}]}
            """.trimIndent())
        }

        assertThat(repo.findSavedPath("中国", "朝阳区")).isEmpty()
        assertThat(repo.findSavedPath("中国", "东城区").map { it.top?.name })
            .containsExactly("中国", "北京市", "东城区").inOrder()
        assertThat(repo.findSavedPath("中国", "吉林省长春市朝阳区").map { it.top?.adcode })
            .containsExactly("100000", "220000", "220100", "220104").inOrder()
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
