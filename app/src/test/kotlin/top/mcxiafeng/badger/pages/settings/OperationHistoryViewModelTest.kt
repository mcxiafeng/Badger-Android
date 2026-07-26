package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
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
import org.junit.Test
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.HistoryOpResult
import top.mcxiafeng.badger.data.repository.OperationHistoryRepository
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * [V2-P7] OperationHistoryViewModel 测试。
 *
 * 覆盖 8 类核心契约:
 * 1. initialValue 是 Loading
 * 2. Repository 推 records → Success
 * 3. Repository 推 records=null → Empty
 * 4. Retry 事件转发
 * 5. Withdraw 事件转发
 * 6. AdoptLocal 事件转发
 * 7. AdoptServer 事件转发
 * 8. ChangeFilter 切换后调 Repository.observeHistory 带新 filter
 *
 * [测试技巧]: uiState 是 `stateIn(WhileSubscribed(5_000))`,只有 collector 订阅才推进
 * 状态。这里 `backgroundScope.launch { vm.uiState.collect{} }` 拉起订阅,再 advanceUntilIdle
 * 让上游 RecordsFlow 推的 records 走下游;coVerify 同理 — ViewModelScope 必须有 collector
 * 才能触发 `flatMapLatest`/`map` 的副作用。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OperationHistoryViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: OperationHistoryRepository
    private val recordsFlow = MutableStateFlow<List<OperationHistoryWithContact>>(emptyList())

    @Before
    fun setup() {
        repository = mockk(relaxed = true) {
            every { observeHistory(filter = any(), limit = any()) } returns recordsFlow
        }
    }

    @After
    fun tearDown() {
        // mockk auto-clear
    }

    private fun makeViewModel(): OperationHistoryViewModel {
        return OperationHistoryViewModel(repository)
    }

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
        canUndo = true,
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

    // ============ 4. Retry 事件转发 ============

    @Test
    fun event_Retry_callsRepositoryRetry() = runTest {
        coEvery { repository.retry("op-1") } returns HistoryOpResult.Success
        val vm = makeViewModel()
        vm.onEvent(OperationHistoryEvent.Retry("op-1"))
        advanceUntilIdle()
        coVerify { repository.retry("op-1") }
    }

    // ============ 5. Withdraw 事件转发 ============

    @Test
    fun event_Withdraw_callsRepositoryWithdraw() = runTest {
        coEvery { repository.withdraw("op-1") } returns HistoryOpResult.Success
        val vm = makeViewModel()
        vm.onEvent(OperationHistoryEvent.Withdraw("op-1"))
        advanceUntilIdle()
        coVerify { repository.withdraw("op-1") }
    }

    // ============ 6. AdoptLocal 事件转发 ============

    @Test
    fun event_AdoptLocal_callsRepositoryAdoptLocal() = runTest {
        coEvery { repository.adoptLocal("op-1") } returns HistoryOpResult.Success
        val vm = makeViewModel()
        vm.onEvent(OperationHistoryEvent.AdoptLocal("op-1"))
        advanceUntilIdle()
        coVerify { repository.adoptLocal("op-1") }
    }

    // ============ 7. AdoptServer 事件转发 ============

    @Test
    fun event_AdoptServer_callsRepositoryAdoptServer() = runTest {
        val serverJson = """{"name":"x"}"""
        coEvery { repository.adoptServer("op-1", serverJson) } returns HistoryOpResult.Success
        val vm = makeViewModel()
        vm.onEvent(OperationHistoryEvent.AdoptServer("op-1", serverJson))
        advanceUntilIdle()
        coVerify { repository.adoptServer("op-1", serverJson) }
    }

    // ============ 8. ChangeFilter 切换 ============

    @Test
    fun event_ChangeFilter_callsRepositoryWithNewFilter() = runTest {
        recordsFlow.value = emptyList()
        val vm = makeViewModel()
        // 拉起 collector 让 flatMapLatest 生效
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        vm.onEvent(OperationHistoryEvent.ChangeFilter(HistoryFilter.Pending))
        advanceUntilIdle()
        coVerify { repository.observeHistory(filter = HistoryFilter.Pending, limit = any()) }
    }
}