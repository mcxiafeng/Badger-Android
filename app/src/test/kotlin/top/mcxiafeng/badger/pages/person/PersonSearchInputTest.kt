package top.mcxiafeng.badger.pages.person

import android.app.Application
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModelStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PersonContactRow
import top.mcxiafeng.badger.data.PersonContactSection
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PersonSearchInputTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var viewModel: PersonViewModel
    private val store = ViewModelStore()

    @Before fun setup() {
        val contacts = mockk<ContactRepository>(relaxed = true)
        val profile = mockk<UserProfileRepository>(relaxed = true)
        val tags = mockk<TagRepository>(relaxed = true)
        every { contacts.getLetterIndex() } returns flowOf(listOf(LetterCount("C", 1)))
        every { contacts.observeContactById(any()) } returns flowOf(null)
        every { profile.getUserProfile() } returns flowOf(null)
        coEvery { profile.getUserProfileOnce() } returns null
        every { tags.observeTagsForContacts(any()) } returns flowOf(emptyMap())
        every { contacts.personPagingSource(any()) } answers {
            val query = firstArg<String>()
            InputTestSource(if (CONTACT_NAME.startsWith(query)) listOf(ROW) else emptyList())
        }
        every { contacts.observePersonSections(any()) } answers {
            flowOf(if (CONTACT_NAME.startsWith(firstArg<String>())) listOf(PersonContactSection("", 0, "", 0, 1)) else emptyList())
        }
        viewModel = PersonViewModel(contacts, profile, tags, mockk(relaxed = true))
        store.put("person", viewModel)
        composeRule.setContent { MiuixTheme { PersonScreen(viewModel, {}, {}) } }
    }

    @After fun close() { store.clear() }

    @Test fun slowTypingAcrossEachDebounceKeepsTextOrderAndFocus() {
        composeRule.onNode(hasSetTextAction()).performClick()
        CONTACT_NAME.forEachIndexed { index, character ->
            composeRule.onNode(hasSetTextAction()).performTextInput(character.toString())
            val prefix = CONTACT_NAME.take(index + 1)
            awaitApplied(prefix)
            assertEditor(prefix, TextRange(prefix.length))
        }
    }

    @Test fun debounceAndEmptyResultKeepUserSelectionInsteadOfMovingItToTheEnd() {
        composeRule.onNode(hasSetTextAction()).performClick().performTextInput(CONTACT_NAME)
        composeRule.onNode(hasSetTextAction()).performTextInputSelection(TextRange(3))
        awaitApplied(CONTACT_NAME)
        assertEditor(CONTACT_NAME, TextRange(3))
        composeRule.onNode(hasSetTextAction()).performTextInput("X")
        assertEditor("CapX000001", TextRange(4))
        composeRule.onNode(hasSetTextAction()).performTextInputSelection(TextRange(3, 6))
        awaitApplied("CapX000001") // No matching rows: keep the selected range across the new session.
        assertEditor("CapX000001", TextRange(3, 6))
        composeRule.onNode(hasSetTextAction()).performTextInput("Q")
        assertEditor("CapQ0001", TextRange(4))
        awaitApplied("CapQ0001")
        composeRule.onNode(hasSetTextAction()).performTextReplacement(CONTACT_NAME)
        awaitApplied(CONTACT_NAME)
        assertEditor(CONTACT_NAME)
    }

    private fun assertEditor(text: String, selection: TextRange? = null) {
        val editor = composeRule.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))
            .assertIsFocused()
        if (selection != null) editor.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
    }

    private fun awaitApplied(query: String) {
        shadowOf(Looper.getMainLooper()).idleFor(DEBOUNCE_STEP_MS, TimeUnit.MILLISECONDS)
        composeRule.mainClock.advanceTimeBy(DEBOUNCE_STEP_MS)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.pagingSession.value.query == query
        }
        composeRule.waitForIdle()
    }

    private class InputTestSource(private val rows: List<PersonContactRow>) : PagingSource<Int, PersonContactRow>() {
        override val jumpingSupported = true
        override fun getRefreshKey(state: PagingState<Int, PersonContactRow>) = 0
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PersonContactRow> =
            LoadResult.Page(rows, null, null, itemsBefore = 0, itemsAfter = 0)
    }

    private companion object {
        const val CONTACT_NAME = "Cap000001"
        const val DEBOUNCE_STEP_MS = 350L
        const val WAIT_TIMEOUT_MS = 5_000L
        val ROW = PersonContactRow(Contact(id = 1, name = CONTACT_NAME, pinyinInitial = "C"), "C", "", 0, "", 0)
    }
}
