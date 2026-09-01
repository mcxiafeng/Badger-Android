package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.repository.TagRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TagManagerSettingsViewModelTest {

    private lateinit var repository: TagRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh recovers after observation flow exhausts retries`() = runTest(UnconfinedTestDispatcher()) {
        val failure = flow<List<TagCacheEntity>> { throw IllegalStateException("temporary failure") }
        val recovered = flow { emit(listOf(tag(1L, "Work"))) }
        every { repository.observeAllTags() } returnsMany listOf(failure, recovered)

        val vm = TagManagerSettingsViewModel(repository)
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        assertThat(vm.uiState.value).isInstanceOf(TagManagerUiState.Error::class.java)

        vm.onEvent(TagManagerEvent.Refresh)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state).isInstanceOf(TagManagerUiState.Success::class.java)
        assertThat((state as TagManagerUiState.Success).tags.map(TagCacheEntity::name))
            .containsExactly("Work")
    }

    private fun tag(id: Long, name: String) = TagCacheEntity(
        id = id,
        name = name,
        createTime = id,
    )
}
