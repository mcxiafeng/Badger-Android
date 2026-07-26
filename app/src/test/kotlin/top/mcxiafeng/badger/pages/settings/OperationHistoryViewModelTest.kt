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
import top.mcxiafeng.badger.data.repository.BatchHistoryOpResult
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
 * [V2-P10] 多选模式新增 7 个用例:
 * 9. EnterMultiSelect / ExitMultiSelect / ToggleSelect / SelectAll / ClearSelection
 * 10. BatchRetry 事件转发 + emit Message
 * 11. BatchWithdraw 事件转发 + emit Message + 退出多选
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
        canUndo: Boolean = true,
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
        canUndo = canUndo,
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

    // ============ 9. [V2-P10] EnterMultiSelect 切到多选态 ============

    @Test
    fun event_EnterMultiSelect_setsMultiSelectTrue() = runTest {
        recordsFlow.value = listOf(
            OperationHistoryWithContact(history = entity("op-1"), contactName = "Alice"),
            OperationHistoryWithContact(history = entity("op-2"), contactName = "Bob"),
        )
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        vm.onEvent(OperationHistoryEvent.EnterMultiSelect(initialSelectedId = "op-1"))
        advanceUntilIdle()
        val state = vm.uiState.value as OperationHistoryUiState.Success
        assertThat(state.multiSelect).isTrue()
        assertThat(state.selectedIds).containsExactly("op-1")
    }

    // ============ 10. ExitMultiSelect 清空状态 ============

    @Test
    fun event_ExitMultiSelect_clearsSelection() = runTest {
        recordsFlow.value = listOf(
            OperationHistoryWithContact(history = entity("op-1"), contactName = "Alice"),
        )
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        vm.onEvent(OperationHistoryEvent.EnterMultiSelect("op-1"))
        advanceUntilIdle()
        vm.onEvent(OperationHistoryEvent.ExitMultiSelect)
        advanceUntilIdle()
        val state = vm.uiState.value as OperationHistoryUiState.Success
        assertThat(state.multiSelect).isFalse()
        assertThat(state.selectedIds).isEmpty()
    }

    // ============ 11. ToggleSelect 加 / 减 ============

    @Test
    fun event_ToggleSelect_addsAndRemoves() = runTest {
        recordsFlow.value = listOf(
            OperationHistoryWithContact(history = entity("op-1"), contactName = "Alice"),
            OperationHistoryWithContact(history = entity("op-2"), contactName = "Bob"),
        )
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        vm.onEvent(OperationHistoryEvent.ToggleSelect("op-1"))
        advanceUntilIdle()
        vm.onEvent(OperationHistoryEvent.ToggleSelect("op-2"))
        advanceUntilIdle()
        var state = vm.uiState.value as OperationHistoryUiState.Success
        assertThat(state.selectedIds).containsExactly("op-1", "op-2")
        // 再次 toggle op-1 → 移除
        vm.onEvent(OperationHistoryEvent.ToggleSelect("op-1"))
        advanceUntilIdle()
        state = vm.uiState.value as OperationHistoryUiState.Success
        assertThat(state.selectedIds).containsExactly("op-2")
    }

    // ============ 12. SelectAll 全选当前 records ============

    @Test
    fun event_SelectAll_selectsAllVisibleRecords() = runTest {
        recordsFlow.value = listOf(
            OperationHistoryWithContact(history = entity("op-1"), contactName = "Alice"),
            OperationHistoryWithContact(history = entity("op-2"), contactName = "Bob"),
            OperationHistoryWithContact(history = entity("op-3"), contactName = "Carol"),
        )
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        vm.onEvent(OperationHistoryEvent.EnterMultiSelect())
        vm.onEvent(OperationHistoryEvent.SelectAll)
        advanceUntilIdle()
        val state = vm.uiState.value as OperationHistoryUiState.Success
        assertThat(state.selectedIds).containsExactly("op-1", "op-2", "op-3")
        assertThat(state.multiSelect).isTrue()
    }

    // ============ 13. ClearSelection 保留多选态 ============

    @Test
    fun event_ClearSelection_keepsMultiSelectTrue() = runTest {
        recordsFlow.value = listOf(
            OperationHistoryWithContact(history = entity("op-1"), contactName = "Alice"),
        )
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        vm.onEvent(OperationHistoryEvent.EnterMultiSelect("op-1"))
        vm.onEvent(OperationHistoryEvent.ClearSelection)
        advanceUntilIdle()
        val state = vm.uiState.value as OperationHistoryUiState.Success
        assertThat(state.multiSelect).isTrue()
        assertThat(state.selectedIds).isEmpty()
    }

    // ============ 14. BatchRetry 转发 + emit Message ============

    @Test
    fun event_BatchRetry_callsRepositoryBatchRetry() = runTest {
        coEvery { repository.batchRetry(listOf("op-1", "op-2")) } returns
            BatchHistoryOpResult.Success(succeeded = 2, failed = 0)
        val vm = makeViewModel()
        // 收集 message
        val receivedMsgs = mutableListOf<OperationHistoryMessage>()
        backgroundScope.launch { vm.messages.collect { receivedMsgs.add(it) } }
        vm.onEvent(OperationHistoryEvent.BatchRetry(listOf("op-1", "op-2")))
        advanceUntilIdle()
        coVerify { repository.batchRetry(listOf("op-1", "op-2")) }
        assertThat(receivedMsgs).hasSize(1)
        assertThat(receivedMsgs[0]).isInstanceOf(OperationHistoryMessage.Info::class.java)
        // 批量操作后退出多选
        val state = vm.uiState.value
        if (state is OperationHistoryUiState.Success) {
            assertThat(state.multiSelect).isFalse()
            assertThat(state.selectedIds).isEmpty()
        }
    }

    // ============ 15. BatchWithdraw 转发 + emit Message + 退出多选 ============

    @Test
    fun event_BatchWithdraw_callsRepositoryBatchWithdraw() = runTest {
        coEvery { repository.batchWithdraw(listOf("op-1")) } returns
            BatchHistoryOpResult.Success(succeeded = 1, failed = 0)
        recordsFlow.value = listOf(
            OperationHistoryWithContact(history = entity("op-1"), contactName = "Alice"),
        )
        val vm = makeViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        vm.onEvent(OperationHistoryEvent.EnterMultiSelect("op-1"))
        advanceUntilIdle()
        val receivedMsgs = mutableListOf<OperationHistoryMessage>()
        backgroundScope.launch { vm.messages.collect { receivedMsgs.add(it) } }
        vm.onEvent(OperationHistoryEvent.BatchWithdraw(listOf("op-1")))
        advanceUntilIdle()
        coVerify { repository.batchWithdraw(listOf("op-1")) }
        assertThat(receivedMsgs).hasSize(1)
        assertThat(receivedMsgs[0]).isInstanceOf(OperationHistoryMessage.Info::class.java)
        // 批量操作后退出多选
        val state = vm.uiState.value as OperationHistoryUiState.Success
        assertThat(state.multiSelect).isFalse()
        assertThat(state.selectedIds).isEmpty()
    }
}