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
 * [§14.2] 不再是 Hilt @Module,改造成普通 object 工厂 + Koin `single { ... }` 引用。
 *
 * OKHttp client + TokenHolder + ServerApi 三者协作仍然需要"先建 ServerApi 再装入工厂"
 * 的握手约定 — 这是 ServerApiFactory 的设计初衷,Koin 不会改变这一点。
 *
 * ServerUrlHolder / WorldRegionRepository / UserAuthRepository / PendingUploadScheduler /
 * ContactSyncBootstrapper / LegacyTagFixup / UseCases / Repository / Snapshotter / Executor
 * 现在通过 [KoinModule] 注册;这里只保留"无依赖图"的基础设施类。
 */
object NetworkModule {

    private const val TAG = "NetworkModule"

    /**
     * 提供给 Koin;`single { NetworkModule.provideTokenHolder() }`
     */
    fun provideTokenHolder(): TokenHolder = TokenHolder()

    fun provideOkHttpClient(
        context: Context,
        factory: ServerApiFactory,
        tokenHolder: TokenHolder,
    ): OkHttpClient {
        // [修复防御]: 把 ServerApi 实例的构造与 baseUrl 控制权交给 ServerApiFactory,
        // 避免「OkHttpClient 单例 + ServerApi baseUrl val」组合导致 URL 改完必须重启
        // 才能让新地址生效。Factory 现在持有可变 baseUrl 引用,每次请求读最新值。
        // [§14.2 修复] AuthPrefs 损坏会拖崩 Koin 启动 → 全 app 启动崩。
        // SharedPreferences 反序列化在某些 Android 版本/损坏 XML 下会抛
        // ClassCastException 或 XmlPullParserException,这里 catch 住 + 降级到默认 URL,
        // 并 Log.w 记录根因(不静默吞错)。
        val initialUrl = try {
            AuthPrefs.readServerUrl(context)
        } catch (e: Throwable) {
            Log.w(TAG, "AuthPrefs.readServerUrl 失败,降级到默认 URL http://10.0.2.2:8080", e)
            "http://10.0.2.2:8080"
        }
        val api = ServerApi(
            baseUrl = initialUrl,
            http = baseClient(context),
            tokenProvider = tokenHolder::get,
        )
        factory.install(api, initialUrl)
        return baseClient(context).newBuilder()
            .addInterceptor(tokenAuthInterceptor(tokenHolder))
            .addInterceptor(tokenRefreshInterceptor(tokenHolder, context))
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
        val tok = holder.get()
        val request = if (tok != null && original.header("Authorization") == null) {
            original.newBuilder().header("Authorization", "Bearer $tok").build()
        } else original
        chain.proceed(request)
    }

    private fun tokenRefreshInterceptor(
        holder: TokenHolder,
        context: Context,
    ): Interceptor = Interceptor chain@{ chain ->
        val req = chain.request()
        val resp = chain.proceed(req)

        // 不是 401 或当前没有 token → 原样返回 (404/500 等不触发刷新)
        if (resp.code != 401 || holder.get() == null) return@chain resp

        // 关掉原响应,避免 socket leak
        resp.close()

        val current = holder.get()!!
        val refreshed = runRefresh(context, current)

        if (refreshed != null) {
            holder.set(refreshed)
            AuthPrefs.writeRefreshToken(context, refreshed)
            // 用新 token 重试一次 —— 这是唯一的重试;若仍然 401,由上层 / 调用方 catch 后走 SignedOut
            val retried = req.newBuilder()
                .header("Authorization", "Bearer $refreshed")
                .build()
            return@chain chain.proceed(retried)
        }

        // [修复防御]: 旧实现这里会 "chain.proceed(chain.request().newBuilder().build())" —— 那是死循环温床。
        // 用空 token 重放原请求会再次得到 401 → 再次进入本拦截器 → 再次 refresh 失败 → 如此循环直到 OkHttp 超时,
        // 用户体感就是 "配置了正确地址却连不上服务器"。正确做法:清凭证后抛 ApiException,
        // 让上游 (bootstrap/fetchMe 等) 在 catch 中走 SignedOut,UI 自然跳登录页。
        Log.w(TAG, "token refresh failed; clearing auth and surfacing 401 to caller (path=${req.url.encodedPath})")
        holder.set(null)
        AuthPrefs.clearAuth(context)
        throw ApiException(401, "token refresh failed", req.url.encodedPath)
    }

    private fun runRefresh(context: Context, currentToken: String): String? {
        val req = Request.Builder()
            .url(AuthPrefs.readServerUrl(context) + "/api/auth/refresh")
            .header("Authorization", "Bearer $currentToken")
            .post("".toRequestBody(null))
            .build()
        val call = okhttp3.OkHttpClient().newCall(req)
        return try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    // [修复防御]: 服务端明确拒绝 (401/403) 不要重试任何其他逻辑,直接当 refresh 失败
                    // —— 区分「服务端拒绝」与「网络层异常」,便于排查到底是配错地址还是 token 失效
                    Log.w(TAG, "refresh rejected by server: code=${resp.code}")
                    return@use null
                }
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                // [Phase 2 修复防御]: 新 Java /api 契约 refresh 响应是 ApiResult 壳 `{code,data:{token}}`，
                // 不再直接裸 `{token}` —— 直接 `obj.get("token")` 会拿到 null，刷新永久失败。
                // 同时消费壳内业务 code（HTTP 2xx 但 code!=200 视为拒绝）。
                val code = obj.get("code")?.takeIf { !it.isJsonNull }?.asInt
                if (code != null && code != 200) {
                    Log.w(TAG, "refresh rejected by ApiResult code=$code msg=${obj.get("message")?.asString}")
                    return@use null
                }
                obj.get("data")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("token")?.takeIf { !it.isJsonNull }?.asString
            }
        } catch (e: java.net.ConnectException) {
            // [修复防御]: 与服务端拒绝同样返回 null,但日志分类为「网络层」,便于排查 "连不上服务器"
            Log.w(TAG, "refresh connect failed (server unreachable?): ${e.message}")
            null
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "refresh timeout: ${e.message}")
            null
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "refresh dns failed: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed (other): ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    /**
     * Process-singleton token holder. Both the auth interceptor (reads) and
     * the user-auth repository (writes) share this instance.
     */
    class TokenHolder {
        @Volatile private var token: String? = null
        fun get(): String? = token
        fun set(t: String?) { token = t }
    }

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
}
