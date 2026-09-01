package top.mcxiafeng.badger.pages.person

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.repository.CommitResult
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository

class PersonViewModelTest {

    @Test
    fun deleteContacts_failedDelete_restoresOnlyFailedContactsInOriginalOrder() = runTest {
        val repository = mockk<ContactRepository>()
        val userProfileRepository = mockk<UserProfileRepository>()
        val tagRepository = mockk<TagRepository>()
        val context = mockk<Context>(relaxed = true)
        val first = contact(1L, "First")
        val second = contact(2L, "Second")
        val third = contact(3L, "Third")

        coEvery { userProfileRepository.getUserProfile() } returns flowOf(null)
        coEvery { userProfileRepository.getUserProfileOnce() } returns null
        coEvery { repository.getAllContacts() } returns flowOf(listOf(first, second, third))
        coEvery { repository.getLetterIndex() } returns flowOf(emptyList())
        coEvery { tagRepository.observeTagsForContacts(any()) } returns flowOf(emptyMap())
        coEvery { repository.commitDelete(2L) } returns CommitResult.SentFailed("HTTP 503")
        coEvery { repository.commitDelete(3L) } returns CommitResult.SentSuccess

        val viewModel = PersonViewModel(
            repository = repository,
            userProfileRepository = userProfileRepository,
            tagRepository = tagRepository,
            appContext = context,
        )
        advanceUntilIdle()

        val result = viewModel.deleteContacts(listOf(2L, 3L))

        assertThat(result.requested).isEqualTo(2)
        assertThat(result.succeeded).isEqualTo(1)
        assertThat(result.failed).isEqualTo(1)
        assertThat(viewModel.contacts.value.map { it.id }).containsExactly(1L, 2L).inOrder()
    }

    private fun contact(id: Long, name: String) = ContactCacheEntity(
        id = id,
        name = name,
        serverId = "srv-$id",
        isLocalOnly = false,
        isDeleted = false,
        createTime = id,
        updateTime = id,
    )
}
