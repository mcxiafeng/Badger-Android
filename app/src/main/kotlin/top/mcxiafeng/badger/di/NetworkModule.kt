package top.mcxiafeng.badger.di

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ServerApi
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The single source of HTTP I/O the app uses. The previous WebDAV
 * variant (`@WebDav OkHttpClient`) is gone — cloud sync goes through
 * the server's `/v1/backups`.
 *
 * The token holder is a process-singleton ([TokenHolder]); [UserAuthRepository]
 * writes the access JWT into it on login/refresh, the OkHttp auth
 * interceptor reads from it on every request, and the 401 interceptor
 * updates it on refresh.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTokenHolder(): TokenHolder = TokenHolder()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        factory: ServerApiFactory,
        tokenHolder: TokenHolder,
    ): OkHttpClient {
        // Install the ServerApi factory now that we have a token holder.
        factory.install {
            ServerApi(
                baseUrl = AuthPrefs.readServerUrl(context),
                http = baseClient(context),
                tokenProvider = tokenHolder::get,
            )
        }
        return baseClient(context).newBuilder()
            .addInterceptor(tokenAuthInterceptor(tokenHolder))
            .addInterceptor(tokenRefreshInterceptor(tokenHolder, context))
            .build()
    }

    private fun baseClient(@ApplicationContext context: Context): OkHttpClient =
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
        @ApplicationContext context: Context,
    ): Interceptor = Interceptor { chain ->
        val resp = chain.proceed(chain.request())
        if (resp.code == 401 && holder.get() != null) {
            // Token may have expired. Try /api/auth/refresh with the current
            // token; on success retry the request with the new token. On
            // failure drop the token so the App can route to Login.
            resp.close()
            val refreshed = runRefresh(context, holder.get()!!)
            if (refreshed != null) {
                holder.set(refreshed)
                AuthPrefs.writeRefreshToken(context, refreshed)
                val retried = chain.request().newBuilder()
                    .header("Authorization", "Bearer $refreshed")
                    .build()
                chain.proceed(retried)
            } else {
                holder.set(null)
                AuthPrefs.clearAuth(context)
                chain.proceed(chain.request().newBuilder().build())
            }
        } else {
            resp
        }
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
                if (!resp.isSuccessful) return@use null
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                obj.get("token")?.asString
            }
        } catch (e: Exception) {
            Log.w("NetworkModule", "refresh failed", e)
            null
        }
    }

    /**
     * Process-singleton token holder. Both the auth interceptor (reads) and
     * the user-auth repository (writes) share this instance. Public so
     * Hilt can construct it as a separate binding.
     */
    class TokenHolder {
        @Volatile private var token: String? = null
        fun get(): String? = token
        fun set(t: String?) { token = t }
    }

    /**
     * Build a [ServerApi] ad-hoc — used by static-object compat layers
     * that don't have an injected reference. New code should inject
     * [top.mcxiafeng.badger.data.repository.ServerApiFactory] instead.
     */
    fun provideServerApi(context: Context, tokenHolder: TokenHolder): ServerApi =
        ServerApi(
            baseUrl = AuthPrefs.readServerUrl(context),
            http = baseClient(context),
            tokenProvider = tokenHolder::get,
        )

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
}