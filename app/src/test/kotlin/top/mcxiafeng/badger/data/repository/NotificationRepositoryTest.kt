package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.UserNotification

/**
 * [B1] NotificationRepository 轮询 / 登出清零 / 已读乐观更新。
 *
 * 调度器全部走 [StandardTestDispatcher]，60s 间隔用虚时推进，不打真网络。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRepositoryTest {

    private fun row(uuid: String, read: Boolean = false) = UserNotification(
        uuid = uuid,
        senderName = "admin",
        title = "t",
        body = "b",
        read = read,
        createTime = "t0",
        entityType = null,
        entityId = null,
    )

    @Test
    fun signedIn_pollsImmediately_thenEvery60s() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authState = MutableStateFlow<AuthState>(AuthState.Unknown)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns authState
        every { auth.currentToken() } returns "tok"
        val api = mockk<ServerApi>()
        every { api.getUnreadNotificationCount() } returns 4

        val repo = NotificationRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        authState.value = AuthState.SignedIn
        // [修复防御]: 轮询是 while+delay 无限循环，禁止 advanceUntilIdle（会把 60s 虚时一路快进到超时）。
        runCurrent()
        assertThat(repo.unreadCount.value).isEqualTo(4)
        verify(exactly = 1) { api.getUnreadNotificationCount() }

        advanceTimeBy(NotificationRepository.POLL_INTERVAL_MS)
        runCurrent()
        verify(exactly = 2) { api.getUnreadNotificationCount() }
    }

    @Test
    fun signedOut_stopsPolling_andZerosBadge() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authState = MutableStateFlow<AuthState>(AuthState.Unknown)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns authState
        every { auth.currentToken() } returns "tok"
        val api = mockk<ServerApi>()
        every { api.getUnreadNotificationCount() } returns 2

        val repo = NotificationRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        authState.value = AuthState.SignedIn
        runCurrent()
        assertThat(repo.unreadCount.value).isEqualTo(2)

        every { auth.currentToken() } returns null
        authState.value = AuthState.SignedOut
        runCurrent()
        assertThat(repo.unreadCount.value).isEqualTo(0)
        assertThat(repo.notifications.value).isEmpty()

        advanceTimeBy(NotificationRepository.POLL_INTERVAL_MS)
        runCurrent()
        verify(exactly = 1) { api.getUnreadNotificationCount() }
    }

    @Test
    fun unreadFetchFailure_keepsLastCount() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authState = MutableStateFlow<AuthState>(AuthState.SignedIn)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns authState
        every { auth.currentToken() } returns "tok"
        val api = mockk<ServerApi>()
        every { api.getUnreadNotificationCount() } returns 5 andThenThrows
            ApiException(500, "boom", "notifications.unreadCount")

        val repo = NotificationRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        runCurrent()
        assertThat(repo.unreadCount.value).isEqualTo(5)

        advanceTimeBy(NotificationRepository.POLL_INTERVAL_MS)
        runCurrent()
        assertThat(repo.unreadCount.value).isEqualTo(5)
    }

    @Test
    fun noToken_skipsNetwork() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns MutableStateFlow(AuthState.SignedOut)
        every { auth.currentToken() } returns null
        val api = mockk<ServerApi>(relaxed = true)

        val repo = NotificationRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        repo.refreshUnreadCount()
        advanceUntilIdle()
        verify(exactly = 0) { api.getUnreadNotificationCount() }
    }

    @Test
    fun markAsRead_optimisticallyFlipsRow_thenRefreshesUnread() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns MutableStateFlow(AuthState.Unknown)
        every { auth.currentToken() } returns "tok"
        val api = mockk<ServerApi>(relaxed = true)
        every { api.listNotifications() } returns listOf(row("n-1"), row("n-2"))
        every { api.getUnreadNotificationCount() } returns 1

        val repo = NotificationRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        repo.refreshNotifications()
        advanceUntilIdle()
        repo.markAsRead("n-1")
        advanceUntilIdle()

        assertThat(repo.notifications.value.first { it.uuid == "n-1" }.read).isTrue()
        assertThat(repo.notifications.value.first { it.uuid == "n-2" }.read).isFalse()
        verify { api.markNotificationRead("n-1") }
        assertThat(repo.unreadCount.value).isEqualTo(1)
    }

    @Test
    fun delete_removesRow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val auth = mockk<UserAuthRepository>()
        every { auth.state } returns MutableStateFlow(AuthState.Unknown)
        every { auth.currentToken() } returns "tok"
        val api = mockk<ServerApi>(relaxed = true)
        every { api.listNotifications() } returns listOf(row("n-1"), row("n-2"))
        every { api.deleteNotification("n-1") } returns true
        every { api.getUnreadNotificationCount() } returns 0

        val repo = NotificationRepository(api, auth, ioDispatcher = dispatcher, externalScope = backgroundScope)
        repo.refreshNotifications()
        advanceUntilIdle()
        repo.delete("n-1")
        advanceUntilIdle()

        assertThat(repo.notifications.value.map { it.uuid }).containsExactly("n-2")
        verify { api.deleteNotification("n-1") }
    }
}
