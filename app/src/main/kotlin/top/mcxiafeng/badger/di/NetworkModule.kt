package top.mcxiafeng.badger.di

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** 基础网络设施：OkHttp、token holder 与 ServerApi 工厂。 */
object NetworkModule {

    private const val TAG = "NetworkModule"
    private const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"

    /** Refresh 是全局凭证状态变更操作，同一时间只允许一个请求执行 refresh。 */
    private val refreshLock = ReentrantLock()

    fun provideTokenHolder(): TokenHolder = TokenHolder()

    fun provideOkHttpClient(
        context: Context,
        tokenHolder: TokenHolder,
    ): OkHttpClient {
        val base = baseClient(context)
        return base.newBuilder()
            .addInterceptor(tokenAuthInterceptor(tokenHolder))
            .addInterceptor(tokenRefreshInterceptor(tokenHolder, context, base))
            .build()
    }

    /**
     * Constructs the sole ServerApi instance. The factory is installed only after construction
     * succeeds, so eager Koin singletons can safely request ServerApi during startup.
     */
    fun provideServerApi(
        context: Context,
        http: OkHttpClient,
        tokenHolder: TokenHolder,
        outboxStore: top.mcxiafeng.badger.sync.OutboxStore,
        outboxScheduler: top.mcxiafeng.badger.sync.OutboxScheduler,
        factory: ServerApiFactory,
    ): ServerApi {
        val initialUrl = try {
            AuthPrefs.readServerUrl(context)
        } catch (e: Exception) {
            Log.w(TAG, "AuthPrefs.readServerUrl failed; using default URL", e)
            DEFAULT_SERVER_URL
        }

        return ServerApi(
            baseUrl = initialUrl,
            http = http,
            tokenProvider = tokenHolder::get,
            outboxStore = outboxStore,
            outboxScheduler = outboxScheduler,
        ).also {
            factory.install(it, initialUrl)
        }
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

    /** 网络瞬时故障不清凭证，仅服务端明确拒绝时才 clearAuth。 */
    private fun tokenRefreshInterceptor(
        holder: TokenHolder,
        context: Context,
        baseClient: OkHttpClient,
    ): Interceptor = Interceptor chain@{ chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        val failedToken = holder.get()
        if (response.code != 401 || failedToken == null) {
            return@chain response
        }

        response.close()

        val usableToken = refreshLock.withLock {
            val latestToken = holder.get()
            if (latestToken != null && latestToken != failedToken) {
                latestToken
            } else {
                try {
                    runRefresh(context, failedToken, baseClient)?.also {
                        holder.set(it)
                        AuthPrefs.writeRefreshToken(context, it)
                    } ?: run {
                        if (holder.get() == failedToken) {
                            holder.set(null)
                            AuthPrefs.clearAuth(context)
                        }
                        null
                    }
                } catch (e: java.net.ConnectException) {
                    Log.w(TAG, "tokenRefresh: network unavailable (connect), keeping auth: ${e.message}")
                    null
                } catch (e: java.net.SocketTimeoutException) {
                    Log.w(TAG, "tokenRefresh: network unavailable (timeout), keeping auth: ${e.message}")
                    null
                } catch (e: java.net.UnknownHostException) {
                    Log.w(TAG, "tokenRefresh: network unavailable (DNS), keeping auth: ${e.message}")
                    null
                }
            }
        }

        if (usableToken != null) {
            val retried = request.newBuilder()
                .header("Authorization", "Bearer $usableToken")
                .build()
            return@chain chain.proceed(retried)
        }

        Log.w(
            TAG,
            "token refresh failed; surfacing 401 " +
                "(path=${request.url.encodedPath})",
        )
        throw ApiException(401, "token refresh failed", request.url.encodedPath)
    }

    /**
     * 执行 refresh 请求。
     *
     * @return 新 token（服务端接受）或 null（服务端拒绝——token 无效/过期）
     * @throws java.net.ConnectException / SocketTimeoutException / UnknownHostException 网络不可达
     *         （调用方据此决定不清除凭证）
     */
    private fun runRefresh(
        context: Context,
        currentToken: String,
        baseClient: OkHttpClient,
    ): String? {
        val refreshUrl = try {
            AuthPrefs.readServerUrl(context)
        } catch (e: Exception) {
            Log.w(TAG, "runRefresh: readServerUrl failed; using default URL", e)
            DEFAULT_SERVER_URL
        }

        val request = Request.Builder()
            .url("${refreshUrl.trimEnd('/')}/api/auth/refresh")
            .header("Authorization", "Bearer $currentToken")
            .post("".toRequestBody(null))
            .build()

        // 网络异常不吞——向调用方抛出让拦截器保留凭证；
        // 仅服务端明确拒绝（HTTP 非 2xx / code≠200）返回 null。
        val response = try {
            baseClient.newCall(request).execute()
        } catch (e: java.net.ConnectException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            throw e
        }

        return response.use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "refresh rejected by server: code=${resp.code}")
                return@use null
            }

            val body = resp.body?.string() ?: return@use null
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
