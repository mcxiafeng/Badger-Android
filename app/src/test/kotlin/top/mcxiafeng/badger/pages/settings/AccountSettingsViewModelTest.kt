package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.pages.settings.account.AccountSettingsViewModel

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.emptyFlow

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountSettingsViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var userAuthRepository: UserAuthRepository
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var serverApiFactory: ServerApiFactory
    private lateinit var serverUrlHolder: ServerUrlHolder
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedOut)

    
    
    private fun newHolder(): ServerUrlHolder = ServerUrlHolder()

    
    private var stubUsername: String? = null
    private var stubServerUrl: String = "http://10.0.2.2:8080"
    
    private var stubIsAdmin: Boolean = false

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        userAuthRepository = mockk(relaxed = true) {
            every { state } returns authStateFlow
        }
        serverApiFactory = mockk(relaxed = true)
        
        userProfileRepository = mockk(relaxed = true) {
            every { getUserProfile() } returns emptyFlow()
            coEvery { getUserProfileOnce() } returns null
        }
        mockkObject(AuthPrefs)
        every { AuthPrefs.readUsername() } answers { stubUsername }
        every { AuthPrefs.readIsAdmin() } answers { stubIsAdmin }
        every { AuthPrefs.readServerUrl() } answers { stubServerUrl }
        every { AuthPrefs.writeServerUrl(any()) } answers {
            stubServerUrl = firstArg()
        }
        
        
        
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { context }
                    single { userAuthRepository }
                    single { userProfileRepository }
                    single { serverApiFactory }
                    single { ServerUrlHolder() }
                },
            )
        }
        serverUrlHolder = ServerUrlHolder()
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        unmockkAll()
    }

    private fun createViewModel(): AccountSettingsViewModel =
        AccountSettingsViewModel()

    

    @Test
    fun `init reads snapshot from AuthPrefs and auth state`() = runTest {
        stubUsername = "alice"
        stubIsAdmin = true
        stubServerUrl = "https://badger.example.com"
        authStateFlow.value = AuthState.SignedIn

        val vm = createViewModel()
        
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.username).isEqualTo("alice")
        
        assertThat(s.role).isEqualTo("管理员")
        assertThat(s.serverUrl).isEqualTo("https://badger.example.com")
        assertThat(s.isLoggedIn).isTrue()
        assertThat(s.isLoggingOut).isFalse()
    }

    @Test
    fun `init reflects SignedOut when no auth session`() {
        stubUsername = null
        stubIsAdmin = false
        stubServerUrl = "http://10.0.2.2:8080"
        authStateFlow.value = AuthState.SignedOut

        val vm = createViewModel()

        val s = vm.state.value
        assertThat(s.isLoggedIn).isFalse()
        assertThat(s.username).isNull()
        
        assertThat(s.role).isEqualTo("普通用户")
    }

    

    @Test
    fun `state flips isLoggedIn when authState transitions SignedOut to SignedIn`() = runTest {
        stubUsername = "bob"
        authStateFlow.value = AuthState.SignedOut
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.isLoggedIn).isFalse()

        
        stubUsername = "bob2"
        authStateFlow.value = AuthState.SignedIn
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.isLoggedIn).isTrue()
        assertThat(s.username).isEqualTo("bob2")
    }

    

    @Test
    fun `updateServerUrl ignores blank input and does not touch prefs`() {
        val vm = createViewModel()
        val original = vm.state.value.serverUrl

        vm.updateServerUrl("   ")
        vm.updateServerUrl("")

        io.mockk.verify(exactly = 0) { AuthPrefs.writeServerUrl(any()) }
        assertThat(vm.state.value.serverUrl).isEqualTo(original)
    }

    @Test
    fun `updateServerUrl trims trailing slash, persists, and updates state`() {
        val vm = createViewModel()

        vm.updateServerUrl("  https://badger.example.com/  ")

        
        io.mockk.verify(exactly = 1) {
            AuthPrefs.writeServerUrl("https://badger.example.com")
        }
        
        assertThat(vm.state.value.serverUrl).isEqualTo("https://badger.example.com")
    }

    @Test
    fun `updateServerUrl pushes normalized url into ServerApiFactory`() {
        
        
        
        val vm = createViewModel()

        vm.updateServerUrl("  https://badger.example.com/  ")

        io.mockk.verify(exactly = 1) {
            serverApiFactory.updateBaseUrl("https://badger.example.com")
        }
    }

    

    @Test
    fun `logout flips isLoggingOut then calls repository then resets flag`() = runTest {
        authStateFlow.value = AuthState.SignedIn
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.isLoggingOut).isFalse()

        vm.logout()
        
        assertThat(vm.state.value.isLoggingOut).isTrue()

        advanceUntilIdle()
        coVerify(exactly = 1) { userAuthRepository.logout() }
        
        assertThat(vm.state.value.isLoggingOut).isFalse()
    }

    @Test
    fun `logout second call during flight is no-op`() = runTest {
        authStateFlow.value = AuthState.SignedIn
        val vm = createViewModel()
        advanceUntilIdle()

        vm.logout()
        vm.logout() 
        advanceUntilIdle()

        
        coVerify(exactly = 1) { userAuthRepository.logout() }
    }
}
