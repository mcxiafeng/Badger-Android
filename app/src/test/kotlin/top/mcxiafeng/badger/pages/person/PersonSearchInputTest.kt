package top.mcxiafeng.badger.pages.person

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel as entryViewModel
import androidx.paging.PagingSource
import androidx.paging.PagingState
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import top.mcxiafeng.badger.AppTheme
import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PersonContactRow
import top.mcxiafeng.badger.data.PersonContactSection
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.pages.person.contact.ContactDraftUiState
import top.mcxiafeng.badger.pages.person.contact.CreateContactPage
import top.mcxiafeng.badger.pages.person.contact.CreateContactViewModel
import top.mcxiafeng.badger.ui.designsystem.BadgerDesignColors
import top.mcxiafeng.badger.ui.navigation.Route
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PersonSearchInputTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var viewModel: PersonViewModel
    private lateinit var contactsViewModel: PersonViewModel
    private lateinit var imageLoader: ImageLoader
    private val accountScope = AccountScope.local("avatar-test")
    private val scopedAvatarLoads = AtomicInteger()

    @OptIn(DelicateCoilApi::class)
    @Before fun setup() {
        val application = RuntimeEnvironment.getApplication()
        val avatarUri = "android.resource://${application.packageName}/${R.drawable.ic_qq}"
        imageLoader = ImageLoader.Builder(application).diskCache(null).eventListener(object : EventListener() {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                if (request.data == avatarUri && AccountContext.find(request.context)?.scope == accountScope) {
                    scopedAvatarLoads.incrementAndGet()
                }
            }
        }).build()
        SingletonImageLoader.setUnsafe(imageLoader)

        val contacts = mockk<ContactRepository>(relaxed = true)
        val profile = mockk<UserProfileRepository>(relaxed = true)
        val tags = mockk<TagRepository>(relaxed = true)
        val row = ROW.copy(contact = ROW.contact.copy(avatarUrl = avatarUri))
        every { contacts.getLetterIndex() } returns flowOf(listOf(LetterCount("C", 1)))
        every { contacts.observeContactById(any()) } returns flowOf(null)
        coEvery { contacts.getContactPlatforms(ROW.contact.id) } returns listOf(
            ContactPlatform(contactId = ROW.contact.id, platformKey = "qq", value = QQ_ID, jumpLink = QQ_LINK),
            ContactPlatform(contactId = ROW.contact.id, platformKey = "wechat", jumpLink = WECHAT_LINK),
        )
        every { profile.getUserProfile() } returns flowOf(null)
        coEvery { profile.getUserProfileOnce() } returns null
        every { tags.observeTagsForContacts(any()) } returns flowOf(emptyMap())
        every { contacts.personPagingSource(any()) } answers {
            val query = firstArg<String>()
            InputTestSource(if (CONTACT_NAME.startsWith(query)) listOf(row) else emptyList())
        }
        every { contacts.observePersonSections(any()) } answers {
            flowOf(if (CONTACT_NAME.startsWith(firstArg<String>())) listOf(PersonContactSection("", 0, "", 0, 1)) else emptyList())
        }
        mountContacts { PersonViewModel(contacts, profile, tags, mockk(relaxed = true)) }
    }

    @OptIn(DelicateCoilApi::class)
    @After fun clearImageLoader() {
        SingletonImageLoader.reset()
        if (::imageLoader.isInitialized) imageLoader.shutdown()
    }

    private fun mountContacts(newViewModel: () -> PersonViewModel) {
        composeRule.setContent {
            val context = LocalContext.current
            val accountContext = remember(context) { AccountContext(context, accountScope) }
            CompositionLocalProvider(LocalContext provides accountContext) {
                AppTheme { ContactTestNavigation(newViewModel) }
            }
        }
    }

    @Composable
    private fun ContactTestNavigation(newViewModel: () -> PersonViewModel) {
        val nav = rememberNavController<Route>(Route.MainTabs)
        NavDisplay(navController = nav, onBack = { nav.pop() }) {
            entry<Route.MainTabs> {
                val mainViewModel = entryViewModel { newViewModel() }
                contactsViewModel = mainViewModel
                PersonScreen(
                    viewModel = mainViewModel,
                    onScan = { nav.push(Route.Scanner()) },
                    onCreateContact = { nav.push(Route.CreateContact()) },
                    onContactClick = {},
                    onSearch = { nav.push(Route.ContactSearch) },
                )
            }
            entry<Route.CreateContact> {
                val draftViewModel = remember {
                    mockk<CreateContactViewModel>(relaxed = true).also {
                        every { it.uiState } returns MutableStateFlow(ContactDraftUiState(isLoading = false, targetInitialized = true))
                    }
                }
                CreateContactPage(onBack = { nav.pop() }, viewModel = draftViewModel)
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
        composeRule.onNodeWithText("姓名 / 昵称（必填）").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.onNodeWithContentDescription("扫码").performClick()
        composeRule.onNodeWithText("返回联系人列表").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("更多").assertIsDisplayed()
    }

    @Test fun contactListTitleShowsContactCountSubtitle() {
        // Miuix TopAppBar renders the subtitle in both the expanded (large title) and collapsed
        // states, so both copies appear in the merged semantics tree.
        composeRule.onAllNodesWithText("共 1 位联系人").assertCountEquals(2)
    }

    @Test fun selectionModeHidesContactCountSubtitle() {
        composeRule.onAllNodesWithText("共 1 位联系人").assertCountEquals(2)
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("多选").performClick()
        composeRule.onAllNodesWithText("已选择 0 项").assertCountEquals(2)
        composeRule.onAllNodesWithText("共 1 位联系人").assertCountEquals(0)
    }

    @Test fun longPressInContactsOpensShareAndSwitchesTheDisplayedQr() {
        val row = composeRule.onNodeWithText(CONTACT_NAME)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { row.isDisplayed() }
        val avatarImage = hasContentDescription("头像")
        composeRule.onNode(avatarImage, useUnmergedTree = true).assertIsDisplayed()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { scopedAvatarLoads.get() >= 1 }
        val listAvatarLoads = scopedAvatarLoads.get()
        row.performTouchInput { longClick() }
        composeRule.onNodeWithText("分享").performClick()
        awaitShareQr("QQ二维码")
        val shareAvatar = composeRule.onNode(hasAnyAncestor(isDialog()) and avatarImage, useUnmergedTree = true)
        shareAvatar.assertIsDisplayed()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { scopedAvatarLoads.get() > listAvatarLoads }
        val avatarNodeId = shareAvatar.fetchSemanticsNode().id
        composeRule.onNodeWithText(QQ_ID).assertIsDisplayed()
        composeRule.onNodeWithText(QQ_LINK).assertDoesNotExist()
        val pager = composeRule.onNodeWithTag("contact_share_platform_pager")
        pager.performTouchInput { swipeLeft() }
        awaitShareQr("微信二维码")
        shareAvatar.assertIsDisplayed()
        check(shareAvatar.fetchSemanticsNode().id == avatarNodeId) { "Platform switching recreated the contact avatar" }
        composeRule.onNodeWithText(WECHAT_LINK).assertDoesNotExist()
        composeRule.onNodeWithText(QQ_ID).assertDoesNotExist()
        pager.performTouchInput { swipeRight() }
        awaitShareQr("QQ二维码")
        composeRule.onNodeWithContentDescription("添加平台").assertDoesNotExist()
        dismissShareDialogByTappingOutside()
        row.assertIsDisplayed()
    }

    @Test fun systemShareSendsOnlyTheCurrentPlatformsContactInformation() {
        val row = composeRule.onNodeWithText(CONTACT_NAME)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { row.isDisplayed() }
        row.performTouchInput { longClick() }
        composeRule.onNodeWithText("分享").performClick()
        awaitShareQr("QQ二维码")
        val share = composeRule.onNodeWithContentDescription("分享联系方式")
        share.assertIsDisplayed().performTouchInput { click() }
        assertSystemShareText("$CONTACT_NAME\nQQ：$QQ_ID\n$QQ_LINK")

        composeRule.onNodeWithTag("contact_share_platform_pager").performTouchInput { swipeLeft() }
        awaitShareQr("微信二维码")
        share.performTouchInput { click() }
        assertSystemShareText("$CONTACT_NAME\n微信：$WECHAT_LINK")
        composeRule.onNodeWithContentDescription("微信二维码").assertIsDisplayed()
    }

    private fun assertSystemShareText(expected: String) {
        val chooser = checkNotNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = checkNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(expected, send.getStringExtra(Intent.EXTRA_TEXT))
        check(!send.hasExtra(Intent.EXTRA_STREAM))
    }

    @Test
    @Config(qualifiers = "night")
    fun sharingQrUsesTheDarkThemeAndKeepsTheSamePayload() {
        ShadowLog.clear()
        val row = composeRule.onNodeWithText(CONTACT_NAME)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { row.isDisplayed() }
        row.performTouchInput { longClick() }
        composeRule.onNodeWithText("分享").performClick()
        awaitShareQr("QQ二维码")
        composeRule.onNodeWithText(QQ_ID).assertIsDisplayed()
        check(ShadowLog.getLogsForTag("PersonViewModelTester").any {
            it.msg == "Generated contact share QR; whiteBackground=false"
        })

        assertQrDecodesWithBackground(Color.WHITE)
        assertQrDecodesWithBackground(BadgerDesignColors.contactShareQrDarkBackground.toArgb())
    }

    private fun assertQrDecodesWithBackground(backgroundColor: Int) = runBlocking {
        val bitmap = contactsViewModel.renderContactShareQr(QQ_LINK, backgroundColor).getOrThrow()
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            assertEquals(backgroundColor, pixels.first())
            assertEquals(backgroundColor, pixels.last())
            check(pixels.any { it == Color.BLACK })
            val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
            assertEquals(QQ_LINK, QRCodeReader().decode(image).text)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    @Config(qualifiers = "w400dp" + "-h800dp-xhdpi")
    fun tappingTheShareAvatarRotatesTheDialogAndMovesItToTheOppositeEdge() {
        val row = composeRule.onNodeWithText(CONTACT_NAME)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { row.isDisplayed() }
        row.performTouchInput { longClick() }
        composeRule.onNodeWithText("分享").performClick()
        awaitShareQr("QQ二维码")
        val qqQr = composeRule.onNodeWithContentDescription("QQ二维码")
        qqQr.assertWidthIsEqualTo(288.dp).assertHeightIsEqualTo(288.dp)

        val avatar = composeRule.onNode(SemanticsMatcher("分享卡片的头像") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == "旋转分享卡片"
        })
        val picker = composeRule.onNodeWithTag("contact_share_platform_pager")
        val share = composeRule.onNodeWithContentDescription("分享联系方式").assertIsDisplayed()
        val windowCenter = composeRule.onNode(isDialog()).centerY()
        fun cardCenter(qr: SemanticsNodeInteraction) = (share.centerY() + qr.centerY()) / 2f
        check(cardCenter(qqQr) > windowCenter && share.centerY() < picker.centerY() && picker.centerY() < qqQr.centerY()) {
            "Expected the header, platform carousel, and QR on the bottom half of the screen"
        }

        avatar.performTouchInput { click() }
        share.assertIsDisplayed()
        check(cardCenter(qqQr) < windowCenter && qqQr.centerY() < picker.centerY() && picker.centerY() < share.centerY()) {
            "Expected the inverted header, platform carousel, and QR on the top half of the screen"
        }
        // Screen-right is the next-page gesture when reading the card from the opposite side.
        picker.performTouchInput { swipeRight(startX = centerX, endX = centerX + width * 0.4f, durationMillis = 600L) }
        awaitShareQr("微信二维码")
        val wechatQr = composeRule.onNodeWithContentDescription("微信二维码")
        check(cardCenter(wechatQr) < windowCenter)

        avatar.performTouchInput { click() }
        check(cardCenter(wechatQr) > windowCenter && share.centerY() < picker.centerY() && picker.centerY() < wechatQr.centerY())
        avatar.performTouchInput { click() }
        share.performTouchInput { click() }
        assertSystemShareText("$CONTACT_NAME\n微信：$WECHAT_LINK")
        dismissShareDialogByTappingOutside()
    }

    private fun SemanticsNodeInteraction.centerY() = fetchSemanticsNode().boundsInRoot.center.y

    @Test fun longPressInSearchOpensShareAndKeepsTheQuery() {
        openSearch()
        composeRule.onNode(hasSetTextAction()).performTextInput(CONTACT_NAME)
        awaitApplied(CONTACT_NAME)
        val row = searchResult()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { row.isDisplayed() }
        row.performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.onNodeWithText("分享").performClick()
        awaitShareQr("QQ二维码")
        dismissShareDialogByTappingOutside()
        row.assertIsDisplayed()
        composeRule.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(CONTACT_NAME)))
    }

    private fun dismissShareDialogByTappingOutside() {
        val dialog = composeRule.onNode(isDialog())
        dialog.performTouchInput { click(Offset(1f, centerY)) }
        dialog.assertDoesNotExist()
    }

    private fun awaitShareQr(description: String) {
        val qr = composeRule.onNodeWithContentDescription(description)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { qr.isDisplayed() }
    }

    @Test fun contactsUseMoreMenuToEnterAndCancelMultiSelect() {
        val row = composeRule.onNodeWithText(CONTACT_NAME)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { row.isDisplayed() }
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("从 QAuxiliary 导入 QQ 好友").assertIsDisplayed()
        composeRule.onNodeWithText("多选").performClick()

        assertSelectedCount(0)
        row.assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
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
        composeRule.onNode(hasAnyAncestor(hasTestTag(SEARCH_PAGE)) and hasContentDescription("更多")).performClick()
        composeRule.onNodeWithText("多选").performClick()

        assertSelectedCount(0)
        row.assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
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
        const val QQ_ID = "123456789"
        const val QQ_LINK = "https://tool.gljlw.com/qq/?qq=$QQ_ID"
        const val WECHAT_LINK = "https://u.wechat.com/test-contact"
        const val CONTACT_NAME = "Cap000001"
        const val SEARCH_PAGE = "contact_search_page"
        const val DEBOUNCE_STEP_MS = 350L
        const val WAIT_TIMEOUT_MS = 5_000L
        val ROW = PersonContactRow(Contact(id = 1, name = CONTACT_NAME, pinyinInitial = "C"), "C", "", 0, "", 0)
    }
}
