package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.robolectric.shadows.ShadowLog
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.repository.AddPlatformsResult
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.domain.ParsePlatformInputUseCase
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-mdpi")
class AddPlatformScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val repository = mockk<ContactRepository>()
    private lateinit var viewModel: AddPlatformViewModel

    @Before
    fun setUp() {
        coEvery { repository.getContactById(CONTACT_ID) } returns Contact(id = CONTACT_ID, name = "联系人")
        coEvery { repository.getContactPlatformKeys(CONTACT_ID) } returns emptySet()
        coEvery { repository.addContactPlatforms(CONTACT_ID, any()) } returns AddPlatformsResult.Saved
        viewModel = AddPlatformViewModel(repository, ParsePlatformInputUseCase(), SavedStateHandle())
    }

    @Test
    fun automaticInputShowsRecognizedPlatformsAndSavesToTheTarget() {
        mount()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("QQ:123456\nGitHub:octocat")
        composeRule.onNodeWithText("QQ").assertExists()
        composeRule.onNodeWithText("GitHub").assertExists()
        composeRule.onNodeWithText("保存 2 个平台").assertIsEnabled().performClick()
        composeRule.waitUntil { viewModel.uiState.value.isSaved }
        coVerify(exactly = 1) { repository.addContactPlatforms(CONTACT_ID, match { it.keys == setOf("qq", "github") }) }
    }

    @Test
    fun manualSearchAndFormKeepTheAutomaticDraftAndJoinItsSaveBatch() {
        mount()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("QQ:123456")
        composeRule.onNodeWithText("手动添加").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("github")
        composeRule.onNodeWithText("GitHub").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("octocat")
        composeRule.onNodeWithText("添加到列表").performClick()
        composeRule.onNodeWithText("保存 2 个平台").assertIsEnabled()
        composeRule.runOnIdle {
            assertThat(viewModel.uiState.value.input).isEqualTo("QQ:123456")
            assertThat(viewModel.uiState.value.pending["github"]?.value).isEqualTo("octocat")
        }
    }

    @Test
    fun letterIndexLocatesTheMatchingGroup() {
        mount()
        composeRule.onNodeWithText("手动添加").performClick()
        selectLetter("W")
        assertCurrentLetter("W")
        composeRule.onNodeWithText("微信").assertIsDisplayed()
    }

    @Test
    fun returningFromFormRestoresThePlatformListPositionAndSelectedLetter() {
        mount()
        composeRule.onNodeWithText("手动添加").performClick()
        selectLetter("W")
        val website = composeRule.onNodeWithText("网站").assertIsDisplayed()
        val previousTop = website.fetchSemanticsNode().boundsInRoot.top
        website.performClick()
        composeRule.onNodeWithText("添加到列表").assertExists()
        composeRule.onNodeWithContentDescription("返回").performClick()

        val restored = composeRule.onNodeWithText("网站").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertThat(restored.top).isWithin(1f).of(previousTop)
        assertCurrentLetter("W")
    }

    @Test
    fun clearingSearchRestoresTheListAndKeepsTheAutomaticDraft() {
        mount()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("QQ:123456")
        composeRule.onNodeWithText("手动添加").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("no_matching_platform")
        composeRule.onNodeWithText("没有找到匹配的平台").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("清除搜索").performClick()

        composeRule.onNodeWithContentDescription("平台字母索引").assertExists()
        composeRule.runOnIdle { assertThat(viewModel.uiState.value.input).isEqualTo("QQ:123456") }
    }

    @Test
    @Config(sdk = [28], qualifiers = "w1000dp-h900dp-mdpi")
    fun wideScreenKeepsInputAndSaveCenteredAtAReadableWidth() {
        mount()
        val input = composeRule.onAllNodes(hasSetTextAction())[0].fetchSemanticsNode().boundsInRoot
        val save = composeRule.onNodeWithText("保存").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val maxWidth = with(composeRule.density) { 720.dp.toPx() }

        assertThat(input.width).isAtMost(maxWidth)
        assertThat(input.center.x).isWithin(1f).of(root.center.x)
        assertThat(save.center.x).isWithin(1f).of(input.center.x)
    }

    @Test
    fun unresolvedInputDisablesSaveAndShowsAnActionableReason() {
        mount()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("123456")
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
        composeRule.onNodeWithText("未识别平台，请使用“平台：账号”或手动添加").assertExists()
        coVerify(exactly = 0) { repository.addContactPlatforms(any(), any()) }
    }

    @Test
    fun saveRemainsReachableAtDoubleFontScale() {
        mount(fontScale = 2f)
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("QQ:123456")
        val button = composeRule.onNodeWithText("保存 1 个平台").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(button.bottom).isAtMost(root.bottom)
        assertThat(button.top).isAtLeast(root.top)
    }

    @Test
    fun pullingPastManualListBottomCannotPaintOverTheFixedSearchAndCustomAction() {
        mount()
        composeRule.onNodeWithText("手动添加").performClick()
        val scroller = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)
        val list = composeRule.onNode(scroller)
        val platforms = viewModel.uiState.value.platforms
        val lastName = platforms.last().definition.displayName
        // Jump to the last row without repeated semantic ScrollBy accumulating a prior edge offset.
        list.performScrollToIndex(platforms.size + platforms.map { it.initial }.distinct().size - 1)
        val restingLastRow = composeRule.onNodeWithText(lastName).fetchSemanticsNode().boundsInRoot
        val search = composeRule.onNode(hasSetTextAction())
        val custom = composeRule.onNodeWithText("自定义平台")
        val searchBounds = search.fetchSemanticsNode().boundsInRoot
        val customBounds = custom.fetchSemanticsNode().boundsInRoot
        val viewport = composeRule.onNodeWithTag("manual_platform_viewport").fetchSemanticsNode().boundsInRoot
        val viewportTop = viewport.top
        val pull = viewport.height / 3f
        // Inject screen-relative points: the scrolling child itself moves under the held finger.
        composeRule.mainClock.autoAdvance = false
        composeRule.onRoot().performTouchInput {
            down(viewport.center)
            moveBy(Offset(0f, -pull))
            moveBy(Offset(0f, -pull / 4f))
        }
        composeRule.mainClock.advanceTimeBy(48)
        val stretchedLastRow = composeRule.onNodeWithText(lastName).fetchSemanticsNode().boundsInRoot
        assertWithMessage("viewport=$viewport; before=$restingLastRow; held=$stretchedLastRow; spring=${ShadowLog.getLogsForTag("SpringOverscrollTester").takeLast(3).map { it.msg }}")
            .that(restingLastRow.top - stretchedLastRow.top).isGreaterThan(pull / 4f)
        val visibleRows = composeRule.onAllNodes(hasAnyAncestor(scroller) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.height > 0f && it.width > 0f }
        assertThat(visibleRows).isNotEmpty()
        visibleRows.forEach { assertThat(it.top).isAtLeast(viewportTop - 1f) }
        assertThat(search.fetchSemanticsNode().boundsInRoot).isEqualTo(searchBounds)
        assertThat(custom.fetchSemanticsNode().boundsInRoot).isEqualTo(customBounds)
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        custom.performClick()
        composeRule.onNodeWithText("平台名称").assertIsDisplayed()
    }

    private fun selectLetter(letter: String) {
        val actions = composeRule.onNodeWithContentDescription("平台字母索引")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        composeRule.runOnIdle { check(actions.single { it.label == "定位到 $letter 开头的平台" }.action()) }
    }

    private fun assertCurrentLetter(letter: String) {
        composeRule.onNodeWithContentDescription("平台字母索引").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "当前字母 $letter"))
    }

    private fun mount(fontScale: Float = 1f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MiuixTheme {
                    val state by viewModel.uiState.collectAsState()
                    AddPlatformScreen(state, onBack = { viewModel.back() },
                        onInputChange = viewModel::updateInput, onManual = viewModel::openManual,
                        onQueryChange = viewModel::updateQuery, onSelect = viewModel::selectPlatform,
                        onRemovePending = viewModel::removePending, onAccountChange = viewModel::updateAccount,
                        onAuxiliaryChange = viewModel::updateAuxiliary, onNameChange = viewModel::updateDisplayName,
                        onCustomNameChange = viewModel::updateCustomName, onSubmit = {
                            if (state.step == AddPlatformStep.FORM) viewModel.addManualEntry() else viewModel.save()
                        })
                }
            }
        }
        composeRule.runOnIdle { viewModel.load(CONTACT_ID) }
        composeRule.waitUntil { !viewModel.uiState.value.isLoading }
    }

    private companion object { const val CONTACT_ID = 42L }
}
