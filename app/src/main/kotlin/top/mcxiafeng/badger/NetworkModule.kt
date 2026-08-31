package top.mcxiafeng.badger

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 基础网络设施：OkHttp、token holder 与 ServerApi 工厂。
 *
 * ServerApiFactory 持有可变 base URL，因此修改服务器地址后无需重建 OkHttpClient。
 */
object NetworkModule {

    private const val TAG = "NetworkModule"
    private const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"

    fun provideTokenHolder(): TokenHolder = TokenHolder()

    fun provideOkHttpClient(
        context: Context,
        factory: ServerApiFactory,
        tokenHolder: TokenHolder,
    ): OkHttpClient {
        val initialUrl = try {
            AuthPrefs.readServerUrl(context)
        } catch (e: Throwable) {
            Log.w(TAG, "AuthPrefs.readServerUrl failed; using default URL", e)
            DEFAULT_SERVER_URL
        }

        // Cache 必须只由一个 OkHttpClient 实例拥有；后续 builder 基于同一个实例扩展拦截器。
        val base = baseClient(context)
        val api = ServerApi(
            baseUrl = initialUrl,
            http = base,
            tokenProvider = tokenHolder::get,
        )
        factory.install(api, initialUrl)

        return base.newBuilder()
            .addInterceptor(tokenAuthInterceptor(tokenHolder))
            .addInterceptor(tokenRefreshInterceptor(tokenHolder, context, base))
            .build()
    }

    private fun baseClient(context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http_cache"), 10L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()

    private fun tokenAuthInterceptor(holder: TokenHolder): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val token = holder.get()
        val request = if (token != null && original.header("Authorization") == null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        chain.proceed(request)
    }

    private fun tokenRefreshInterceptor(
        holder: TokenHolder,
        context: Context,
        baseClient: OkHttpClient,
    ): Interceptor = Interceptor chain@{ chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        // 没有 token 时绝不刷新；此时必须保留原 response 的生命周期交给调用方。
        val currentToken = holder.get()
        if (response.code != 401 || currentToken == null) {
            return@chain response
        }

        // 只有确定需要刷新后才关闭原响应。
        response.close()
        val refreshed = runRefresh(context, currentToken, baseClient)

        if (refreshed != null) {
            holder.set(refreshed)
            AuthPrefs.writeRefreshToken(context, refreshed)
            val retried = request.newBuilder()
                .header("Authorization", "Bearer $refreshed")
                .build()
            return@chain chain.proceed(retried)
        }

        Log.w(
            TAG,
            "token refresh failed; clearing auth and surfacing 401 " +
                "(path=${request.url.encodedPath})",
        )
        holder.set(null)
        AuthPrefs.clearAuth(context)
        throw ApiException(401, "token refresh failed", request.url.encodedPath)
    }

    private fun runRefresh(
        context: Context,
        currentToken: String,
        baseClient: OkHttpClient,
    ): String? {
        val refreshUrl = try {
            AuthPrefs.readServerUrl(context)
        } catch (e: Throwable) {
            Log.w(TAG, "runRefresh: readServerUrl failed; using default URL", e)
            DEFAULT_SERVER_URL
        }

        val request = Request.Builder()
            .url("$refreshUrl/api/auth/refresh")
            .header("Authorization", "Bearer $currentToken")
            .post("".toRequestBody(null))
            .build()

        return try {
            baseClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "refresh rejected by server: code=${response.code}")
                    return@use null
                }

                val body = response.body?.string() ?: return@use null
                val obj = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@use null
                val code = obj.get("code")?.takeIf { !it.isJsonNull }?.asInt
                if (code != null && code != 200) {
                    Log.w(
                        TAG,
                        "refresh rejected by ApiResult code=$code " +
                            "msg=${obj.get("message")?.asString}",
                    )
                    return@use null
                }
                obj.get("data")
                    ?.takeIf { !it.isJsonNull && it.isJsonObject }
                    ?.asJsonObject
                    ?.get("token")
                    ?.takeIf { !it.isJsonNull }
                    ?.asString
            }
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "refresh connect failed: ${e.message}")
            null
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "refresh timeout: ${e.message}")
            null
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "refresh DNS failed: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    class TokenHolder {
        @Volatile
        private var token: String? = null

        fun get(): String? = token

        fun set(token: String?) {
            this.token = token
        }
    }

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
}
