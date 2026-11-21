package top.mcxiafeng.badger.network.auth

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.core.PublicApi
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** 所有 HTTP 由内存 interceptor 回答，禁止真实网络。 */
class AuthApiTest {
    private lateinit var store: AuthSessionStore
    private lateinit var accounts: AccountStore
    private lateinit var publicApi: PublicApi
    private var currentUrl = BASE
    private val requests = Collections.synchronizedList(mutableListOf<Request>())

    @Before
    fun setup() {
        store = mockk(relaxed = true)
        accounts = mockk(relaxed = true)
        publicApi = mockk(relaxed = true)
        every { publicApi.serverUrl } answers { currentUrl }
    }

    @Test
    fun login_usesRealV1ShapeAndActivatesBoundAccountWithFreshCursor() = runBlocking {
        val saved = slot<AuthSession>()
        every { store.save(capture(saved)) } returns Unit
        val api = api { chain -> response(chain.request(), if (isInstance(chain.request())) INSTANCE else LOGIN) }
        val result = api.login("alice", "synthetic-password", BASE)
        assertThat(result).isInstanceOf(HttpResult.Success::class.java)
        assertThat(requests.map { it.url.encodedPath }).containsExactly("/api/v1/instance", "/api/v1/auth/login").inOrder()
        val request = requests.last()
        assertThat(request.header("X-Badger-Client")).isEqualTo("android")
        assertThat(request.header("Authorization")).isNull()
        assertThat(request.header("Cookie")).isNull()
        val payload = JsonParser.parseString(body(request)).asJsonObject
        assertThat(payload.keySet()).containsExactly("serverId", "dataEpoch", "loginName", "password")
        assertThat(payload.get("serverId").asString).isEqualTo(SERVER)
        assertThat(payload.get("dataEpoch").asString).isEqualTo(EPOCH)
        assertThat(payload.get("loginName").asString).isEqualTo("alice")
        assertThat(saved.captured.baseUrl).isEqualTo(BASE)
        assertThat(saved.captured.expiresAt).isEqualTo(EXPIRY)
        assertThat(saved.captured.syncVersion).isEqualTo(0L)
        verify { accounts.activateRemote(SERVER, USER, EPOCH, "alice", BASE) }
        verify { publicApi.recordSuccessBase(BASE) }
    }

    @Test
    fun login_failedAccountActivationClearsSessionAndReturnsFailure() = runBlocking {
        every { accounts.activateRemote(any(), any(), any(), any(), any()) } throws IllegalStateException("synthetic storage failure")
        val api = api { chain -> response(chain.request(), if (isInstance(chain.request())) INSTANCE else LOGIN) }
        assertThat(api.login("alice", "synthetic-password")).isInstanceOf(HttpResult.Failure::class.java)
        verify { store.clear() }
        verify { accounts.switchToLocal() }
        verify(exactly = 0) { publicApi.recordSuccessBase(any()) }
    }

    @Test
    fun login_httpFailureIsReturnedWithoutCreatingSession() = runBlocking {
        val api = api { chain ->
            response(chain.request(), if (isInstance(chain.request())) INSTANCE else "{\"error\":{\"code\":\"INVALID_CREDENTIALS\"}}",
                if (isInstance(chain.request())) OK else UNAUTHORIZED)
        }
        val result = api.login("alice", "synthetic-password") as HttpResult.Failure
        assertThat(result.code).isEqualTo(UNAUTHORIZED)
        assertThat(result.errorType).isEqualTo(HttpResult.ErrorType.AUTH)
        verify(exactly = 0) { store.save(any()) }
        verify(exactly = 0) { accounts.activateRemote(any(), any(), any(), any(), any()) }
    }

    @Test
    fun login_rejectsMalformedExpiredAndMismatchingResponses() = runBlocking {
        val invalid = listOf("{}", "{\"data\":{\"token\":\"old\"}}", LOGIN.replace(USER, "not-a-uuid"),
            LOGIN.replace(SERVER, OTHER), LOGIN.replace(EPOCH, OTHER), LOGIN.replace(EXPIRY, "2000-01-01T00:00:00.000Z"),
            LOGIN.replace("\"$TOKEN\"", "null"))
        for (payload in invalid) {
            val api = api { chain -> response(chain.request(), if (isInstance(chain.request())) INSTANCE else payload) }
            assertThat(api.login("alice", "synthetic-password")).isInstanceOf(HttpResult.Failure::class.java)
        }
        verify(exactly = 0) { store.save(any()) }
        verify(exactly = 0) { accounts.activateRemote(any(), any(), any(), any(), any()) }
    }

    @Test
    fun login_rejectsUninstalledOrUnsupportedInstanceBeforeSendingPassword() = runBlocking {
        for (descriptor in listOf(INSTANCE.replace("true", "false"), INSTANCE.replace("\"min\":\"1\"", "\"min\":\"2\""), "{}")) {
            val api = api { chain -> response(chain.request(), descriptor) }
            assertThat(api.login("alice", "synthetic-password")).isInstanceOf(HttpResult.Failure::class.java)
        }
        assertThat(requests.all { isInstance(it) }).isTrue()
        verify(exactly = 0) { store.save(any()) }
    }

    @Test
    fun login_addressChangedDuringResponseCannotSaveOrActivate() = runBlocking {
        val api = api { chain ->
            if (!isInstance(chain.request())) currentUrl = "https://other.example"
            response(chain.request(), if (isInstance(chain.request())) INSTANCE else LOGIN)
        }
        assertCancelled { api.login("alice", "synthetic-password", BASE) }
        assertThat(requests.all { it.url.host == "auth.example" }).isTrue()
        verify(exactly = 0) { store.save(any()) }
        verify(exactly = 0) { accounts.activateRemote(any(), any(), any(), any(), any()) }
    }

    @Test
    fun login_newerAttemptPreventsOldResponseFromOverwritingAccount() = runBlocking {
        val oldReached = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        val api = api { chain ->
            if (!isInstance(chain.request()) && body(chain.request()).contains("alice")) {
                oldReached.countDown()
                check(releaseOld.await(WAIT_SECONDS, TimeUnit.SECONDS))
            }
            response(chain.request(), if (isInstance(chain.request())) INSTANCE else LOGIN)
        }
        val old = async(Dispatchers.Default) { api.login("alice", "synthetic-password") }
        await(oldReached)
        try {
            assertThat(api.login("bob", "synthetic-password")).isInstanceOf(HttpResult.Success::class.java)
        } finally { releaseOld.countDown() }
        assertCancelled { old.await() }
        verify(exactly = 1) { store.save(any()) }
        verify(exactly = 1) { accounts.activateRemote(SERVER, USER, EPOCH, "bob", BASE) }
    }

    @Test
    fun login_coroutineCancellationCancelsCallAndCannotSave() = runBlocking {
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val inFlight = AtomicReference<Call>()
        val api = api { chain ->
            inFlight.set(chain.call())
            reached.countDown()
            check(release.await(WAIT_SECONDS, TimeUnit.SECONDS))
            response(chain.request(), INSTANCE)
        }
        val job = async(Dispatchers.Default) { api.login("alice", "synthetic-password") }
        await(reached)
        job.cancel()
        try { assertThat(inFlight.get().isCanceled()).isTrue() } finally { release.countDown() }
        assertCancelled { job.await() }
        verify(exactly = 0) { store.save(any()) }
    }

    @Test
    fun login_explicitInvalidationPreventsSaveEvenAfterResponseArrives() = runBlocking {
        lateinit var api: AuthApi
        api = api { chain ->
            if (!isInstance(chain.request())) api.cancelPendingLogin()
            response(chain.request(), if (isInstance(chain.request())) INSTANCE else LOGIN)
        }
        assertCancelled { api.login("alice", "synthetic-password") }
        verify(exactly = 0) { store.save(any()) }
    }

    @Test
    fun redirectsAreNotFollowedAndCannotCreateSession() = runBlocking {
        val api = api { chain -> response(chain.request(), "", REDIRECT).newBuilder()
            .header("Location", "https://other.example/capture").build() }
        val result = api.login("alice", "synthetic-password") as HttpResult.Failure
        assertThat(result.code).isEqualTo(REDIRECT)
        assertThat(requests).hasSize(1)
        verify(exactly = 0) { store.save(any()) }
    }

    @Test
    fun logout_offlineStillClearsLocalAndNeverUsesNewPublicHost() = runBlocking {
        every { store.snapshot() } returns session()
        currentUrl = "https://other.example"
        val api = api { throw IOException("synthetic offline") }
        val result = api.logout() as HttpResult.Failure
        assertThat(result.errorType).isEqualTo(HttpResult.ErrorType.NETWORK)
        assertThat(requests.single().url.host).isEqualTo("auth.example")
        assertThat(requests.single().header("Authorization")).isNull()
        verify { store.clear() }
        verify { accounts.switchToLocal() }
    }

    @Test
    fun logout_checksBoundInstanceThenSendsCorrectRevokeContract() = runBlocking {
        every { store.snapshot() } returns session()
        currentUrl = "https://other.example"
        val api = api { chain -> response(chain.request(), if (isInstance(chain.request())) INSTANCE else "",
            if (isInstance(chain.request())) OK else NO_CONTENT) }
        assertThat(api.logout()).isInstanceOf(HttpResult.Success::class.java)
        val revoke = requests.last()
        assertThat(revoke.url.toString()).isEqualTo("$BASE/api/v1/auth/logout")
        assertThat(revoke.header("Authorization")).isEqualTo("Bearer $TOKEN")
        val payload = JsonParser.parseString(body(revoke)).asJsonObject
        assertThat(payload.keySet()).containsExactly("mutationId", "dataEpoch")
        assertThat(AuthSession.canonicalUuid(payload.get("mutationId").asString)).isTrue()
        assertThat(payload.get("dataEpoch").asString).isEqualTo(EPOCH)
        verify { store.clear() }
        verify { accounts.switchToLocal() }
    }

    @Test
    fun logout_survivesCallerCancellationFromSpaceSwitch() = runBlocking<Unit> {
        every { store.snapshot() } returns session()
        val caller = Job()
        every { accounts.switchToLocal() } answers {
            caller.cancel()
            AccountScope("local", null, null, null, "本地用户", "", "badger_database")
        }
        val afterCancellation = AtomicBoolean(false)
        val api = api { chain ->
            val instance = isInstance(chain.request())
            if (!instance) afterCancellation.set(caller.isCancelled)
            response(chain.request(), if (instance) INSTANCE else "", if (instance) OK else NO_CONTENT)
        }
        val operation = async(Dispatchers.Default + caller) { api.logout() }
        operation.join()
        assertThat(afterCancellation.get()).isTrue()
        assertThat(requests.map { it.url.encodedPath }).containsExactly("/api/v1/instance", "/api/v1/auth/logout").inOrder()
        verify(exactly = 1) { store.clear() }
        verify(exactly = 1) { accounts.switchToLocal() }
    }

    @Test
    fun logout_fromOldSpaceNeverClearsNewSession() = runBlocking<Unit> {
        every { accounts.current() } returns AccountScope("current", SERVER, USER, EPOCH, "当前账号", BASE, "current.db")
        every { store.snapshot() } returns session()
        val api = api { error("stale exit must not start HTTP") }
        assertThat(api.logout(expectedScopeKey = "old")).isInstanceOf(HttpResult.Failure::class.java)
        verify(exactly = 0) { store.clear() }
        verify(exactly = 0) { accounts.switchToLocal() }
        assertThat(requests).isEmpty()
    }

    @Test
    fun logout_instanceMismatchNeverSendsToken() = runBlocking {
        every { store.snapshot() } returns session()
        val api = api { chain -> response(chain.request(), INSTANCE.replace(SERVER, OTHER)) }
        assertThat(api.logout()).isInstanceOf(HttpResult.Failure::class.java)
        assertThat(requests).hasSize(1)
        assertThat(requests.single().header("Authorization")).isNull()
        verify { store.clear() }
        verify { accounts.switchToLocal() }
    }

    @Test
    fun legacyRegistrationAndReset_areUnavailableWithoutNetwork() = runBlocking {
        val api = api { error("must not use legacy authentication") }
        assertThat(api.registerPolicy()!!.allowRegister).isFalse()
        assertThat(api.register("alice", "a@example.test", "synthetic-password", "synthetic-password")).isInstanceOf(HttpResult.Failure::class.java)
        assertThat(api.sendVerificationCode("a@example.test")).isInstanceOf(HttpResult.Failure::class.java)
        assertThat(api.forgotPassword("a@example.test", "123456", "synthetic-password", "synthetic-password")).isInstanceOf(HttpResult.Failure::class.java)
        assertThat(requests).isEmpty()
    }

    private fun api(handler: (Interceptor.Chain) -> Response): AuthApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request())
            handler(chain)
        }.build()
        return AuthApi(client, store, mockk(relaxed = true), publicApi, accounts)
    }

    private fun session() = AuthSession(USER, TOKEN, "alice", serverId = SERVER, dataEpoch = EPOCH, baseUrl = BASE, expiresAt = EXPIRY)
    private fun isInstance(request: Request) = request.url.encodedPath == "/api/v1/instance"
    private fun body(request: Request): String = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
    private fun response(request: Request, text: String, code: Int = OK) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(code).message("synthetic").body(text.toResponseBody("application/json".toMediaType())).build()

    private suspend fun await(latch: CountDownLatch) = withContext(Dispatchers.IO) {
        check(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "synthetic HTTP did not start" }
    }

    private suspend fun assertCancelled(block: suspend () -> Any?) {
        try {
            block()
            throw AssertionError("expected cancellation")
        } catch (expected: CancellationException) {
            assertThat(expected).isNotNull()
        }
    }

    companion object {
        private const val BASE = "https://auth.example"
        private const val SERVER = "00000000-0000-0000-0000-000000000002"
        private const val USER = "00000000-0000-0000-0000-000000000001"
        private const val EPOCH = "00000000-0000-0000-0000-000000000003"
        private const val OTHER = "00000000-0000-0000-0000-000000000004"
        private const val TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val EXPIRY = "2099-01-01T00:00:00.000Z"
        private const val OK = 200
        private const val UNAUTHORIZED = 401
        private const val NO_CONTENT = 204
        private const val REDIRECT = 302
        private const val WAIT_SECONDS = 5L
        private const val INSTANCE = """{"serverId":"$SERVER","dataEpoch":"$EPOCH","installed":true,"protocol":{"min":"1","max":"1"},"schema":{"min":"1","max":"1"}}"""
        private const val LOGIN = """{"accountIdentity":{"serverId":"$SERVER","userId":"$USER","dataEpoch":"$EPOCH","ownerRevision":"0","roles":["user"],"selfCard":{"id":"$OTHER","version":"1","profileId":"$OTHER"}},"session":{"token":"$TOKEN","expiresAt":"$EXPIRY"}}"""
    }
}
