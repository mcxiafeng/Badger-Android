package top.mcxiafeng.badger.data.account

import android.app.Application
import android.content.Context
import android.graphics.Color
import coil3.ColorImage
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountImageCacheTest {
    private data class TestAvatar(val url: String)

    @Test
    fun sameUrlInDifferentSpacesNeverReusesAnotherAccountsMemoryImage() = runBlocking {
        val base: Context = RuntimeEnvironment.getApplication()
        val store = AccountStore(base)
        val a = AccountContext(base, store.activateRemote("server", "A", "epoch", "A", "https://example.test"))
        val b = AccountContext(base, store.activateRemote("server", "B", "epoch", "B", "https://example.test"))
        val fetches = AtomicInteger()
        val loader = ImageLoader.Builder(base)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(CACHE_BYTES).build() }
            .components {
                add(AccountImageCacheInterceptor())
                add(Keyer<TestAvatar> { data, _ -> data.url })
                add(Fetcher.Factory<TestAvatar> { _, options, _ ->
                    Fetcher {
                        fetches.incrementAndGet()
                        val color = if (AccountContext.find(options.context)?.scope == a.scope) Color.RED else Color.BLUE
                        ImageFetchResult(ColorImage(color, IMAGE_EDGE, IMAGE_EDGE), false, DataSource.NETWORK)
                    }
                })
            }.build()
        try {
            suspend fun request(context: Context) = loader.execute(
                ImageRequest.Builder(context).data(TestAvatar(SAME_URL)).size(IMAGE_EDGE).build(),
            ) as SuccessResult
            val firstA = request(a)
            val firstB = request(b)
            val cachedA = request(a)
            val cachedB = request(b)
            assertThat((firstA.image as ColorImage).color).isEqualTo(Color.RED)
            assertThat((firstB.image as ColorImage).color).isEqualTo(Color.BLUE)
            assertThat(cachedA.dataSource).isEqualTo(DataSource.MEMORY_CACHE)
            assertThat(cachedB.dataSource).isEqualTo(DataSource.MEMORY_CACHE)
            assertThat(cachedA.image).isSameInstanceAs(firstA.image)
            assertThat(cachedB.image).isSameInstanceAs(firstB.image)
            assertThat(firstA.memoryCacheKey).isNotEqualTo(firstB.memoryCacheKey)
            assertThat(firstA.request.diskCacheKey).isNotEqualTo(firstB.request.diskCacheKey)
            assertThat(fetches.get()).isEqualTo(EXPECTED_FETCHES)
        } finally {
            loader.shutdown()
        }
    }

    companion object {
        private const val SAME_URL = "https://example.test/private-avatar"
        private const val CACHE_BYTES = 1024L * 1024L
        private const val IMAGE_EDGE = 32
        private const val EXPECTED_FETCHES = 2
    }
}
