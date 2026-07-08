package top.mcxiafeng.badger.viewmodels

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.pages.person.PersonViewModel
import top.mcxiafeng.badger.testutil.MainDispatcherRule

class PersonViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: ContactRepository
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var appContext: Context

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        userProfileRepository = mockk(relaxed = true)
        appContext = mockk(relaxed = true)
        every { repository.getLetterIndex() } returns flowOf(
            listOf(LetterCount("Z", 1), LetterCount("L", 1))
        )
    }

    @Test
    fun updateSearchQuery_updatesState() = runTest {
        val viewModel = PersonViewModel(repository, userProfileRepository, appContext)

        viewModel.updateSearchQuery("张")
        assertThat(viewModel.searchQuery.first()).isEqualTo("张")
    }

    @Test
    fun updateSearchQuery_blankQuery_resetsState() = runTest {
        val viewModel = PersonViewModel(repository, userProfileRepository, appContext)

        viewModel.updateSearchQuery("张")
        viewModel.updateSearchQuery("")
        assertThat(viewModel.searchQuery.first()).isEmpty()
    }

    @Test
    fun letterCounts_exposedFromRepository() = runTest {
        val counts = listOf(LetterCount("A", 5), LetterCount("B", 3))
        every { repository.getLetterIndex() } returns flowOf(counts)

        val viewModel = PersonViewModel(repository, userProfileRepository, appContext)
        val result = viewModel.letterCounts.first()
        assertThat(result).hasSize(2)
        assertThat(result[0].letter).isEqualTo("A")
        assertThat(result[0].count).isEqualTo(5)
    }
}
