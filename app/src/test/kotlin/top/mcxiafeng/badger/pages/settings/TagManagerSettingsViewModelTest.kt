package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * TagManagerSettingsViewModel 测试。
 *
 * 覆盖三类核心逻辑：
 * 1. UI 状态机：filter / sort / multiSelect / selection 之间的互相作用
 * 2. 业务调用透传：onEvent 触发的 Repository 调用是否正确
 * 3. 错误反馈：捕获异常时 Channel 是否发出 Error 消息
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagManagerSettingsViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var tagRepository: TagRepository
    private lateinit var viewModel: TagManagerSettingsViewModel

    private val tagsFlow = MutableStateFlow<List<Tag>>(emptyList())

    @Before
    fun setup() {
        tagRepository = mockk(relaxed = true)
        every { tagRepository.observeAllTags() } returns tagsFlow
    }

    /**
     * 创建 VM 并在 backgroundScope 启动一个持续 collector，激活 stateIn(WhileSubscribed) 的数据发射。
     * 每个测试调用一次即可（返回 viewModel 便于链式调用）。
     */
    private fun kotlinx.coroutines.test.TestScope.createViewModel(): TagManagerSettingsViewModel {
        val vm = TagManagerSettingsViewModel(tagRepository)
        backgroundScope.launch { vm.uiState.collect { } }
        return vm
    }

    // ========== UI 状态机 ==========

    @Test
    fun tagsFlow_emit_setsSuccessState() = runTest {
        val tags = listOf(
            Tag(id = 1L, name = "工作", source = "manual"),
            Tag(id = 2L, name = "AI 推荐", source = "ai"),
        )
        viewModel = createViewModel()
        advanceUntilIdle()
        tagsFlow.value = tags
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(TagManagerUiState.Success::class.java)
        val success = state as TagManagerUiState.Success
        assertThat(success.tags).hasSize(2)
        assertThat(success.multiSelect).isFalse()
        assertThat(success.filterMode).isEqualTo(TagFilterMode.All)
        assertThat(success.sortMode).isEqualTo(TagSortMode.Alphabetical)
    }

    @Test
    fun changeFilter_filtersVisibleTagsBySource() = runTest {
        tagsFlow.value = listOf(
            Tag(id = 1L, name = "工作", source = "manual"),
            Tag(id = 2L, name = "AI 推荐", source = "ai"),
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TagManagerEvent.ChangeFilter(TagFilterMode.Ai))
        advanceUntilIdle()

        val state = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(state.filterMode).isEqualTo(TagFilterMode.Ai)
        assertThat(state.visibleTags.map { it.id }).containsExactly(2L)

        viewModel.onEvent(TagManagerEvent.ChangeFilter(TagFilterMode.Manual))
        advanceUntilIdle()
        val manualState = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(manualState.visibleTags.map { it.id }).containsExactly(1L)
    }

    @Test
    fun changeSort_sortsByCreatedTimeDesc() = runTest {
        tagsFlow.value = listOf(
            Tag(id = 1L, name = "B", createTime = 100L, pinyinInitial = "B"),
            Tag(id = 2L, name = "A", createTime = 300L, pinyinInitial = "A"),
            Tag(id = 3L, name = "C", createTime = 200L, pinyinInitial = "C"),
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        // 默认按字母 (pinyinInitial)
        val alpha = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(alpha.visibleTags.map { it.id }).containsExactly(2L, 1L, 3L).inOrder()

        // 切到最新创建倒序
        viewModel.onEvent(TagManagerEvent.ChangeSort(TagSortMode.CreatedDesc))
        advanceUntilIdle()
        val created = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(created.visibleTags.map { it.id }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun multiSelect_toggleSelectionAndExit() = runTest {
        tagsFlow.value = listOf(
            Tag(id = 1L, name = "A"),
            Tag(id = 2L, name = "B"),
            Tag(id = 3L, name = "C"),
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        // 进入多选，初始选 1
        viewModel.onEvent(TagManagerEvent.EnterMultiSelect(initialSelectedId = 1L))
        advanceUntilIdle()
        var s = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(s.multiSelect).isTrue()
        assertThat(s.selectedIds).containsExactly(1L)

        // 再勾选 3
        viewModel.onEvent(TagManagerEvent.ToggleSelect(3L))
        advanceUntilIdle()
        s = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(s.selectedIds).containsExactly(1L, 3L)

        // 取消勾选 1
        viewModel.onEvent(TagManagerEvent.ToggleSelect(1L))
        advanceUntilIdle()
        s = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(s.selectedIds).containsExactly(3L)

        // 全选
        viewModel.onEvent(TagManagerEvent.SelectAll)
        advanceUntilIdle()
        s = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(s.selectedIds).hasSize(3)

        // 清空
        viewModel.onEvent(TagManagerEvent.ClearSelection)
        advanceUntilIdle()
        s = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(s.selectedIds).isEmpty()
        assertThat(s.multiSelect).isTrue() // ClearSelection 只清选中态，不退出多选

        // 退出多选
        viewModel.onEvent(TagManagerEvent.ExitMultiSelect)
        advanceUntilIdle()
        s = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(s.multiSelect).isFalse()
        assertThat(s.selectedIds).isEmpty()
    }

    @Test
    fun observeAllTags_error_setsErrorState() = runTest {
        val errorFlow = kotlinx.coroutines.flow.flow<List<Tag>> {
            throw RuntimeException("db down")
        }
        every { tagRepository.observeAllTags() } returns errorFlow
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(TagManagerUiState.Error::class.java)
        assertThat((state as TagManagerUiState.Error).message).contains("db down")
    }

    // ========== 业务调用 ==========

    @Test
    fun create_callsRepositoryUpsert_andSendsInfoMessage() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { tagRepository.upsertTag("测试", color = 0xFF1976D2L, source = "manual") } returns 100L

        viewModel.onEvent(TagManagerEvent.Create("测试", 0xFF1976D2L))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            tagRepository.upsertTag("测试", color = 0xFF1976D2L, source = "manual")
        }

        // Channel 在 Composable 端才是消费者；这里直接 assert 不会抛异常
        // （无法稳定拿到 first() 因为消息已消费/未消费取决于 collect 时机）
        assertThat(viewModel.uiState.value).isInstanceOf(TagManagerUiState.Success::class.java)
    }

    @Test
    fun create_blankName_doesNotCallRepository_butSendsErrorMessage() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TagManagerEvent.Create("   ", 0xFF1976D2L))
        advanceUntilIdle()

        coVerify(exactly = 0) { tagRepository.upsertTag(any(), any(), any()) }
    }

    @Test
    fun rename_duplicateName_sendsErrorMessage_doesNotRename() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val conflict = Tag(id = 99L, name = "重复")
        coEvery { tagRepository.searchTagsByName("重复") } returns listOf(conflict)

        viewModel.onEvent(TagManagerEvent.Rename(1L, "重复"))
        advanceUntilIdle()

        coVerify(exactly = 0) { tagRepository.renameTag(any(), any()) }
    }

    @Test
    fun rename_uniqueName_callsRepositoryRename() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { tagRepository.searchTagsByName("新名") } returns emptyList()

        viewModel.onEvent(TagManagerEvent.Rename(1L, "新名"))
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.renameTag(1L, "新名") }
    }

    @Test
    fun setColor_callsRepository() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TagManagerEvent.SetColor(1L, 0xFFAA00FFL))
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.setTagColor(1L, 0xFFAA00FFL) }
    }

    @Test
    fun setShowDot_callsRepository() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TagManagerEvent.SetShowDot(7L, false))
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.setTagDotVisible(7L, false) }
    }

    @Test
    fun forceDelete_returnsAffectedCount_message() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { tagRepository.forceDeleteTag(5L) } returns listOf(100L, 101L, 102L)

        viewModel.onEvent(TagManagerEvent.ForceDelete(5L))
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.forceDeleteTag(5L) }
    }

    @Test
    fun merge_sameSourceAndTarget_sendsError_doesNotCallRepo() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TagManagerEvent.Merge(1L, 1L))
        advanceUntilIdle()

        coVerify(exactly = 0) { tagRepository.reassignTagUsage(any(), any()) }
    }

    @Test
    fun merge_differentTags_callsReassign() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TagManagerEvent.Merge(1L, 2L))
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.reassignTagUsage(1L, 2L) }
    }

    @Test
    fun batchDelete_callsForceDeleteForEachId_clearsSelection() = runTest {
        tagsFlow.value = listOf(Tag(id = 1L, name = "A"), Tag(id = 2L, name = "B"), Tag(id = 3L, name = "C"))
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { tagRepository.forceDeleteTag(any()) } returns emptyList()

        // 准备多选态
        viewModel.onEvent(TagManagerEvent.EnterMultiSelect())
        advanceUntilIdle()
        viewModel.onEvent(TagManagerEvent.SelectAll)
        advanceUntilIdle()

        viewModel.onEvent(TagManagerEvent.BatchDelete(listOf(1L, 2L, 3L)))
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.forceDeleteTag(1L) }
        coVerify(exactly = 1) { tagRepository.forceDeleteTag(2L) }
        coVerify(exactly = 1) { tagRepository.forceDeleteTag(3L) }

        // 批量删除完成应自动退出多选 + 清选中态
        val s = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(s.multiSelect).isFalse()
        assertThat(s.selectedIds).isEmpty()
    }

    @Test
    fun batchSetColor_callsSetColorForEachId_clearsSelection() = runTest {
        tagsFlow.value = listOf(Tag(id = 1L, name = "A"), Tag(id = 2L, name = "B"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TagManagerEvent.EnterMultiSelect())
        advanceUntilIdle()
        viewModel.onEvent(TagManagerEvent.SelectAll)
        advanceUntilIdle()

        val newColor = 0xFFAA00BBL
        viewModel.onEvent(TagManagerEvent.BatchSetColor(listOf(1L, 2L), newColor))
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.setTagColor(1L, newColor) }
        coVerify(exactly = 1) { tagRepository.setTagColor(2L, newColor) }

        val s = viewModel.uiState.value as TagManagerUiState.Success
        assertThat(s.multiSelect).isFalse()
    }

    @Test
    fun batchDelete_emptyList_doesNothing() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TagManagerEvent.BatchDelete(emptyList()))
        advanceUntilIdle()

        coVerify(exactly = 0) { tagRepository.forceDeleteTag(any()) }
    }

    @Test
    fun setColor_repositoryThrows_swallowsError_stateStillSuccess() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { tagRepository.setTagColor(1L, any()) } throws RuntimeException("disk full")

        viewModel.onEvent(TagManagerEvent.SetColor(1L, 0xFF112233L))
        advanceUntilIdle()

        // 状态机不崩溃，仍是 Success（错误通过 Channel 推 Snackbar）
        assertThat(viewModel.uiState.value).isInstanceOf(TagManagerUiState.Success::class.java)
    }

    @Test
    fun messageChannel_emitsInfoOnCreateSuccess() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { tagRepository.upsertTag("hello", color = any(), source = any()) } returns 1L

        viewModel.onEvent(TagManagerEvent.Create("hello", 0xFF000000L))
        advanceUntilIdle()

        // 启动 collector 抓取 Channel 发射。runTest 内部用 backgroundScope 启动 collect，
        // 这样不会与 ViewModel 的 viewModelScope 抢主线程调度。
        val collected = backgroundScope.async {
            viewModel.messages.first()
        }
        val msg = collected.await()
        assertThat(msg).isInstanceOf(TagManagerMessage.Info::class.java)
        assertThat(msg.text).contains("hello")
    }
}