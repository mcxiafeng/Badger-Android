package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.UserSettings
import top.mcxiafeng.badger.testutil.MainDispatcherRule
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UserSettingsViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var userAuthRepository: UserAuthRepository
    private lateinit var serverApiFactory: ServerApiFactory
    private lateinit var serverApi: ServerApi

    
    private val dispatcherEngaged = AtomicBoolean(false)
    private val recordingDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatcherEngaged.set(true)
            block.run() 
        }
    }

    @Before
    fun setUp() {
        serverApi = mockk(relaxed = true) {
            every { getUserSettings() } answers {
                UserSettings(language = "zh", theme = "dark", notifyEmail = true)
            }
        }
        serverApiFactory = mockk(relaxed = true) {
            every { get() } returns serverApi
        }
        userAuthRepository = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(AuthState.SignedOut)
        }
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { serverApiFactory }
                    single { userAuthRepository }
                },
            )
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        unmockkAll()
    }

    private fun createViewModel(): UserSettingsViewModel =
        UserSettingsViewModel(dispatcher = recordingDispatcher)

    @Test
    fun `load routes getUserSettings through injected dispatcher off Main`() = runTest {
        val vm = createViewModel()

        vm.load()
        advanceUntilIdle()

        
        assertThat(dispatcherEngaged.get()).isTrue()
        verify(exactly = 1) { serverApi.getUserSettings() }
        val state = vm.state.value
        assertThat(state).isInstanceOf(UserSettingsUiState.Success::class.java)
        val success = state as UserSettingsUiState.Success
        assertThat(success.isLoggedIn).isTrue()
        assertThat(success.settings.language).isEqualTo("zh")
    }
}
