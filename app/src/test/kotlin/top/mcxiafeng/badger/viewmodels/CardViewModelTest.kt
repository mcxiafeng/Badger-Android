package top.mcxiafeng.badger.viewmodels

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.pages.card.CardUiState
import top.mcxiafeng.badger.pages.card.CardViewModel
import top.mcxiafeng.badger.testutil.MainDispatcherRule
import top.mcxiafeng.badger.testutil.TestDataProvider

class CardViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var collectionRepository: CollectionRepository
    private lateinit var contactRepository: ContactRepository
    private lateinit var fieldRepository: FieldRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var viewModel: CardViewModel
    private val collectionsFlow = MutableStateFlow<List<CollectionWithCount>>(emptyList())

    @Before
    fun setup() {
        collectionRepository = mockk(relaxed = true)
        contactRepository = mockk(relaxed = true)
        fieldRepository = mockk(relaxed = true)
        tagRepository = mockk(relaxed = true)
        every { collectionRepository.getCollectionsWithCount() } returns collectionsFlow
    }

    @Test
    fun initialState_isLoading() = runTest {
        viewModel = CardViewModel(collectionRepository, contactRepository, fieldRepository, tagRepository)
        assertThat(viewModel.uiState.value).isInstanceOf(CardUiState.Loading::class.java)
    }

    @Test
    fun loadCollections_success_setsSuccessState() = runTest {
        val collections = listOf(
            CollectionWithCount(TestDataProvider.testCardCollection(name = "工作"), 5)
        )
        collectionsFlow.value = collections
        viewModel = CardViewModel(collectionRepository, contactRepository, fieldRepository, tagRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(CardUiState.Success::class.java)
        assertThat((state as CardUiState.Success).collections).hasSize(1)
    }

    @Test
    fun createCollection_callsRepositoryInsert() = runTest {
        collectionsFlow.value = emptyList()
        viewModel = CardViewModel(collectionRepository, contactRepository, fieldRepository, tagRepository)
        advanceUntilIdle()

        viewModel.createCollection("新名片夹", "描述")
        advanceUntilIdle()

        coVerify { collectionRepository.insertCollection(match { it.name == "新名片夹" && it.description == "描述" }) }
    }

    @Test
    fun deleteCollection_callsRepositoryDelete() = runTest {
        collectionsFlow.value = emptyList()
        viewModel = CardViewModel(collectionRepository, contactRepository, fieldRepository, tagRepository)
        advanceUntilIdle()

        val collection = CollectionWithCount(TestDataProvider.testCardCollection(id = 1, name = "工作"), 0)
        viewModel.deleteCollection(collection)
        advanceUntilIdle()

        coVerify { collectionRepository.deleteCollection(collection.collection) }
    }
}
