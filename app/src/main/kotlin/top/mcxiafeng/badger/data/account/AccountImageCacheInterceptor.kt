package top.mcxiafeng.badger.data.account

import android.util.Log
import coil3.intercept.Interceptor
import coil3.request.ImageResult

/** Preserve normal Coil keys and transformations, adding fixed account identity before cache lookup. */
class AccountImageCacheInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val spaceKey = AccountContext.find(request.context)?.scope?.key ?: UNSCOPED_KEY
        val scoped = request.newBuilder()
            .memoryCacheKeyExtra(ACCOUNT_CACHE_EXTRA, spaceKey)
            .diskCacheKey("$spaceKey:${request.diskCacheKey ?: request.data}")
            .build()
        Log.d(TAG, "Applying fixed account image cache namespace")
        return chain.withRequest(scoped).proceed()
    }

    companion object {
        private const val TAG = "AccountImageCacheInterceptorTester"
        private const val ACCOUNT_CACHE_EXTRA = "badger_account_space"
        private const val UNSCOPED_KEY = "unscoped"
    }
}
