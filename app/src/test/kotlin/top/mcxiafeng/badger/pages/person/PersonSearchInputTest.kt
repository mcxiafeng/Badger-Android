package top.mcxiafeng.badger.pages.person

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.viewmodel.compose.viewModel as entryViewModel
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
import top.mcxiafeng.badger.pages.person.contact.CreateContactPage
import top.mcxiafeng.badger.ui.navigation.Route
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PersonSearchInputTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var viewModel: PersonViewModel

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
        mountContacts { PersonViewModel(contacts, profile, tags, mockk(relaxed = true)) }
    }

    private fun mountContacts(newViewModel: () -> PersonViewModel) {
        composeRule.setContent {
            MiuixTheme {
                val nav = rememberNavController<Route>(Route.MainTabs)
                NavDisplay(navController = nav, onBack = { nav.pop() }) {
                    entry<Route.MainTabs> {
                        val mainViewModel = entryViewModel { newViewModel() }
                        PersonScreen(
                            viewModel = mainViewModel,
                            onScan = { nav.push(Route.Scanner()) },
                            onCreateContact = { nav.push(Route.CreateContact()) },
                            onContactClick = {},
                            onSearch = { nav.push(Route.ContactSearch) },
                        )
                    }
                    entry<Route.CreateContact> {
                        CreateContactPage(onBack = { nav.pop() }, viewModel = mockk())
                    }
                    entry<Route.Scanner> {
                        TextButton(text = "返回联系人列表", onClick = { nav.pop() })
                    }
                    entry<Route.ContactSearch> {
                        val searchViewModel = entryViewModel { newViewModel() }
                        viewModel = searchViewModel
                        Box(Modifier.testTag(SEARCH_PAGE)) {
                            ContactSearchScreen(viewModel = searchViewModel, onBack = { nav.pop() },
                                onContactClick = { nav.push(Route.ContactDetail(it)) })
                        }
                    }
                    entry<Route.ContactDetail> { route ->
                        TextButton(text = "联系人详情 ${route.contactId}", onClick = { nav.pop() })
                    }
                }
            }
        }
    }

    @Test fun slowTypingAcrossEachDebounceKeepsTextOrderAndFocus() {
        openSearch()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        CONTACT_NAME.forEachIndexed { index, character ->
            composeRule.onNode(hasSetTextAction()).performTextInput(character.toString())
            val prefix = CONTACT_NAME.take(index + 1)
            awaitApplied(prefix)
            assertEditor(prefix, TextRange(prefix.length))
        }
    }

    @Test fun debounceAndEmptyResultKeepUserSelectionInsteadOfMovingItToTheEnd() {
        openSearch()
        composeRule.onNode(hasSetTextAction()).performTextInput(CONTACT_NAME)
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
        composeRule.onNodeWithContentDescription("清除搜索").performClick()
        awaitApplied("")
        assertEditor("", TextRange.Zero)
    }

    @Test fun searchResultsOpenDetailsAndKeepQueryUntilSearchIsClosed() {
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        val search = composeRule.onNodeWithContentDescription("搜索联系人").fetchSemanticsNode()
        val more = composeRule.onNodeWithContentDescription("更多").fetchSemanticsNode()
        check(search.boundsInRoot.right <= more.boundsInRoot.left)

        openSearch()
        composeRule.onNode(hasSetTextAction()).performTextInput(CONTACT_NAME)
        awaitApplied(CONTACT_NAME)
        val result = searchResult()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { result.isDisplayed() }
        result.performClick()
        composeRule.onNodeWithText("联系人详情 1").assertIsDisplayed().performClick()
        composeRule.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(CONTACT_NAME)))
            .assertIsNotFocused()

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText(CONTACT_NAME).assertIsDisplayed()
        composeRule.onNodeWithText("长按联系人可多选删除").assertDoesNotExist()
        openSearch()
        assertEditor("")
    }

    @Test fun contactsOpenManualCreationFromMoreAndScannerFromFab() {
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("新建联系人").performClick()
        composeRule.onNodeWithText("输入联系人姓名").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.onNodeWithContentDescription("扫码").performClick()
        composeRule.onNodeWithText("返回联系人列表").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("更多").assertIsDisplayed()
    }

    @Test fun contactsUseMoreMenuToEnterAndCancelMultiSelect() {
        val row = composeRule.onNodeWithText(CONTACT_NAME)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { row.isDisplayed() }
        row.assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("从 QAuxiliary 导入 QQ 好友").assertIsDisplayed()
        composeRule.onNodeWithText("多选").performClick()

        assertSelectedCount(0)
        row.performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        assertSelectedCount(1)
        composeRule.onNodeWithContentDescription("删除").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("取消").performClick()
        composeRule.onNodeWithContentDescription("搜索联系人").assertIsDisplayed()
        row.assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
    }

    @Test fun searchResultsUseMoreMenuAndKeepQueryAfterCancellingMultiSelect() {
        openSearch()
        composeRule.onNode(hasSetTextAction()).performTextInput(CONTACT_NAME)
        awaitApplied(CONTACT_NAME)
        val row = searchResult()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { row.isDisplayed() }
        row.assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        composeRule.onNode(hasAnyAncestor(hasTestTag(SEARCH_PAGE)) and hasContentDescription("更多")).performClick()
        composeRule.onNodeWithText("多选").performClick()

        assertSelectedCount(0)
        row.performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNodeWithContentDescription("取消").performClick()
        composeRule.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(CONTACT_NAME)))
        row.assertIsDisplayed()
    }

    private fun assertSelectedCount(count: Int) {
        val titles = composeRule.onAllNodesWithText("已选择 $count 项")
        // The main TopAppBar composes both its small and large titles.
        check(titles.fetchSemanticsNodes().indices.any { titles[it].isDisplayed() }) {
            "No visible title for $count selected contacts"
        }
    }

    private fun searchResult(): SemanticsNodeInteraction = composeRule.onNode(
        hasAnyAncestor(hasTestTag(SEARCH_PAGE)) and hasText(CONTACT_NAME) and hasClickAction() and !hasSetTextAction(),
    )

    private fun openSearch() {
        composeRule.onNodeWithContentDescription("搜索联系人").performClick()
    }

    private fun assertEditor(text: String, selection: TextRange? = null) {
        val editor = composeRule.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))
            .assertIsFocused()
        if (selection != null) editor.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
    }

    private fun awaitApplied(query: String) {
        composeRule.runOnIdle { check(viewModel.searchQuery.value == query) }
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
        const val SEARCH_PAGE = "contact_search_page"
        const val DEBOUNCE_STEP_MS = 350L
        const val WAIT_TIMEOUT_MS = 5_000L
        val ROW = PersonContactRow(Contact(id = 1, name = CONTACT_NAME, pinyinInitial = "C"), "C", "", 0, "", 0)
    }
}
