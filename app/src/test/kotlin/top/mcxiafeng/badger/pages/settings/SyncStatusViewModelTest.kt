package top.mcxiafeng.badger.pages.settings

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
 * [V2-P9] SyncStatusViewModel 测试。
 *
 * 覆盖 4 类核心契约:
 * 1. 初始 uiState:Loading(Repository snapshot 还没推时)
 * 2. event_RetryAll 转发 Repository.retryAll + 推 Message
 * 3. event_PurgeFinished 转发 Repository.purgeFinished + 推 Message
 * 4. event_Refresh 重新订阅触发 UI 状态更新
 *
 * [测试技巧]: uiState 是 `stateIn(WhileSubscribed(5_000))`,只有 collector 订阅才推进。
 * 这里 `backgroundScope.launch { vm.uiState.collect{} }` 拉起订阅,再 advanceUntilIdle
 * 让 Repository 推 snapshot。
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
        // [§14.2] 为 ViewModel 注入 mock 依赖
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
        // mockk auto-clear
    }

    private fun makeViewModel(): SyncStatusViewModel =
        SyncStatusViewModel()

    private val defaultSnapshot = SyncStatusSnapshot(
        pendingCount = 0,
        inFlightCount = 0,
        failedCount = 0,
        conflictCount = 0,
        failedPermanentCount = 0,
        withdrawnCount = 0,
        doneCount = 0,
        totalCount = 0,
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
        // [修复防御]: 消息文本必须含 "3"(告知用户数),这是 UI Snackbar 唯一反馈
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
        // [Phase 3] 空结果文案为"已触发增量同步(无新增变更)"
        assertThat(collected).hasSize(1)
        assertThat(collected[0].text).contains("增量同步")
    }

    // ============ 3. PurgeFinished 转发 + Message ============

    @Test
    fun event_PurgeFinished_callsRepositoryPurge_andEmitsMessage() = runTest {
        coEvery { repository.snapshot() } returns defaultSnapshot
        coEvery { repository.purgeFinished() } returns 5
        val vm = makeViewModel()

        val collected = mutableListOf<SyncStatusMessage>()
        backgroundScope.launch { vm.messages.collect { collected.add(it) } }
        vm.onEvent(SyncStatusEvent.PurgeFinished)
        advanceUntilIdle()

        coVerify { repository.purgeFinished() }
        assertThat(collected).hasSize(1)
        assertThat(collected[0].text).contains("5")
    }

    // ============ 4. Refresh 触发重新订阅 ============

    @Test
    fun event_Refresh_triggersResubscription() = runTest {
        coEvery { repository.snapshot() } returns defaultSnapshot
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        // 第一次已经 collect 过,这里调一次 Refresh 让 trigger 增 → 再读 snapshot
        vm.onEvent(SyncStatusEvent.Refresh)
        advanceUntilIdle()

        // snapshot 会被多次调用(初始 1 + Refresh 1)
        coVerify(atLeast = 2) { repository.snapshot() }
    }
}
