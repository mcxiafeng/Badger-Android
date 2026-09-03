package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.pages.settings.sync.SyncStatusEvent
import top.mcxiafeng.badger.pages.settings.sync.SyncStatusMessage
import top.mcxiafeng.badger.pages.settings.sync.SyncStatusUiState
import top.mcxiafeng.badger.pages.settings.sync.SyncStatusViewModel

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.data.repository.SyncStatusSnapshot
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * [Phase 4 Task #21] SyncStatusViewModel 测试。
 *
 * 退役队列语义后覆盖的契约：
 * 1. 初始 uiState: Loading
 * 2. event_RetryAll 转发 Repository.retryAll + 推 Message
 * 3. event_Refresh 重新订阅触发 UI 状态更新
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var context: android.content.Context
    private lateinit var repository: SyncStatusRepository

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { context }
                    single { repository }
                },
            )
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
    }

    private fun makeViewModel(): SyncStatusViewModel =
        SyncStatusViewModel()

    private val defaultSnapshot = SyncStatusSnapshot(
        lastSyncVersion = 0L,
        lastSyncedAt = 0L,
        unsyncedCount = 0,
    )

    // ============ 1. 初始 Loading ============

    @Test
    fun uiState_init_isLoading() = runTest {
        coEvery { repository.snapshot() } returns defaultSnapshot
        val vm = makeViewModel()
        assertThat(vm.uiState.value).isInstanceOf(SyncStatusUiState.Loading::class.java)
    }

    // ============ 2. RetryAll 转发 + Message ============

    @Test
    fun event_RetryAll_callsRepositoryRetryAll_andEmitsMessage() = runTest {
        coEvery { repository.snapshot() } returns defaultSnapshot
        coEvery { repository.retryAll() } returns 3
        val vm = makeViewModel()

        val collected = mutableListOf<SyncStatusMessage>()
        backgroundScope.launch { vm.messages.collect { collected.add(it) } }
        vm.onEvent(SyncStatusEvent.RetryAll)
        advanceUntilIdle()

        coVerify { repository.retryAll() }
        assertThat(collected).hasSize(1)
        assertThat(collected[0].text).contains("3")
    }

    @Test
    fun event_RetryAll_empty_emitsInfoText() = runTest {
        coEvery { repository.snapshot() } returns defaultSnapshot
        coEvery { repository.retryAll() } returns 0
        val vm = makeViewModel()

        val collected = mutableListOf<SyncStatusMessage>()
        backgroundScope.launch { vm.messages.collect { collected.add(it) } }
        vm.onEvent(SyncStatusEvent.RetryAll)
        advanceUntilIdle()

        coVerify { repository.retryAll() }
        assertThat(collected).hasSize(1)
        assertThat(collected[0].text).contains("增量同步")
    }

    // ============ 3. Refresh 触发重新订阅 ============

    @Test
    fun event_Refresh_triggersResubscription() = runTest {
        coEvery { repository.snapshot() } returns defaultSnapshot
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.onEvent(SyncStatusEvent.Refresh)
        advanceUntilIdle()

        coVerify(atLeast = 2) { repository.snapshot() }
    }
}
