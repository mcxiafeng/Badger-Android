package top.mcxiafeng.badger.pages.settings

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.data.repository.SyncStatusSnapshot
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsHomeViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var userAuthRepository: UserAuthRepository
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var serverUrlHolder: ServerUrlHolder
    private lateinit var syncStatusRepository: SyncStatusRepository
    private lateinit var notificationRepository: NotificationRepository
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedOut)
    
    private val serverUrlFlow = MutableStateFlow("http://10.0.2.2:8080")
    private val unreadCountFlow = MutableStateFlow(0)

    private var stubUsername: String? = null
    private var stubServerUrl: String = "http://10.0.2.2:8080"

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        userAuthRepository = mockk(relaxed = true) {
            every { state } returns authStateFlow
        }
        serverUrlHolder = mockk(relaxed = true) {
            every { url } returns serverUrlFlow
        }
        
        syncStatusRepository = mockk(relaxed = true)
        io.mockk.coEvery { syncStatusRepository.snapshot() } returns SyncStatusSnapshot()
        notificationRepository = mockk(relaxed = true) {
            every { unreadCount } returns unreadCountFlow
        }
        
        
        userProfileRepository = mockk(relaxed = true) {
            every { getUserProfile() } returns MutableStateFlow<UserProfileCacheEntity?>(null)
        }
        mockkObject(AuthPrefs)
        every { AuthPrefs.readUsername() } answers { stubUsername }
        every { AuthPrefs.readServerUrl() } answers { stubServerUrl }
        
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { context }
                    single { userAuthRepository }
                    single { userProfileRepository }
                    single { serverUrlHolder }
                    single { syncStatusRepository }
                    single { notificationRepository }
                },
            )
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        unmockkAll()
    }

    private fun createViewModel(): SettingsHomeViewModel =
        SettingsHomeViewModel()

    
    private fun kotlinx.coroutines.test.TestScope.activate(vm: SettingsHomeViewModel) {
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
    }

    

    @Test
    fun `initial state reflects SignedOut and default server url`() = runTest {
        stubUsername = null
        stubServerUrl = "http://10.0.2.2:8080"
        serverUrlFlow.value = "http://10.0.2.2:8080"
        authStateFlow.value = AuthState.SignedOut

        val vm = createViewModel()

        
        val s = vm.state.value
        assertThat(s.username).isNull()
        assertThat(s.isLoggedIn).isFalse()
        assertThat(s.serverUrl).isEqualTo("http://10.0.2.2:8080")
    }

    @Test
    fun `initial state reflects already-SignedIn session`() = runTest {
        stubUsername = "carol"
        stubServerUrl = "https://badger.example.com"
        serverUrlFlow.value = "https://badger.example.com"
        authStateFlow.value = AuthState.SignedIn

        val vm = createViewModel()

        val s = vm.state.value
        assertThat(s.username).isEqualTo("carol")
        assertThat(s.isLoggedIn).isTrue()
        assertThat(s.serverUrl).isEqualTo("https://badger.example.com")
    }

    

    @Test
    fun `state flips isLoggedIn when authState transitions`() = runTest {
        stubUsername = "dave"
        authStateFlow.value = AuthState.SignedOut
        val vm = createViewModel()
        activate(vm)

        assertThat(vm.state.value.isLoggedIn).isFalse()

        stubUsername = "dave-prime"
        authStateFlow.value = AuthState.SignedIn
        
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.isLoggedIn).isTrue()
        assertThat(s.username).isEqualTo("dave-prime")
    }

    @Test
    fun `server url flips when ServerUrlHolder broadcasts`() = runTest {
        
        
        
        
        stubServerUrl = "https://old.example.com"
        serverUrlFlow.value = "https://old.example.com"
        authStateFlow.value = AuthState.SignedIn
        val vm = createViewModel()
        activate(vm)
        assertThat(vm.state.value.serverUrl).isEqualTo("https://old.example.com")

        
        
        serverUrlFlow.value = "https://new.example.com"
        advanceUntilIdle()

        assertThat(vm.state.value.serverUrl).isEqualTo("https://new.example.com")
        
        assertThat(vm.state.value.isLoggedIn).isTrue()
    }

    @Test
    fun `unreadCount follows NotificationRepository`() = runTest {
        unreadCountFlow.value = 0
        authStateFlow.value = AuthState.SignedIn
        val vm = createViewModel()
        activate(vm)
        assertThat(vm.state.value.unreadCount).isEqualTo(0)

        unreadCountFlow.value = 7
        advanceUntilIdle()
        assertThat(vm.state.value.unreadCount).isEqualTo(7)
    }
}
