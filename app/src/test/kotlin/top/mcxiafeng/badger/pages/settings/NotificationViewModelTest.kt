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
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.UserNotification
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * [B2] NotificationViewModel：refresh / 已读 / 删除 / 失败不静默清空。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private val notificationsFlow = MutableStateFlow<List<UserNotification>>(emptyList())
    private val unreadFlow = MutableStateFlow(0)
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.SignedIn)

    private lateinit var repository: NotificationRepository
    private lateinit var userAuthRepository: UserAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk(relaxed = true)
        every { repository.notifications } returns notificationsFlow
        every { repository.unreadCount } returns unreadFlow
        userAuthRepository = mockk(relaxed = true)
        every { userAuthRepository.state } returns authStateFlow
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { repository }
                    single { userAuthRepository }
                },
            )
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        Dispatchers.resetMain()
    }

    private fun createViewModel(): NotificationViewModel =
        NotificationViewModel(dispatcher = Dispatchers.Unconfined)

    private fun kotlinx.coroutines.test.TestScope.activate(vm: NotificationViewModel) {
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
    }

    private fun row(uuid: String, read: Boolean = false) = UserNotification(
        uuid = uuid,
        senderName = "admin",
        title = "t-$uuid",
        body = "b",
        read = read,
        createTime = "t0",
        entityType = null,
        entityId = null,
    )

    @Test
    fun `refresh success populates items from repository`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { repository.refreshNotifications() } answers {
            notificationsFlow.value = listOf(row("n-1"), row("n-2", read = true))
        }
        coEvery { repository.refreshUnreadCount() } answers { unreadFlow.value = 1 }
        val vm = createViewModel()
        activate(vm)

        vm.refresh()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.loading).isFalse()
        assertThat(s.error).isNull()
        assertThat(s.items).hasSize(2)
        assertThat(s.unreadCount).isEqualTo(1)
        assertThat(s.isLoggedIn).isTrue()
        coVerify(exactly = 1) { repository.refreshNotifications() }
        coVerify(exactly = 1) { repository.refreshUnreadCount() }
    }

    @Test
    fun `refresh failure writes error and keeps existing items`() = runTest(UnconfinedTestDispatcher()) {
        notificationsFlow.value = listOf(row("n-keep"))
        unreadFlow.value = 3
        coEvery { repository.refreshNotifications() } throws ApiException(
            status = 500,
            bodyText = "boom",
            what = "notifications.list",
        )
        val vm = createViewModel()
        activate(vm)

        vm.refresh()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.loading).isFalse()
        assertThat(s.error).isNotNull()
        assertThat(s.items.map { it.uuid }).containsExactly("n-keep")
        assertThat(s.unreadCount).isEqualTo(3)
    }

    @Test
    fun `markAsRead skips already-read rows`() = runTest(UnconfinedTestDispatcher()) {
        notificationsFlow.value = listOf(row("n-1", read = true))
        val vm = createViewModel()
        activate(vm)

        vm.markAsRead("n-1")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.markAsRead("n-1") }
    }

    @Test
    fun `markAsRead failure writes error`() = runTest(UnconfinedTestDispatcher()) {
        notificationsFlow.value = listOf(row("n-1"))
        coEvery { repository.markAsRead("n-1") } throws ApiException(
            status = 500,
            bodyText = "nope",
            what = "notifications.read",
        )
        val vm = createViewModel()
        activate(vm)

        vm.markAsRead("n-1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isNotNull()
    }

    @Test
    fun `delete failure writes error and keeps row until repo updates`() = runTest(UnconfinedTestDispatcher()) {
        notificationsFlow.value = listOf(row("n-1"), row("n-2"))
        coEvery { repository.delete("n-1") } throws ApiException(
            status = 500,
            bodyText = "nope",
            what = "notifications.delete",
        )
        val vm = createViewModel()
        activate(vm)

        vm.delete("n-1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isNotNull()
        assertThat(vm.uiState.value.items.map { it.uuid }).containsExactly("n-1", "n-2")
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
        coEvery { repository.refreshNotifications() } throws ApiException(
            status = 503,
            bodyText = "down",
            what = "notifications.list",
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

    // [C4] 筛选测试
    @Test
    fun `setFilter UNREAD hides read items`() = runTest(UnconfinedTestDispatcher()) {
        notificationsFlow.value = listOf(row("n-1"), row("n-2", read = true), row("n-3"))
        val vm = createViewModel()
        activate(vm)

        vm.setFilter(NotificationFilter.UNREAD)
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.filter).isEqualTo(NotificationFilter.UNREAD)
        assertThat(s.items.map { it.uuid }).containsExactly("n-1", "n-3")
    }

    @Test
    fun `setFilter ALL shows all items`() = runTest(UnconfinedTestDispatcher()) {
        notificationsFlow.value = listOf(row("n-1"), row("n-2", read = true))
        val vm = createViewModel()
        activate(vm)

        vm.setFilter(NotificationFilter.UNREAD)
        advanceUntilIdle()
        assertThat(vm.uiState.value.items).hasSize(1)

        vm.setFilter(NotificationFilter.ALL)
        advanceUntilIdle()
        assertThat(vm.uiState.value.items).hasSize(2)
    }

    @Test
    fun `default filter is ALL`() = runTest(UnconfinedTestDispatcher()) {
        val vm = createViewModel()
        activate(vm)
        assertThat(vm.uiState.value.filter).isEqualTo(NotificationFilter.ALL)
    }
}
