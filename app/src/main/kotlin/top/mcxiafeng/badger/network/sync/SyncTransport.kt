package top.mcxiafeng.badger.network.sync

import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import top.mcxiafeng.badger.network.auth.AuthSession
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class SyncHttpException(val status: Int, val code: String, message: String,
    val fieldCodes: Set<String> = emptySet()) : IOException(message)

/** Every request uses one captured session. Redirects, cookies and shared HTTP cache are disabled. */
class SyncTransport @Inject constructor(client: OkHttpClient, private val sessions: AuthSessionStore) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).cache(null).callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    internal suspend fun json(session: AuthSession, path: String, body: JsonObject? = null): JsonObject =
        SyncJson.parse(String(bytes(session, path, body?.let {
            val encoded = SyncJson.encode(it)
            require(encoded.toByteArray(Charsets.UTF_8).size <= SyncJson.MAX_MUTATION_BYTES) { "同步请求超过大小限制" }
            encoded.toRequestBody(JSON_TYPE)
        }), Charsets.UTF_8))

    internal suspend fun bytes(
        session: AuthSession,
        path: String,
        body: RequestBody? = null,
        maxBytes: Int = SyncJson.MAX_JSON_BYTES,
        validateHeaders: (Response) -> Unit = {},
    ): ByteArray = withContext(Dispatchers.IO) {
        checkSession(session)
        val url = session.baseUrl.trimEnd('/') + path
        require(url.toHttpUrl().let { it.scheme == "http" || it.scheme == "https" })
        val builder = Request.Builder().url(url).header("Authorization", "Bearer ${session.token}")
            .header("X-Badger-Client", "android").header("X-Badger-Sync-Schema", SyncJson.SCHEMA.toString())
            .header("Cache-Control", "no-store")
        if (body == null) builder.get() else builder.post(body)
        Log.d(TAG, "同步请求 method=${if (body == null) "GET" else "POST"}")
        await(client.newCall(builder.build())).use { response ->
            if (response.isSuccessful) validateHeaders(response)
            val stream = requireNotNull(response.body) { "服务器未返回响应正文" }.byteStream()
            val data = stream.use { bounded(it, maxBytes) }
            require(data.size <= maxBytes) { "同步响应超出大小限制" }
            checkSession(session)
            if (!response.isSuccessful) throw failure(response.code, data)
            data
        }
    }

    private fun bounded(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        while (output.size() <= maximum) {
            val count = input.read(buffer, 0, minOf(buffer.size, maximum + 1 - output.size()))
            if (count < 0) break
            if (count > 0) output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun checkSession(expected: AuthSession) {
        val current = sessions.read()
        if (current == null || current.token != expected.token || current.serverId != expected.serverId ||
            current.userUuid != expected.userUuid || current.dataEpoch != expected.dataEpoch || current.baseUrl != expected.baseUrl) {
            Log.d(TAG, "会话已变化，丢弃旧同步请求")
            throw SyncHttpException(HTTP_UNAUTHORIZED, "SESSION_CHANGED", "会话已变化，请重新登录后同步")
        }
    }

    private fun failure(status: Int, bytes: ByteArray): SyncHttpException {
        val error = try {
            SyncJson.parse(String(bytes, Charsets.UTF_8))["error"]?.asJsonObject
        } catch (e: Exception) {
            Log.e(TAG, "同步错误响应格式不合法，status=$status, type=${e.javaClass.simpleName}")
            null
        }
        val codes = error?.get("fieldErrors")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject?.let { item -> SyncJson.text(item, "code") } }?.toSet().orEmpty()
        val message = when {
            "REFERENCE_IN_USE" in codes -> "字段仍被云端资料使用，请处理关联冲突"
            "REFERENCE_NOT_FOUND" in codes -> "关联资料已变化，请处理冲突"
            "NAME_UNAVAILABLE" in codes -> "云端已有同名标签，请重命名后重试"
            else -> error?.let { SyncJson.text(it, "message") } ?: "云端请求失败，请稍后重试"
        }
        return SyncHttpException(status, error?.let { SyncJson.text(it, "code") }.orEmpty(), message, codes)
    }

    private suspend fun await(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    companion object {
        private const val TAG = "SyncTransportTester"
        private const val HTTP_UNAUTHORIZED = 401
        private const val REQUEST_TIMEOUT_SECONDS = 60L
        private const val READ_BUFFER_BYTES = 16 * 1024
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal data class VerifiedSnapshot(val revision: String, val roots: List<SyncRoot>)

internal class SnapshotReader(private val transport: SyncTransport) {
    suspend fun read(session: AuthSession, minRevision: String): VerifiedSnapshot {
        val metadata = transport.json(session, "/api/v1/sync/snapshots", SyncJson.obj(
            "dataEpoch" to session.dataEpoch, "knownRevision" to null, "minRevision" to minRevision))
        identity(metadata, session)
        require(SyncJson.long(metadata, "schemaVersion") == SyncJson.SCHEMA.toLong()) { "服务器同步格式需要升级" }
        require(SyncJson.text(metadata, "status") == "snapshot") { "完整快照响应不完整" }
        val revision = requireNotNull(SyncJson.text(metadata, "revision"))
        require(SyncJson.counter(revision) >= SyncJson.counter(minRevision)) { "服务器资料水位回退，已暂停同步" }
        verifyManifest(metadata)
        val id = SyncJson.id(requireNotNull(SyncJson.text(metadata, "snapshotId")))
        val descriptors = SyncJson.array(metadata, "pages")
        require(descriptors.size().toLong() == SyncJson.long(metadata, "pageCount"))
        var token = SyncJson.text(metadata, "firstPageToken")
        var bytes = 0L
        val roots = linkedMapOf<String, SyncRoot>()
        descriptors.forEachIndexed { index, descriptor ->
            val pageToken = requireNotNull(token) { "完整快照缺页" }
            require(PAGE_TOKEN.matches(pageToken))
            val path = "/api/v1/sync/snapshots/$id?pageToken=$pageToken"
            val raw = transport.bytes(session, path)
            val pageText = String(raw, Charsets.UTF_8)
            val page = SyncJson.parse(pageText)
            identity(page, session)
            require(SyncJson.text(page, "snapshotId") == id && SyncJson.text(page, "revision") == revision)
            val wireRows = SyncJson.wireRows(pageText)
            val expected = descriptor.asJsonObject
            require(SyncJson.long(page, "pageNumber") == index.toLong() && SyncJson.long(expected, "pageNumber") == index.toLong())
            require(wireRows.size.toLong() == SyncJson.long(expected, "byteCount") && SyncJson.long(page, "byteCount") == wireRows.size.toLong())
            require(SyncJson.hash(wireRows) == SyncJson.text(expected, "sha256") && SyncJson.text(page, "sha256") == SyncJson.text(expected, "sha256"))
            val rows = SyncJson.array(page, "rows")
            require(rows.size().toLong() == SyncJson.long(expected, "rowCount") && SyncJson.long(page, "rowCount") == rows.size().toLong())
            rows.forEach { element ->
                val root = SyncRoot.read(element.asJsonObject)
                require(SyncJson.counter(root.version).signum() > 0)
                require(roots.put(root.key, root) == null) { "快照包含重复记录" }
            }
            bytes += wireRows.size
            require(bytes <= SyncJson.MAX_SNAPSHOT_BYTES)
            token = SyncJson.text(page, "nextPageToken")
        }
        require(token == null && bytes == SyncJson.long(metadata, "totalBytes") && roots.size.toLong() == SyncJson.long(metadata, "totalRows"))
        FullSyncContract.snapshot(roots.values.toList())
        return VerifiedSnapshot(revision, roots.values.toList())
    }

    private fun identity(obj: JsonObject, session: AuthSession) {
        require(SyncJson.text(obj, "serverId") == session.serverId && SyncJson.text(obj, "userId") == session.userUuid &&
            SyncJson.text(obj, "dataEpoch") == session.dataEpoch) { "服务器身份或资料世代已变化，已暂停同步" }
    }

    private fun verifyManifest(metadata: JsonObject) {
        require(SyncJson.text(metadata, "digestAlgorithm") == "badger-jcs-sha256-1")
        val manifest = metadata.deepCopy()
        listOf("status", "digestAlgorithm", "digest", "firstPageToken").forEach(manifest::remove)
        require(SyncJson.hash(SyncJson.canonical(manifest).toByteArray(Charsets.UTF_8)) == SyncJson.text(metadata, "digest")) {
            "完整快照校验失败，原资料已保留"
        }
    }

    companion object {
        private val PAGE_TOKEN = Regex("[A-Za-z0-9_-]{1,256}")
    }
}
