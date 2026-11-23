package top.mcxiafeng.badger.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.pages.person.PersonViewModel
import top.mcxiafeng.badger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()
    private lateinit var repository: ContactRepository
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var appContext: Context
    private val store = ViewModelStore()

    @Before fun setup() {
        repository = mockk(relaxed = true)
        userProfileRepository = mockk(relaxed = true)
        tagRepository = mockk(relaxed = true)
        appContext = mockk(relaxed = true)
        every { repository.getLetterIndex() } returns flowOf(listOf(LetterCount("Z", 1), LetterCount("L", 1)))
        every { repository.observeContactById(any()) } returns flowOf(null)
        every { repository.observePersonSections(any()) } returns flowOf(emptyList())
        every { userProfileRepository.getUserProfile() } returns flowOf(null)
        every { tagRepository.observeTagsForContacts(any()) } returns flowOf(emptyMap())
    }

    @After fun close() { store.clear() }
    private fun viewModel() = PersonViewModel(repository, userProfileRepository, tagRepository, appContext).also { store.put("person", it) }

    @Test fun updateSearchQuery_updatesState() = runTest {
        val vm = viewModel()
        vm.updateSearchQuery("张")
        assertThat(vm.searchQuery.first()).isEqualTo("张")
    }

    @Test fun updateSearchQuery_blankQuery_resetsState() = runTest {
        val vm = viewModel()
        vm.updateSearchQuery("张"); vm.updateSearchQuery("")
        assertThat(vm.searchQuery.first()).isEmpty()
    }

    @Test fun letterCounts_exposedFromRepository() = runTest {
        val counts = listOf(LetterCount("A", 5), LetterCount("B", 3))
        every { repository.getLetterIndex() } returns flowOf(counts)
        assertThat(viewModel().letterCounts.first()).isEqualTo(counts)
    }

    @Test fun initialAndSearchSessionsNeverCollectFullContactsOrTagMembers() = runTest {
        val vm = viewModel()
        vm.updateSearchQuery("秋日"); advanceUntilIdle()
        assertThat(vm.pagingSession.value.query).isEqualTo("秋日")
        verify(exactly = 0) { repository.getAllContacts() }
        verify(exactly = 0) { repository.searchContacts(any()) }
        coVerify(exactly = 0) { tagRepository.getContactsByTag(any()) }
        assertThat(PersonViewModel.PAGING_CONFIG.maxSize).isEqualTo(180)
        assertThat(PersonViewModel.PAGING_CONFIG.enablePlaceholders).isTrue()
        assertThat(PersonViewModel.PAGING_CONFIG.jumpThreshold).isGreaterThan(0)
    }

    @Test fun selectAllResolvesCompleteResultIdsBeyondCachedPages() = runTest {
        val ids = (1L..1_000L).toList()
        coEvery { repository.getPersonContactIds("") } returns ids
        val vm = viewModel()
        vm.enterSelection()
        vm.toggleSelectAll(); advanceUntilIdle()
        assertThat(vm.selection.value.ids).hasSize(1_000)
        assertThat(vm.selection.value.allSelected).isTrue()
        vm.toggleSelectAll()
        assertThat(vm.selection.value.ids).isEmpty()
    }

    @Test fun changingQueryDiscardsOldSelectionAndIgnoresLateSelectAll() = runTest {
        val response = CompletableDeferred<List<Long>>()
        coEvery { repository.getPersonContactIds("") } coAnswers { response.await() }
        val vm = viewModel()
        vm.enterSelection()
        vm.toggleSelection(1); vm.toggleSelectAll(); runCurrent()
        vm.updateSearchQuery("new"); advanceTimeBy(301); runCurrent()
        response.complete((1L..300L).toList()); runCurrent()
        assertThat(vm.pagingSession.value.query).isEqualTo("new")
        assertThat(vm.selection.value.ids).isEmpty()
        assertThat(vm.selection.value.enabled).isFalse()
    }

    @Test fun reopeningSelectionIgnoresASelectAllFromThePreviousSelection() = runTest {
        val response = CompletableDeferred<List<Long>>()
        coEvery { repository.getPersonContactIds("") } coAnswers { response.await() }
        val vm = viewModel()
        vm.enterSelection()
        vm.toggleSelectAll()
        runCurrent()
        vm.exitSelection()
        vm.enterSelection()
        vm.toggleSelection(17)

        response.complete(listOf(17L, 42L))
        runCurrent()
        assertThat(vm.selection.value.ids).containsExactly(17L)
        assertThat(vm.selection.value.selectingAll).isFalse()
    }

    @Test fun tagObserverFollowsLoadedIdsAndReleasesEvictedPages() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.contactTagsMap.collect {} }
        vm.updateLoadedContactIds(listOf(2, 1, 2)); runCurrent()
        verify { tagRepository.observeTagsForContacts(listOf(1, 2)) }
        vm.updateLoadedContactIds(listOf(90, 91)); runCurrent()
        verify { tagRepository.observeTagsForContacts(listOf(90, 91)) }
        verify(exactly = 0) { tagRepository.observeTagsForContacts(listOf(1, 2, 90, 91)) }
    }

    @Test fun deletionFailurePreservesSelectionAndReportsFailure() = runTest {
        coEvery { repository.deleteByIds(any()) } throws IllegalStateException("write failed")
        val vm = viewModel()
        vm.enterSelection()
        vm.toggleSelection(17)
        assertThat(vm.deleteSelectedContacts()).isFalse()
        assertThat(vm.selection.value.ids).containsExactly(17L)
        assertThat(vm.message.value).isEqualTo("删除失败，请重试")
    }

    @Test fun finishingAnOldQueryDeletionDoesNotClearTheNewQuerySelection() = runTest {
        val saved = CompletableDeferred<Unit>()
        coEvery { repository.deleteByIds(any()) } coAnswers { saved.await() }
        val vm = viewModel()
        vm.enterSelection()
        vm.toggleSelection(17)
        val deletion = launch { vm.deleteSelectedContacts() }
        runCurrent()
        vm.updateSearchQuery("new")
        advanceTimeBy(301)
        runCurrent()
        vm.enterSelection()
        vm.toggleSelection(42)
        saved.complete(Unit)
        deletion.join()
        assertThat(vm.selection.value.ids).containsExactly(42L)
        assertThat(vm.selection.value.enabled).isTrue()
    }

    @Test fun deletionWaitsForDatabaseSuccessBeforeAnnouncingSuccess() = runTest {
        val saved = CompletableDeferred<Unit>()
        coEvery { repository.deleteByIds(any()) } coAnswers { saved.await() }
        val vm = viewModel()
        vm.enterSelection()
        vm.toggleSelection(17)
        val job = launch { vm.deleteSelectedContacts() }; runCurrent()
        assertThat(vm.message.value).isNull()
        assertThat(vm.selection.value.ids).containsExactly(17L)
        saved.complete(Unit); job.join()
        assertThat(vm.selection.value.ids).isEmpty()
        assertThat(vm.message.value).isEqualTo("已删除 1 个联系人")
    }
}
