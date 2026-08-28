package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.UserDevice

/**
 * [B3] DeviceRepository 拉取 / 登出清空 / 重命名乐观更新 / 删除乐观移除。
 *
 * 调度器全部走 [StandardTestDispatcher]，不打真网络。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRepositoryTest {

    private fun device(uuid: String, name: String = "dev", online: Boolean = true) = UserDevice(
        uuid = uuid,
        deviceId = "id-$uuid",
        deviceName = name,
        ip = "1.2.3.4",
        online = online,
        loginTime = "t0",
    )

    @Test
    fun refresh_fetchesDevices() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns MutableStateFlow(AuthState.SignedIn)
        every { auth.currentToken() } returns "tok"
        val api = mockk<ServerApi>()
        every { api.listDevices() } returns listOf(device("d-1"), device("d-2"))

        val repo = DeviceRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        repo.refresh()
        advanceUntilIdle()

        assertThat(repo.devices.value.map { it.uuid }).containsExactly("d-1", "d-2")
        verify(exactly = 1) { api.listDevices() }
    }

    @Test
    fun signedOut_clearsDevices() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authState = MutableStateFlow<AuthState>(AuthState.SignedIn)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns authState
        every { auth.currentToken() } returns "tok"
        val api = mockk<ServerApi>()
        every { api.listDevices() } returns listOf(device("d-1"))

        val repo = DeviceRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        repo.refresh()
        advanceUntilIdle()
        assertThat(repo.devices.value).hasSize(1)

        every { auth.currentToken() } returns null
        authState.value = AuthState.SignedOut
        runCurrent()
        assertThat(repo.devices.value).isEmpty()
    }

    @Test
    fun noToken_skipsNetwork() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns MutableStateFlow(AuthState.SignedOut)
        every { auth.currentToken() } returns null
        val api = mockk<ServerApi>(relaxed = true)

        val repo = DeviceRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        repo.refresh()
        advanceUntilIdle()
        verify(exactly = 0) { api.listDevices() }
    }

    @Test
    fun renameDevice_optimisticallyUpdatesRow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns MutableStateFlow(AuthState.Unknown)
        every { auth.currentToken() } returns "tok"
        val api = mockk<ServerApi>(relaxed = true)
        every { api.listDevices() } returns listOf(device("d-1", "old"), device("d-2"))

        val repo = DeviceRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        repo.refresh()
        advanceUntilIdle()
        repo.renameDevice("d-1", "new-name")
        advanceUntilIdle()

        assertThat(repo.devices.value.first { it.uuid == "d-1" }.deviceName).isEqualTo("new-name")
        assertThat(repo.devices.value.first { it.uuid == "d-2" }.deviceName).isEqualTo("dev")
        verify { api.renameDevice("d-1", "new-name") }
    }

    @Test
    fun deleteDevice_optimisticallyRemovesRow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns MutableStateFlow(AuthState.Unknown)
        every { auth.currentToken() } returns "tok"
        val api = mockk<ServerApi>(relaxed = true)
        every { api.listDevices() } returns listOf(device("d-1"), device("d-2"))
        every { api.deleteDevice("d-1") } returns true

        val repo = DeviceRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        repo.refresh()
        advanceUntilIdle()
        repo.deleteDevice("d-1")
        advanceUntilIdle()

        assertThat(repo.devices.value.map { it.uuid }).containsExactly("d-2")
        verify { api.deleteDevice("d-1") }
    }
}
