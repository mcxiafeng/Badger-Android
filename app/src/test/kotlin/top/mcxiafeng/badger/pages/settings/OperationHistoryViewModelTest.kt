package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.pages.settings.history.OperationHistoryEvent
import top.mcxiafeng.badger.pages.settings.history.OperationHistoryUiState
import top.mcxiafeng.badger.pages.settings.history.OperationHistoryViewModel

import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryRepository
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * [Phase 3] OperationHistoryViewModel（只读日志版）测试。
 *
 * 队列退役后保留的契约：
 * 1. initialValue 是 Loading
 * 2. Repository 推 records → Success
 * 3. Repository 推空 records → Empty
 * 4. ChangeFilter 切换后调 Repository.observeHistory 带新 filter
 *
 * [测试技巧]: uiState 是 `stateIn(WhileSubscribed(5_000))`,只有 collector 订阅才推进
 * 状态。这里 `backgroundScope.launch { vm.uiState.collect{} }` 拉起订阅,再 advanceUntilIdle。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OperationHistoryViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: OperationHistoryRepository
    private val recordsFlow = MutableStateFlow<List<OperationHistoryWithContact>>(emptyList())

    @Before
    fun setup() {
        repository = mockk(relaxed = true) {
            every { observeHistory(filter = any(), limit = any()) } returns recordsFlow
        }
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(module { single { repository } })
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
    }

    private fun makeViewModel(): OperationHistoryViewModel = OperationHistoryViewModel()

    private fun entity(
        opId: String = "op-1",
        status: String = "DONE",
    ) = OperationHistoryEntity(
        opId = opId,
        contactId = 1L,
        opType = "UPDATE_NAME",
        opLabel = "修改姓名",
        payloadJson = "{}",
        snapshotBeforeJson = "{}",
        snapshotAfterJson = null,
        createdAt = 1L,
        opStatus = status,
        canUndo = false,
        canReplay = false,
    )

    // ============ 1. initialValue 是 Loading ============

    @Test
    fun uiState_init_isLoading() = runTest {
        val vm = makeViewModel()
        assertThat(vm.uiState.value).isInstanceOf(OperationHistoryUiState.Loading::class.java)
    }

    // ============ 2. Repository 推 records → Success ============

    @Test
    fun uiState_withRecords_emitsSuccess() = runTest {
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        recordsFlow.value = listOf(
            OperationHistoryWithContact(history = entity("op-1"), contactName = "Alice"),
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertThat(state).isInstanceOf(OperationHistoryUiState.Success::class.java)
        val success = state as OperationHistoryUiState.Success
        assertThat(success.records).hasSize(1)
        assertThat(success.records[0].contactName).isEqualTo("Alice")
    }

    // ============ 3. 空 records → Empty ============

    @Test
    fun uiState_withEmptyRecords_emitsEmptyState() = runTest {
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        recordsFlow.value = emptyList()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertThat(state).isInstanceOf(OperationHistoryUiState.Empty::class.java)
    }

    // ============ 4. ChangeFilter 切换 ============

    @Test
    fun event_ChangeFilter_callsRepositoryWithNewFilter() = runTest {
        recordsFlow.value = emptyList()
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        vm.onEvent(OperationHistoryEvent.ChangeFilter(HistoryFilter.Pending))
        advanceUntilIdle()
        coVerify { repository.observeHistory(filter = HistoryFilter.Pending, limit = any()) }
    }

    // ============ 5. Refresh 是 no-op（只读页无副作用） ============

    @Test
    fun event_Refresh_doesNotThrow() = runTest {
        val vm = makeViewModel()
        vm.onEvent(OperationHistoryEvent.Refresh)
        advanceUntilIdle()
        assertThat(vm.uiState.value).isNotNull()
    }
}
