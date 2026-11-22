package top.mcxiafeng.badger.data.sync

import android.graphics.BitmapFactory
import android.util.Log
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okio.FileSystem
import okio.HashingSource
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.blackholeSink
import okio.buffer
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.auth.AuthSession
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncTransport
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

class PrivateAssetKeyer : Keyer<PrivateAssetImage> {
    override fun key(data: PrivateAssetImage, options: Options): String = data.cacheKey
}

/** One ImageLoader cache; permits cover fetching, queued sources and decoding until source close. */
@Singleton
class PrivateAssetFetcherFactory @Inject constructor(
    private val transport: SyncTransport,
    private val sessions: AuthSessionStore,
    private val accounts: AccountStore,
) : Fetcher.Factory<PrivateAssetImage> {
    private val transfers = Semaphore(MAX_TRANSFERS)
    // Match the server's single cold-thumbnail generator; cache hits and original reads still overlap.
    private val thumbnailRequests = Semaphore(MAX_THUMBNAIL_REQUESTS)
    private val stripes = Array(LOCK_STRIPES) { Mutex() }

    override fun create(data: PrivateAssetImage, options: Options, imageLoader: ImageLoader): Fetcher = Fetcher {
        transfers.acquire()
        var file: SourceFile? = null
        try {
            val loaded = stripes[Math.floorMod(data.cacheKey.hashCode(), stripes.size)].withLock {
                load(data, options, imageLoader.diskCache)
            }
            file = loaded
            checkScope(data)
            val closed = AtomicBoolean()
            val release = AutoCloseable {
                if (closed.compareAndSet(false, true)) {
                    try { loaded.closeable.close() } finally { transfers.release() }
                }
            }
            SourceFetchResult(ImageSource(loaded.path, loaded.fileSystem, closeable = release), "image/png", loaded.source)
        } catch (e: Throwable) {
            try { file?.closeable?.close() } finally { transfers.release() }
            if (e is CancellationException) Log.d(TAG, "私有图片读取已取消")
            else Log.e(TAG, "私有图片读取失败，保留资产引用；type=${e.javaClass.simpleName}")
            throw e
        }
    }

    private suspend fun load(data: PrivateAssetImage, options: Options, cache: DiskCache?): SourceFile {
        checkScope(data)
        if (options.diskCachePolicy.readEnabled) {
            cached(cache, data)?.let { return it }
        }
        check(options.networkCachePolicy.readEnabled) { "图片未缓存，当前请求禁止联网读取" }
        val bytes = download(data, session(data))
        checkScope(data)
        if (options.diskCachePolicy.writeEnabled) store(cache, data, bytes)?.let { return it }
        // Decode from a file even when persistence is disabled or the cache cannot accept a write.
        val temporary = File.createTempFile("badger-private-", ".png", options.context.cacheDir)
        try { temporary.outputStream().use { it.write(bytes) } }
        catch (e: Throwable) { temporary.delete(); throw e }
        return SourceFile(temporary.toOkioPath(), FileSystem.SYSTEM, DataSource.NETWORK, AutoCloseable {
            if (!temporary.delete() && temporary.exists()) Log.w(TAG, "图片解码临时文件未能清理")
        })
    }

    private fun checkScope(data: PrivateAssetImage) {
        check(accounts.current().key == data.scope.key) { "图片所属账号已切换" }
    }

    private fun session(data: PrivateAssetImage): AuthSession {
        val session = requireNotNull(sessions.read()) { "图片尚未缓存，请登录后联网读取" }
        require(session.serverId == data.scope.serverId && session.userUuid == data.scope.userId &&
            session.dataEpoch == data.scope.dataEpoch && session.baseUrl == accounts.current().baseUrl) {
            "当前会话与图片所属用户空间不匹配"
        }
        return session
    }

    private suspend fun download(data: PrivateAssetImage, session: AuthSession): ByteArray {
        if (data.thumbnail) {
            try { return downloadThumbnail(data, session) }
            catch (e: LegacyImageServer) {
                Log.d(TAG, "旧服务器未识别缩略图请求，改为有界读取并校验原图")
            }
        }
        val bytes = transport.bytes(session, "/api/v1/assets/${data.reference.id}",
            maxBytes = RemoteAssetReference.MAX_IMAGE_BYTES)
        require(bytes.size.toLong() == data.reference.size && SyncJson.hash(bytes) == data.reference.sha256) { "云端原图校验失败" }
        Log.d(TAG, "已校验并按需读取原图")
        return bytes
    }

    private suspend fun downloadThumbnail(data: PrivateAssetImage, session: AuthSession): ByteArray = thumbnailRequests.withPermit {
        var expectedHash: String? = null
        val bytes = transport.bytes(session, "/api/v1/assets/${data.reference.id}?variant=thumbnail",
            maxBytes = MAX_THUMBNAIL_BYTES, validateHeaders = { response ->
                val variant = response.header(VARIANT_HEADER)
                if (variant == null) throw LegacyImageServer()
                require(variant == "thumbnail") { "缩略图响应变体不正确" }
                require(response.header(CANONICAL_HASH_HEADER) == data.reference.sha256) { "缩略图来源不一致" }
                expectedHash = response.header(HASH_HEADER)
                require(expectedHash?.matches(HASH) == true) { "缩略图缺少完整性描述" }
            })
        require(SyncJson.hash(bytes) == expectedHash) { "缩略图校验失败" }
        require(bytes.size in 1..MAX_THUMBNAIL_BYTES)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ByteArrayInputStream(bytes).use { BitmapFactory.decodeStream(it, null, bounds) }
        validateThumbnailBounds(bounds)
        Log.d(TAG, "已校验并按需读取缩略图")
        bytes
    }

    private fun cached(cache: DiskCache?, data: PrivateAssetImage): SourceFile? {
        val snapshot = cache?.openSnapshot(data.cacheKey) ?: return null
        try {
            val size = cache.fileSystem.metadata(snapshot.data).size ?: error("图片缓存缺少长度")
            require(size in 1..RemoteAssetReference.MAX_IMAGE_BYTES.toLong())
            require(cache.fileSystem.metadata(snapshot.metadata).size == HASH_BYTES)
            val expectedHash = cache.fileSystem.read(snapshot.metadata) { readUtf8() }
            val hashing = HashingSource.sha256(cache.fileSystem.source(snapshot.data))
            hashing.buffer().use { it.readAll(blackholeSink()) }
            require(hashing.hash.hex() == expectedHash)
            if (expectedHash == data.reference.sha256) require(size == data.reference.size)
            else validateCachedThumbnail(cache, snapshot, data, size)
            return source(cache, snapshot, DataSource.DISK)
        } catch (e: Exception) {
            snapshot.close()
            Log.e(TAG, "图片缓存损坏，丢弃缓存并重新校验下载", e)
            cache.remove(data.cacheKey)
            return null
        }
    }

    private fun validateCachedThumbnail(cache: DiskCache, snapshot: DiskCache.Snapshot, data: PrivateAssetImage, size: Long) {
        require(data.thumbnail && size <= MAX_THUMBNAIL_BYTES)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cache.fileSystem.source(snapshot.data).buffer().inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        validateThumbnailBounds(bounds)
    }

    private fun store(cache: DiskCache?, data: PrivateAssetImage, bytes: ByteArray): SourceFile? {
        val editor = cache?.openEditor(data.cacheKey) ?: return null
        try {
            cache.fileSystem.write(editor.data) { write(bytes) }
            cache.fileSystem.write(editor.metadata) { writeUtf8(SyncJson.hash(bytes)) }
            return editor.commitAndOpenSnapshot()?.let { source(cache, it, DataSource.NETWORK) }
        } catch (e: Exception) {
            editor.abort()
            Log.e(TAG, "图片已校验但缓存写入失败", e)
            return null
        }
    }

    private fun source(cache: DiskCache, snapshot: DiskCache.Snapshot, source: DataSource) =
        SourceFile(snapshot.data, cache.fileSystem, source, snapshot)

    private fun validateThumbnailBounds(bounds: BitmapFactory.Options) {
        require(bounds.outMimeType == "image/png" && bounds.outWidth in 1..THUMBNAIL_SIZE &&
            bounds.outHeight in 1..THUMBNAIL_SIZE) { "缩略图尺寸或格式不合法" }
    }

    private data class SourceFile(val path: Path, val fileSystem: FileSystem, val source: DataSource, val closeable: AutoCloseable)
    private class LegacyImageServer : IOException("Server returned the original representation")

    companion object {
        private const val TAG = "PrivateAssetFetcherFactoryTester"
        private const val MAX_TRANSFERS = 2
        private const val MAX_THUMBNAIL_REQUESTS = 1
        private const val LOCK_STRIPES = 16
        private const val MAX_THUMBNAIL_BYTES = 512 * 1024
        private const val THUMBNAIL_SIZE = 256
        private const val HASH_BYTES = 64L
        private const val VARIANT_HEADER = "X-Badger-Asset-Variant"
        private const val HASH_HEADER = "X-Badger-Asset-Sha256"
        private const val CANONICAL_HASH_HEADER = "X-Badger-Asset-Canonical-Sha256"
        private val HASH = Regex("[0-9a-f]{64}")
    }
}
