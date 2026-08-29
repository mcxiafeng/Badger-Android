package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.DeviceRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.UserDevice
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * [B4] DeviceViewModel：refresh / rename / delete / currentDeviceId / 失败不静默清空。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeviceViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private val devicesFlow = MutableStateFlow<List<UserDevice>>(emptyList())
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedIn)

    private lateinit var repository: DeviceRepository
    private lateinit var userAuthRepository: UserAuthRepository
    private lateinit var deviceIdProvider: DeviceIdProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk(relaxed = true)
        every { repository.devices } returns devicesFlow
        userAuthRepository = mockk(relaxed = true)
        every { userAuthRepository.state } returns authStateFlow
        deviceIdProvider = mockk(relaxed = true)
        every { deviceIdProvider.deviceId() } returns "current-device-id"
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { repository }
                    single { userAuthRepository }
                    single { deviceIdProvider }
                },
            )
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DeviceViewModel =
        DeviceViewModel(dispatcher = Dispatchers.Unconfined)

    private fun kotlinx.coroutines.test.TestScope.activate(vm: DeviceViewModel) {
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
    }

    private fun device(
        uuid: String,
        deviceId: String = "dev-$uuid",
        name: String = "Device $uuid",
        ip: String? = "192.168.1.1",
        online: Boolean = true,
        loginTime: String? = "2026-08-29T10:00:00",
    ) = UserDevice(
        uuid = uuid,
        deviceId = deviceId,
        deviceName = name,
        ip = ip,
        online = online,
        loginTime = loginTime,
    )

    @Test
    fun `refresh success populates devices from repository`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { repository.refresh() } answers {
            devicesFlow.value = listOf(device("d-1"), device("d-2"))
        }
        val vm = createViewModel()
        activate(vm)

        vm.refresh()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.loading).isFalse()
        assertThat(s.error).isNull()
        assertThat(s.devices).hasSize(2)
        assertThat(s.isLoggedIn).isTrue()
        coVerify(exactly = 1) { repository.refresh() }
    }

    @Test
    fun `refresh failure writes error and keeps existing devices`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(device("d-keep"))
        coEvery { repository.refresh() } throws ApiException(
            status = 500,
            bodyText = "boom",
            what = "devices.list",
        )
        val vm = createViewModel()
        activate(vm)

        vm.refresh()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.loading).isFalse()
        assertThat(s.error).isNotNull()
        assertThat(s.devices.map { it.uuid }).containsExactly("d-keep")
    }

    @Test
    fun `renameDevice failure writes error`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { repository.renameDevice("d-1", "New Name") } throws ApiException(
            status = 500,
            bodyText = "nope",
            what = "devices.rename",
        )
        val vm = createViewModel()
        activate(vm)

        vm.renameDevice("d-1", "New Name")
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isNotNull()
    }

    @Test
    fun `renameDevice blank inputs are ignored`() = runTest(UnconfinedTestDispatcher()) {
        val vm = createViewModel()
        activate(vm)

        vm.renameDevice("", "name")
        vm.renameDevice("d-1", "")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.renameDevice(any(), any()) }
    }

    @Test
    fun `deleteDevice failure writes error`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { repository.deleteDevice("d-1") } throws ApiException(
            status = 403,
            bodyText = "cannot delete current device",
            what = "devices.delete",
        )
        val vm = createViewModel()
        activate(vm)

        vm.deleteDevice("d-1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isNotNull()
    }

    @Test
    fun `deleteDevice blank uuid is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val vm = createViewModel()
        activate(vm)

        vm.deleteDevice("")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.deleteDevice(any()) }
    }

    @Test
    fun `currentDeviceId exposes DeviceIdProvider value`() = runTest(UnconfinedTestDispatcher()) {
        val vm = createViewModel()
        assertThat(vm.currentDeviceId).isEqualTo("current-device-id")
    }

    @Test
    fun `signed out is reflected in uiState`() = runTest(UnconfinedTestDispatcher()) {
        authStateFlow.value = AuthState.SignedOut
        val vm = createViewModel()
        activate(vm)
        assertThat(vm.uiState.value.isLoggedIn).isFalse()
    }

    @Test
    fun `clearError resets transient error`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { repository.refresh() } throws ApiException(
            status = 503,
            bodyText = "down",
            what = "devices.list",
        )
        val vm = createViewModel()
        activate(vm)
        vm.refresh()
        advanceUntilIdle()
        assertThat(vm.uiState.value.error).isNotNull()

        vm.clearError()
        advanceUntilIdle()
        assertThat(vm.uiState.value.error).isNull()
    }
}
