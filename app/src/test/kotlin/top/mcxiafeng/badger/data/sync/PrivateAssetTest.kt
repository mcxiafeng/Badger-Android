package top.mcxiafeng.badger.data.sync

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import coil3.serviceLoaderEnabled
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.fetch.SourceFetchResult
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import top.mcxiafeng.badger.data.account.AccountImageCacheInterceptor
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.auth.AuthSession
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncTransport
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PrivateAssetTest {
    @get:Rule val directory = TemporaryFolder()
    private lateinit var accounts: AccountStore
    private lateinit var scoped: AccountContext
    private lateinit var sessions: AuthSessionStore
    private lateinit var factory: PrivateAssetFetcherFactory
    private lateinit var loader: ImageLoader
    private lateinit var cache: DiskCache
    private lateinit var client: OkHttpClient
    private lateinit var original: ByteArray
    private lateinit var thumbnailBytes: ByteArray
    private var requests = 0
    private var legacy = false
    private var corrupt = false
    private var status = 200
    private var variantMarker = "thumbnail"
    private var onRequest: (() -> Unit)? = null

    @Before fun setup() {
        val base: Context = RuntimeEnvironment.getApplication()
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = File(directory.root, "files").apply { mkdirs() }
        }
        accounts = AccountStore(context)
        val serverId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        val epoch = UUID.randomUUID().toString()
        val scope = accounts.activateRemote(serverId, userId, epoch, "用户", "https://example.test")
        scoped = AccountContext(context, scope)
        sessions = AuthSessionStore(base.getSharedPreferences("image-session", Context.MODE_PRIVATE))
        sessions.save(AuthSession(userId, "A".repeat(43), "用户", serverId = serverId, dataEpoch = epoch,
            baseUrl = scope.baseUrl, expiresAt = "2099-01-01T00:00:00Z"))
        original = png(2, 2)
        thumbnailBytes = png(1, 1)
        client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            onRequest?.invoke()
            val response = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("test")
            val thumbnail = chain.request().url.queryParameter("variant") == "thumbnail" && !legacy
            if (thumbnail) {
                response.header("X-Badger-Asset-Variant", variantMarker)
                    .header("X-Badger-Asset-Sha256", SyncJson.hash(thumbnailBytes))
                    .header("X-Badger-Asset-Canonical-Sha256", SyncJson.hash(original))
            }
            val representation = if (thumbnail) thumbnailBytes else original
            val bytes = if (corrupt) representation.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() } else representation
            response.body(bytes.toResponseBody("image/png".toMediaType())).build()
        }.build()
        factory = PrivateAssetFetcherFactory(SyncTransport(client, sessions), sessions, accounts)
        cache = DiskCache.Builder().directory(directory.newFolder("cache")).maxSizeBytes(CACHE_BUDGET).build()
        loader = ImageLoader.Builder(scoped).diskCache(cache).build()
    }

    @After fun close() {
        if (::loader.isInitialized) loader.shutdown()
        if (::cache.isInitialized) cache.shutdown()
        if (::client.isInitialized) {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray() }
        finally { bitmap.recycle() }
    }

    private fun data(thumbnail: Boolean = false, id: String = UUID.randomUUID().toString()) =
        PrivateAssetImage(scoped.scope, RemoteAssetReference(id, SyncJson.hash(original), original.size.toLong()), thumbnail)

    private suspend fun open(data: PrivateAssetImage) =
        factory.create(data, Options(scoped), loader).fetch() as SourceFetchResult

    private suspend fun fetch(data: PrivateAssetImage): ByteArray =
        open(data).source.use { it.source().readByteArray() }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun coldThumbnailRequestsMatchTheServersSingleGenerator() = runTest {
        val network = mockk<SyncTransport>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        var active = 0
        var peak = 0
        coEvery { network.bytes(any(), any(), null, any(), any()) } coAnswers {
            calls++
            active++
            peak = maxOf(peak, active)
            try {
                if (calls == 1) release.await()
                val response = Response.Builder().request(Request.Builder().url("https://example.test").build())
                    .protocol(Protocol.HTTP_1_1).code(200).message("test")
                    .header("X-Badger-Asset-Variant", "thumbnail")
                    .header("X-Badger-Asset-Sha256", SyncJson.hash(thumbnailBytes))
                    .header("X-Badger-Asset-Canonical-Sha256", SyncJson.hash(original)).build()
                arg<(Response) -> Unit>(4).invoke(response)
                thumbnailBytes
            } finally { active-- }
        }
        factory = PrivateAssetFetcherFactory(network, sessions, accounts)
        val first = async { fetch(data(true, UUID(0L, 1L).toString())) }
        val second = async { fetch(data(true, UUID(0L, 2L).toString())) }
        runCurrent()
        assertThat(calls).isEqualTo(1)
        release.complete(Unit)
        advanceUntilIdle()
        assertThat(first.await().toList()).isEqualTo(thumbnailBytes.toList())
        assertThat(second.await().toList()).isEqualTo(thumbnailBytes.toList())
        assertThat(peak).isEqualTo(1)
        assertThat(calls).isEqualTo(2)
    }

    @Test fun queuedDecodesHoldPermitsAndUseFileSources() = runBlocking {
        val images = List(3) { data() }
        images.forEach { fetch(it) }
        val first = open(images[0])
        val second = open(images[1])
        val third = async(start = CoroutineStart.UNDISPATCHED) { open(images[2]) }
        assertThat(first.source.fileOrNull()).isNotNull()
        assertThat(second.source.fileOrNull()).isNotNull()
        assertThat(third.isCompleted).isFalse()
        first.source.close()
        val thirdSource = withTimeout(SOURCE_WAIT_MS) { third.await() }
        first.source.close()
        val fourth = async(start = CoroutineStart.UNDISPATCHED) { open(images[0]) }
        assertThat(fourth.isCompleted).isFalse()
        second.source.close()
        val fourthSource = withTimeout(SOURCE_WAIT_MS) { fourth.await() }
        thirdSource.source.close()
        fourthSource.source.close()
        assertThat(requests).isEqualTo(3)
    }

    @Test(timeout = PIPELINE_TIMEOUT_MS) fun referencePathChecksScopeBeforeImageMemoryCache() = runBlocking {
        val data = data(thumbnail = true)
        val file = data.reference.write(File(scoped.filesDir, "avatar${RemoteAssetReference.SUFFIX}"))
        val imageLoader = ImageLoader.Builder(scoped).diskCache(cache)
            .serviceLoaderEnabled(false)
            .fetcherCoroutineContext(Dispatchers.Unconfined)
            .decoderCoroutineContext(Dispatchers.Unconfined).components {
            add(AccountImageCacheInterceptor())
            add(PrivateAssetImageInterceptor(accounts))
            add(PrivateAssetKeyer())
            add(factory)
        }.build()
        try {
            val request = ImageRequest.Builder(scoped).data(file.path).size(128).build()
            assertThat(imageLoader.execute(request)).isInstanceOf(SuccessResult::class.java)
            assertThat(imageLoader.execute(request)).isInstanceOf(SuccessResult::class.java)
            accounts.switchToLocal()
            assertThat(imageLoader.execute(request)).isInstanceOf(ErrorResult::class.java)
            assertThat(requests).isEqualTo(1)
        } finally { imageLoader.shutdown() }
    }

    @Test fun originalIsVerifiedAndReusedOffline() = runBlocking {
        val data = data()
        assertThat(fetch(data).toList()).isEqualTo(original.toList())
        sessions.clear()
        assertThat(fetch(data).toList()).isEqualTo(original.toList())
        assertThat(requests).isEqualTo(1)
    }

    @Test fun thumbnailUsesVariantAndDistinctCacheKey() = runBlocking {
        val thumbnail = data(thumbnail = true)
        assertThat(fetch(thumbnail).toList()).isEqualTo(thumbnailBytes.toList())
        assertThat(fetch(thumbnail).toList()).isEqualTo(thumbnailBytes.toList())
        assertThat(fetch(thumbnail.copy(thumbnail = false)).toList()).isEqualTo(original.toList())
        assertThat(requests).isEqualTo(2)
        assertThat(thumbnail.cacheKey).isNotEqualTo(thumbnail.copy(thumbnail = false).cacheKey)
    }

    @Test fun oldServerResponseUsesVerifiedOriginalFallback() = runBlocking {
        legacy = true
        assertThat(fetch(data(thumbnail = true)).toList()).isEqualTo(original.toList())
        assertThat(requests).isEqualTo(2)
    }

    @Test fun corruptResponseNeverEntersCache() = runBlocking {
        val data = data()
        corrupt = true
        assertThat(runCatching { fetch(data) }.isFailure).isTrue()
        assertThat(cache.openSnapshot(data.cacheKey)).isNull()
        corrupt = false
        assertThat(fetch(data).toList()).isEqualTo(original.toList())
        assertThat(requests).isEqualTo(2)
    }

    @Test fun unknownVariantDoesNotRetryOriginal() = runBlocking {
        variantMarker = "unexpected"
        val data = data(thumbnail = true)
        assertThat(runCatching { fetch(data) }.isFailure).isTrue()
        assertThat(cache.openSnapshot(data.cacheKey)).isNull()
        assertThat(requests).isEqualTo(1)
    }

    @Test fun nonImageThumbnailIsRejectedEvenWithValidDigest() = runBlocking {
        thumbnailBytes = byteArrayOf(1, 2, 3)
        val data = data(thumbnail = true)
        assertThat(runCatching { fetch(data) }.isFailure).isTrue()
        assertThat(cache.openSnapshot(data.cacheKey)).isNull()
        assertThat(requests).isEqualTo(1)
    }

    @Test fun busyThumbnailDoesNotRetryOriginal() = runBlocking {
        val data = data(thumbnail = true)
        status = 429
        assertThat(runCatching { fetch(data) }.isFailure).isTrue()
        assertThat(cache.openSnapshot(data.cacheKey)).isNull()
        assertThat(requests).isEqualTo(1)
    }

    @Test fun corruptThumbnailDoesNotRetryOriginal() = runBlocking {
        val data = data(thumbnail = true)
        corrupt = true
        assertThat(runCatching { fetch(data) }.isFailure).isTrue()
        assertThat(cache.openSnapshot(data.cacheKey)).isNull()
        assertThat(requests).isEqualTo(1)
    }

    @Test fun damagedCacheIsDiscardedAndRefetched() = runBlocking {
        val data = data()
        fetch(data)
        val editor = requireNotNull(cache.openEditor(data.cacheKey))
        cache.fileSystem.write(editor.data) { writeUtf8("damaged") }
        editor.commit()
        assertThat(fetch(data).toList()).isEqualTo(original.toList())
        assertThat(requests).isEqualTo(2)
    }

    @Test fun switchingAccountRejectsEvenCachedImages() = runBlocking {
        val data = data()
        fetch(data)
        accounts.switchToLocal()
        assertThat(runCatching { fetch(data) }.isFailure).isTrue()
        assertThat(requests).isEqualTo(1)
    }

    @Test fun lateResponseCannotPopulateOldAccountsCache() = runBlocking {
        val data = data()
        onRequest = { accounts.switchToLocal() }
        assertThat(runCatching { fetch(data) }.isFailure).isTrue()
        assertThat(cache.openSnapshot(data.cacheKey)).isNull()
    }

    @Test fun imageEvictionPreservesDurableReferenceAndUserOriginal() = runBlocking {
        original += ByteArray(ENTRY_PADDING) { it.toByte() }
        val data = data()
        val reference = data.reference.write(File(scoped.filesDir, "avatar${RemoteAssetReference.SUFFIX}"))
        val localOriginal = File(scoped.filesDir, "user-original.png").apply { writeBytes(original) }
        repeat(CAPACITY_ENTRIES) { fetch(data(id = UUID.randomUUID().toString())) }
        withTimeout(CACHE_CLEANUP_TIMEOUT_MS) {
            while (cache.size > CACHE_BUDGET) delay(CACHE_POLL_INTERVAL_MS)
        }
        assertThat(cache.size).isAtMost(CACHE_BUDGET)
        assertThat(RemoteAssetReference.read(reference)).isEqualTo(data.reference)
        assertThat(localOriginal.readBytes().toList()).isEqualTo(original.toList())
    }

    companion object {
        private const val PIPELINE_TIMEOUT_MS = 15_000L
        private const val CACHE_CLEANUP_TIMEOUT_MS = 10_000L
        private const val CACHE_POLL_INTERVAL_MS = 10L
        private const val SOURCE_WAIT_MS = 2_000L
        private const val CACHE_BUDGET = 1024L * 1024
        private const val ENTRY_PADDING = 64 * 1024
        private const val CAPACITY_ENTRIES = 24
    }
}
