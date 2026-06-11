package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class WebDavClientTest {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var webDavClient: WebDavClient

    private val testUrl = "https://example.com/dav/"
    private val testUsername = "user"
    private val testPassword = "pass"

    @Before
    fun setup() {
        okHttpClient = mockk(relaxed = true)
        webDavClient = WebDavClient(okHttpClient)
    }

    private fun setupCall(response: Response) {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } returns response
        every { okHttpClient.newCall(any()) } returns call
    }

    private fun mockSuccessResponse(code: Int = 200): Response {
        val response = mockk<Response>(relaxed = true)
        every { response.code } returns code
        every { response.isSuccessful } returns true
        return response
    }

    private fun mockErrorResponse(code: Int): Response {
        val response = mockk<Response>(relaxed = true)
        every { response.code } returns code
        every { response.isSuccessful } returns false
        return response
    }

    // ========== testConnection ==========

    @Test
    fun testConnection_success_returnsSuccess() = runTest {
        setupCall(mockSuccessResponse(207))
        val result = webDavClient.testConnection(testUrl, testUsername, testPassword)
        assertThat(result).isInstanceOf(WebDavResult.Success::class.java)
    }

    @Test
    fun testConnection_authError_returnsAuthError() = runTest {
        setupCall(mockErrorResponse(401))
        val result = webDavClient.testConnection(testUrl, testUsername, testPassword)
        assertThat(result).isInstanceOf(WebDavResult.AuthError::class.java)
        assertThat((result as WebDavResult.AuthError).message).contains("401")
    }

    @Test
    fun testConnection_notFound_returnsNotFound() = runTest {
        setupCall(mockErrorResponse(404))
        val result = webDavClient.testConnection(testUrl, testUsername, testPassword)
        assertThat(result).isEqualTo(WebDavResult.NotFound)
    }

    @Test
    fun testConnection_timeout_returnsTimeout() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws SocketTimeoutException("connect timed out")
        every { okHttpClient.newCall(any()) } returns call
        val result = webDavClient.testConnection(testUrl, testUsername, testPassword)
        assertThat(result).isEqualTo(WebDavResult.Timeout)
    }

    @Test
    fun testConnection_networkError_returnsNetworkError() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws IOException("network error")
        every { okHttpClient.newCall(any()) } returns call
        val result = webDavClient.testConnection(testUrl, testUsername, testPassword)
        assertThat(result).isInstanceOf(WebDavResult.NetworkError::class.java)
    }

    @Test
    fun testConnection_invalidUrl_returnsNetworkError() = runTest {
        val result = webDavClient.testConnection("http://insecure.com/dav/", testUsername, testPassword)
        assertThat(result).isInstanceOf(WebDavResult.NetworkError::class.java)
    }

    // ========== upload ==========

    @Test
    fun upload_success_returnsSuccess() = runTest {
        setupCall(mockSuccessResponse(201))
        val result = webDavClient.upload(testUrl, testUsername, testPassword, "/test.txt", "data".toByteArray())
        assertThat(result).isInstanceOf(WebDavResult.Success::class.java)
    }

    @Test
    fun upload_authError_returnsAuthError() = runTest {
        setupCall(mockErrorResponse(403))
        val result = webDavClient.upload(testUrl, testUsername, testPassword, "/test.txt", "data".toByteArray())
        assertThat(result).isInstanceOf(WebDavResult.AuthError::class.java)
    }

    @Test
    fun upload_notFound_returnsNotFound() = runTest {
        setupCall(mockErrorResponse(404))
        val result = webDavClient.upload(testUrl, testUsername, testPassword, "/test.txt", "data".toByteArray())
        assertThat(result).isEqualTo(WebDavResult.NotFound)
    }

    @Test
    fun upload_timeout_returnsTimeout() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws SocketTimeoutException("connect timed out")
        every { okHttpClient.newCall(any()) } returns call
        val result = webDavClient.upload(testUrl, testUsername, testPassword, "/test.txt", "data".toByteArray())
        assertThat(result).isEqualTo(WebDavResult.Timeout)
    }

    @Test
    fun upload_networkError_returnsNetworkError() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws IOException("network error")
        every { okHttpClient.newCall(any()) } returns call
        val result = webDavClient.upload(testUrl, testUsername, testPassword, "/test.txt", "data".toByteArray())
        assertThat(result).isInstanceOf(WebDavResult.NetworkError::class.java)
    }

    // ========== download ==========

    @Test
    fun download_success_returnsSuccess() = runTest {
        val response = mockk<Response>(relaxed = true)
        every { response.code } returns 200
        every { response.isSuccessful } returns true
        val testData = "hello".toByteArray()
        every { response.body } returns testData.toResponseBody("application/octet-stream".toMediaType())
        setupCall(response)
        val result = webDavClient.download(testUrl, testUsername, testPassword, "/test.txt")
        assertThat(result).isInstanceOf(WebDavResult.Success::class.java)
        assertThat((result as WebDavResult.Success).data).isEqualTo(testData)
    }

    @Test
    fun download_notFound_returnsNotFound() = runTest {
        setupCall(mockErrorResponse(404))
        val result = webDavClient.download(testUrl, testUsername, testPassword, "/test.txt")
        assertThat(result).isEqualTo(WebDavResult.NotFound)
    }

    @Test
    fun download_authError_returnsAuthError() = runTest {
        setupCall(mockErrorResponse(401))
        val result = webDavClient.download(testUrl, testUsername, testPassword, "/test.txt")
        assertThat(result).isInstanceOf(WebDavResult.AuthError::class.java)
    }

    @Test
    fun download_timeout_returnsTimeout() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws SocketTimeoutException("read timed out")
        every { okHttpClient.newCall(any()) } returns call
        val result = webDavClient.download(testUrl, testUsername, testPassword, "/test.txt")
        assertThat(result).isEqualTo(WebDavResult.Timeout)
    }

    @Test
    fun download_networkError_returnsNetworkError() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws IOException("connection reset")
        every { okHttpClient.newCall(any()) } returns call
        val result = webDavClient.download(testUrl, testUsername, testPassword, "/test.txt")
        assertThat(result).isInstanceOf(WebDavResult.NetworkError::class.java)
    }

    // ========== listFiles ==========

    @Test
    fun listFiles_success_returnsFileList() = runTest {
        val xmlBody = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
            <d:response>
                <d:href>/dav/backup/</d:href>
            </d:response>
            <d:response>
                <d:href>/dav/backup/backup_20240101.json</d:href>
                <d:propstat><d:prop>
                    <d:getcontentlength>12345</d:getcontentlength>
                    <d:getlastmodified>Mon, 01 Jan 2024 12:00:00 GMT</d:getlastmodified>
                </d:prop></d:propstat>
            </d:response>
            </d:multistatus>
        """.trimIndent()
        val response = mockk<Response>(relaxed = true)
        every { response.code } returns 207
        every { response.isSuccessful } returns true
        every { response.body } returns xmlBody.toResponseBody("application/xml".toMediaType())
        setupCall(response)
        val result = webDavClient.listFiles(testUrl, testUsername, testPassword, "/backup/")
        assertThat(result).isInstanceOf(WebDavResult.Success::class.java)
        val files = (result as WebDavResult.Success).data
        assertThat(files).hasSize(1)
        assertThat(files[0].name).isEqualTo("backup_20240101.json")
        assertThat(files[0].size).isEqualTo(12345L)
    }

    @Test
    fun listFiles_nonMultiStatus_returnsError() = runTest {
        setupCall(mockErrorResponse(500))
        val result = webDavClient.listFiles(testUrl, testUsername, testPassword, "/backup/")
        assertThat(result).isInstanceOf(WebDavResult.NetworkError::class.java)
    }

    @Test
    fun listFiles_notFound_returnsNotFound() = runTest {
        setupCall(mockErrorResponse(404))
        val result = webDavClient.listFiles(testUrl, testUsername, testPassword, "/backup/")
        assertThat(result).isEqualTo(WebDavResult.NotFound)
    }

    @Test
    fun listFiles_authError_returnsAuthError() = runTest {
        setupCall(mockErrorResponse(401))
        val result = webDavClient.listFiles(testUrl, testUsername, testPassword, "/backup/")
        assertThat(result).isInstanceOf(WebDavResult.AuthError::class.java)
    }

    @Test
    fun listFiles_timeout_returnsTimeout() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws SocketTimeoutException("connect timed out")
        every { okHttpClient.newCall(any()) } returns call
        val result = webDavClient.listFiles(testUrl, testUsername, testPassword, "/backup/")
        assertThat(result).isEqualTo(WebDavResult.Timeout)
    }

    @Test
    fun listFiles_networkError_returnsNetworkError() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws IOException("DNS resolution failed")
        every { okHttpClient.newCall(any()) } returns call
        val result = webDavClient.listFiles(testUrl, testUsername, testPassword, "/backup/")
        assertThat(result).isInstanceOf(WebDavResult.NetworkError::class.java)
    }

    // ========== ensureRemotePath ==========

    @Test
    fun ensureRemotePath_success_returnsSuccess() = runTest {
        setupCall(mockSuccessResponse(201))
        val result = webDavClient.ensureRemotePath(testUrl, testUsername, testPassword, "/backup/subdir")
        assertThat(result).isInstanceOf(WebDavResult.Success::class.java)
    }

    @Test
    fun ensureRemotePath_alreadyExists_returnsSuccess() = runTest {
        setupCall(mockErrorResponse(405)) // 405 Method Not Allowed = already exists
        val result = webDavClient.ensureRemotePath(testUrl, testUsername, testPassword, "/backup/subdir")
        assertThat(result).isInstanceOf(WebDavResult.Success::class.java)
    }

    @Test
    fun ensureRemotePath_authError_returnsAuthError() = runTest {
        setupCall(mockErrorResponse(401))
        val result = webDavClient.ensureRemotePath(testUrl, testUsername, testPassword, "/backup/subdir")
        assertThat(result).isInstanceOf(WebDavResult.AuthError::class.java)
    }

    @Test
    fun ensureRemotePath_timeout_returnsTimeout() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws SocketTimeoutException("connect timed out")
        every { okHttpClient.newCall(any()) } returns call
        val result = webDavClient.ensureRemotePath(testUrl, testUsername, testPassword, "/backup/subdir")
        assertThat(result).isEqualTo(WebDavResult.Timeout)
    }

    @Test
    fun ensureRemotePath_networkError_returnsNetworkError() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws IOException("connection refused")
        every { okHttpClient.newCall(any()) } returns call
        val result = webDavClient.ensureRemotePath(testUrl, testUsername, testPassword, "/backup/subdir")
        assertThat(result).isInstanceOf(WebDavResult.NetworkError::class.java)
    }
}
