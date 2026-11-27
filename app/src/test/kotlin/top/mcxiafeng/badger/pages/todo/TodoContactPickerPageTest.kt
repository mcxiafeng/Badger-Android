package top.mcxiafeng.badger.pages.todo

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PersonContactRow
import top.mcxiafeng.badger.data.PersonContactSection
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.pages.person.PersonViewModel
import top.mcxiafeng.badger.ui.glass.RetainedPopupHost
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TodoContactPickerPageTest {
    @get:Rule val compose = createComposeRule()
    private val contacts = mockk<ContactRepository>()
    private var confirmed: List<Long>? = null
    private var cancels = 0

    @Test fun startingFromTheDraftKeepsExistingPicksCheckedAndTogglesThemOff() {
        mount(selected = listOf(1L), rows = true)
        compose.onNodeWithContentDescription("取消").assertIsDisplayed()
        compose.onNodeWithContentDescription("确定").assertIsDisplayed()
        compose.onNodeWithText("选择联系人").assertIsDisplayed()
        awaitRow("陈晨").performClick()
        awaitRow("林岚").performClick()
        compose.onNodeWithContentDescription("确定").performClick()
        compose.runOnIdle { assertThat(confirmed).containsExactly(2L).inOrder() }
    }

    @Test fun confirmingWithNoChangesReturnsTheOriginalDraftSelection() {
        mount(selected = listOf(1L, 3L), rows = true)
        compose.onNodeWithContentDescription("确定").performClick()
        compose.runOnIdle { assertThat(confirmed).containsExactly(1L, 3L) }
    }

    @Test fun cancellingNeverAppliesAnything() {
        mount(selected = listOf(1L), rows = true)
        awaitRow("林岚").performClick()
        compose.onNodeWithContentDescription("取消").performClick()
        compose.runOnIdle {
            assertThat(cancels).isEqualTo(1)
            assertThat(confirmed).isNull()
        }
    }

    @Test fun emptyResultShowsThePlainEmptyCopyInsteadOfAScrollableList() {
        mount(selected = emptyList(), rows = false)
        awaitText("还没有联系人")
        compose.onNodeWithText("还没有联系人").assertIsDisplayed()
        // The search field is a list item, so it must survive the empty branch on its own.
        compose.onNodeWithText("搜索姓名、账号或标签").assertIsDisplayed()
        compose.onAllNodesWithText("陈晨").assertCountEquals(0)
        compose.onNodeWithContentDescription("确定").assertIsDisplayed()
    }

    private fun mount(selected: List<Long>, rows: Boolean) {
        every { contacts.personPagingSource(any()) } returns PickerSource(if (rows) ROWS else emptyList())
        every { contacts.observePersonSections(any()) } returns flowOf(emptyList<PersonContactSection>())
        every { contacts.getLetterIndex() } returns flowOf(listOf(LetterCount("#", if (rows) ROWS.size else 0)))
        every { contacts.observeContactById(any()) } returns flowOf(null)
        every { contacts.searchContacts(any()) } returns flowOf(emptyList())
        val profile = mockk<UserProfileRepository>(relaxed = true).also {
            every { it.getUserProfile() } returns flowOf(null)
        }
        val viewModel = PersonViewModel(contacts, profile, mockk<TagRepository>(relaxed = true), mockk(relaxed = true))
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    TodoContactPickerPage(selected = selected, onCancel = { cancels++ },
                        onConfirm = { confirmed = it }, viewModel = viewModel)
                }
            }
        }
    }

    private fun awaitRow(name: String) = awaitText(name)

    private fun awaitText(text: String): SemanticsNodeInteraction {
        compose.waitUntil(WAIT_TIMEOUT_MS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNodeWithText(text)
    }

    private class PickerSource(private val rows: List<PersonContactRow>) : PagingSource<Int, PersonContactRow>() {
        // PersonViewModel's shared PagingConfig sets jumpThreshold.
        override val jumpingSupported = true
        override fun getRefreshKey(state: PagingState<Int, PersonContactRow>) = 0
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PersonContactRow> =
            LoadResult.Page(rows, null, null, itemsBefore = 0, itemsAfter = 0)
    }

    private companion object {
        const val WAIT_TIMEOUT_MS = 5_000L
        val ROWS = listOf(
            PersonContactRow(Contact(id = 1, name = "陈晨", pinyinInitial = "C"), "C", "", 0, "", 0),
            PersonContactRow(Contact(id = 2, name = "林岚", pinyinInitial = "L"), "L", "", 0, "", 0),
            PersonContactRow(Contact(id = 3, name = "周舟", pinyinInitial = "Z"), "Z", "", 0, "", 0),
        )
    }
}
