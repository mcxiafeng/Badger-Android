package top.mcxiafeng.badger.data.sync

import android.util.Log
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import java.io.File
import javax.inject.Inject

data class PrivateAssetImage(val scope: AccountScope, val reference: RemoteAssetReference, val thumbnail: Boolean) {
    val cacheKey: String get() = "private-image-v1:${scope.key}:${reference.id}:${reference.sha256}:${if (thumbnail) "thumbnail" else "original"}"
}

/** Resolve durable references before Coil looks up a decoded image, retaining a captured account. */
class PrivateAssetImageInterceptor @Inject constructor(private val accounts: AccountStore) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val path = when (val data = chain.request.data) {
            is File -> data.path
            is String -> data.substringBefore('?').removePrefix("file://")
            else -> return chain.proceed()
        }
        if (!path.endsWith(RemoteAssetReference.SUFFIX)) return chain.proceed()
        val scoped = requireNotNull(AccountContext.find(chain.request.context)) { "图片缺少固定用户空间" }
        check(accounts.current().key == scoped.scope.key && !scoped.scope.isLocal) { "图片所属账号已切换" }
        val source = File(path).canonicalFile
        require(source.toPath().startsWith(scoped.filesDir.canonicalFile.toPath())) { "图片不属于当前用户空间" }
        val reference = withContext(Dispatchers.IO) { RemoteAssetReference.read(source) }
        val thumbnail = (chain.size.width as? Dimension.Pixels)?.px?.let { it <= THUMBNAIL_SIZE } == true &&
            (chain.size.height as? Dimension.Pixels)?.px?.let { it <= THUMBNAIL_SIZE } == true
        val data = PrivateAssetImage(scoped.scope, reference, thumbnail)
        val request = chain.request.newBuilder().data(data).memoryCacheKey(data.cacheKey).build()
        Log.d(TAG, "按显示尺寸读取私有图片 thumbnail=$thumbnail")
        val result = chain.withRequest(request).proceed()
        check(accounts.current().key == scoped.scope.key) { "账号已切换，丢弃旧图片结果" }
        return result
    }

    companion object {
        private const val TAG = "PrivateAssetImageInterceptorTester"
        internal const val THUMBNAIL_SIZE = 256
    }
}
