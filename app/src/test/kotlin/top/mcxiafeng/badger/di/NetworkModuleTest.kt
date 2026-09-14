package top.mcxiafeng.badger.di

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.di.NetworkModule
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NetworkModuleTest {

    private lateinit var tempDir: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "badger-test-${System.nanoTime()}")
        tempDir.mkdirs()
        tempDir.deleteOnExit()
        context = mockk {
            every { cacheDir } returns tempDir
        }
        
        
        
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { context }
                },
            )
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        unmockkAll()
    }

    @Test
    fun `general client does not use insecure settings even when allowed`() {
        
        
        
        
        
        
        
        
        mockkObject(AuthPrefs)
        every { AuthPrefs.readServerUrl() } returns "https://badger.example.com"
        val client = NetworkModule.provideOkHttpClient(context, mockk(relaxed = true))
        assertThat(client.hostnameVerifier.javaClass.name).doesNotContain("NetworkModule")
    }

}