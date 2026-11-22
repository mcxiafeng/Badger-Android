package top.mcxiafeng.badger.data.sync

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.google.common.truth.Truth.assertThat
import dagger.hilt.EntryPoints
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.BadgerApplication
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.di.DatabaseEntryPoint
import top.mcxiafeng.badger.di.ImageModule
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import top.mcxiafeng.badger.network.sync.SyncTransport
import android.content.Context

@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = BadgerApplication::class)
class ApplicationImageLoaderTest {
    private var loader: ImageLoader? = null

    @After fun clearFactory() {
        SingletonImageLoader.reset()
        loader?.shutdown()
        loader?.diskCache?.shutdown()
        unmockkStatic(EntryPoints::class)
    }

    @Test fun firstRequestWithWrappedContextUsesTheApplicationFactory() {
        val application = RuntimeEnvironment.getApplication() as BadgerApplication
        val accounts = AccountStore(application)
        val sessions = AuthSessionStore(application.getSharedPreferences("image-factory-test", Context.MODE_PRIVATE))
        val client = OkHttpClient()
        val expected = ImageModule.provideImageLoader(application, client, PrivateAssetImageInterceptor(accounts),
            PrivateAssetFetcherFactory(SyncTransport(client, sessions), sessions, accounts))
        loader = expected
        val entry = mockk<DatabaseEntryPoint>()
        every { entry.imageLoader() } returns expected
        // The host JVM has no AndroidKeyStore. Replace only Hilt's dependency lookup, retaining
        // the real Application.onCreate registration and the real ImageModule configuration.
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(application, DatabaseEntryPoint::class.java) } returns entry
        val scope = AccountScope.remote("server", "user", "epoch", "用户", "https://example.test")
        val context = AccountContext(application, scope)
        val actual = SingletonImageLoader.get(context)
        assertThat(actual).isSameInstanceAs(expected)
        assertThat(SingletonImageLoader.get(context)).isSameInstanceAs(expected)
        assertThat(actual.memoryCache?.maxSize).isEqualTo(MEMORY_BYTES)
        assertThat(actual.diskCache?.maxSize).isEqualTo(DISK_BYTES)
        verify(exactly = 1) { entry.imageLoader() }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
        private const val MEMORY_BYTES = 32L * 1024 * 1024
        private const val DISK_BYTES = 100L * 1024 * 1024
    }
}
