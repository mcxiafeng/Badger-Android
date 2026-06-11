package top.mcxiafeng.badger.di

import android.content.Context
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import top.mcxiafeng.badger.network.NetworkConfig
import top.mcxiafeng.badger.network.WebDavConfig
import java.io.File
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        Log.d("Tester", "NetworkModule: creating general OkHttpClient (strict SSL)")
        return OkHttpClient.Builder()
            .cache(Cache(cacheDir, 10L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    @WebDav
    fun provideWebDavOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val builder = OkHttpClient.Builder()
            .cache(Cache(cacheDir, 10L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()
                chain.proceed(request)
            }

        if (NetworkConfig.isAllowInsecureHttp()) {
            Log.d("Tester", "NetworkModule: creating WebDAV OkHttpClient (allowInsecureHttp=true, domain-restricted)")

            builder.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))

            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)

            builder.hostnameVerifier { hostname, _ ->
                val webDavUrl = WebDavConfig.getServerUrl(context)
                if (webDavUrl.isBlank()) {
                    Log.d("Tester", "WebDAV hostnameVerifier: no WebDAV URL configured, rejecting $hostname")
                    false
                } else {
                    val webDavHost = try {
                        URI(webDavUrl).host
                    } catch (_: Exception) {
                        Log.d("Tester", "WebDAV hostnameVerifier: failed to parse URL $webDavUrl")
                        ""
                    }
                    val allowed = hostname == webDavHost
                    Log.d("Tester", "WebDAV hostnameVerifier: hostname=$hostname, expected=$webDavHost, allowed=$allowed")
                    allowed
                }
            }
        } else {
            Log.d("Tester", "NetworkModule: creating WebDAV OkHttpClient (allowInsecureHttp=false, strict SSL)")
        }

        return builder.build()
    }

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
}
