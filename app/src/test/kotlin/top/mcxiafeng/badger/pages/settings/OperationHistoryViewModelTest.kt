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

    

    @Test
    fun uiState_init_isLoading() = runTest {
        val vm = makeViewModel()
        assertThat(vm.uiState.value).isInstanceOf(OperationHistoryUiState.Loading::class.java)
    }

    

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
}
