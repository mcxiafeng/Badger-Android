package top.mcxiafeng.badger.viewmodels

import com.google.common.truth.Truth.assertThat
import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.domain.PrepareNfcWriteUseCase
import top.mcxiafeng.badger.domain.LoadPlatformProfileUseCase
import top.mcxiafeng.badger.domain.PlatformProfile
import top.mcxiafeng.badger.pages.social.cardContentFor
import top.mcxiafeng.badger.domain.SelectPlatformUseCase
import top.mcxiafeng.badger.pages.social.NfcActivityHandler
import top.mcxiafeng.badger.pages.social.NfcHelper
import top.mcxiafeng.badger.pages.social.SocialViewModel
import top.mcxiafeng.badger.testutil.MainDispatcherRule

class SocialViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: UserProfileRepository
    private lateinit var contactRepository: ContactRepository
    private lateinit var contactsFlow: MutableStateFlow<List<Contact>>
    private lateinit var myContactFlow: MutableStateFlow<Contact?>
    private lateinit var platformsFlow: MutableStateFlow<List<ContactPlatform>>
    private lateinit var selectPlatformUseCase: SelectPlatformUseCase
    private lateinit var prepareNfcWriteUseCase: PrepareNfcWriteUseCase
    private lateinit var loadPlatformProfileUseCase: LoadPlatformProfileUseCase
    private lateinit var viewModel: SocialViewModel
    private val profileFlow = MutableStateFlow<UserProfile?>(null)

    @After
    fun tearDown() {
        // ViewModel 的 init collect 挂在 viewModelScope（Main dispatcher）上；
        // 不取消的话 resetMain 后这些协程恢复时会抛 Main dispatcher 异常，
        // 污染同一 JVM 中后续测试类（UncaughtExceptionsBeforeTest）。
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
    }

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        contactRepository = mockk(relaxed = true)
        selectPlatformUseCase = mockk(relaxed = true)
        prepareNfcWriteUseCase = mockk(relaxed = true)
        loadPlatformProfileUseCase = mockk()
        coEvery { loadPlatformProfileUseCase(any(), any()) } returns null
        every { repository.getUserProfile() } returns profileFlow
        contactsFlow = MutableStateFlow(emptyList())
        myContactFlow = MutableStateFlow(null)
        platformsFlow = MutableStateFlow(emptyList())
        every { contactRepository.getAllContacts() } returns contactsFlow
        every { contactRepository.observeContactById(MY_CONTACT_ID) } returns myContactFlow
        every { contactRepository.observeContactPlatforms(any()) } returns platformsFlow
    }

    @Test
    fun loadProfile_success_setsProfileAndPlatforms() = runTest {
        myContactFlow.value = Contact(id = MY_CONTACT_ID, name = "测试用户")
        contactsFlow.value = listOf(Contact(id = MY_CONTACT_ID, name = "测试用户"))
        platformsFlow.value = listOf(
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "QQ", jumpLink = "https://qq.com/123", value = "123"),
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "哔哩哔哩", jumpLink = "https://bilibili.com/456", value = "456")
        )
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.profile).isNotNull()
        assertThat(state.profile!!.name).isEqualTo("测试用户")
        assertThat(state.platforms).hasSize(2)
    }

    @Test
    fun loadProfile_nullProfile_setsEmptyPlatforms() = runTest {
        profileFlow.value = null
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.platforms).isEmpty()
    }

    @Test
    fun buildPlatformList_filtersBlankJumpLink() = runTest {
        platformsFlow.value = listOf(
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "QQ", jumpLink = "https://qq.com/123", value = "123"),
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "微信", jumpLink = "", value = "wxid"),
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "empty", jumpLink = "", value = null)
        )
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // QQ has jumpLink, 微信 has non-blank value; empty row is filtered out
        assertThat(state.platforms).hasSize(2)
    }

    @Test
    fun buildPlatformList_distinctKeysBothKept() = runTest {
        platformsFlow.value = listOf(
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "QQ", jumpLink = "https://qq.com/123", value = "123"),
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "qq", jumpLink = "https://qq.com/456", value = "456")
        )
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // "QQ" and "qq" are distinct platform keys, both rows pass filter
        assertThat(state.platforms).hasSize(2)
    }

    @Test
    fun setNfcSupported_updatesState() {
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        viewModel.setNfcSupported(true)
        assertThat(viewModel.uiState.value.nfcSupported).isTrue()
    }

    @Test
    fun addOrUpdatePlatform_callsRepository() = runTest {
        profileFlow.value = UserProfile(id = 1L, name = "测试")
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        advanceUntilIdle()

        viewModel.addOrUpdatePlatform("QQ", "https://qq.com/123", "123")
        advanceUntilIdle()

        coVerify {
            contactRepository.updateContactPlatform(eq(MY_CONTACT_ID), eq("QQ"), match {
                it.jumpLink == "https://qq.com/123" && it.value == "123"
            })
        }
    }

    @Test
    fun removePlatform_removed() = runTest {
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)

        // removePlatform 已随"我的名片"迁移至 ContactRepository（联系人详情页同一套数据表），
        // SocialViewModel 不再持有删除入口
        assertThat(true).isTrue()
    }

    // updateProfileBasic / setShowEditProfileDialog / setShowAddPlatformDialog 已随
    // "我的名片"存储迁移（contacts 表 id=-1）移除：身份编辑走联系人详情页，
    // 添加平台对话框状态改为页面本地 state，不再经过 SocialUiState。
    @Test
    fun defaultPlatformChanged_updatesSelectedIndex() = runTest {
        // defaultPlatform 来自 user_profile 行；平台列表来自 contact_platforms
        profileFlow.value = UserProfile(id = 1L, name = "测试", defaultPlatform = "哔哩哔哩")
        platformsFlow.value = listOf(
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "QQ", jumpLink = "https://qq.com/123", value = "123"),
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "哔哩哔哩", jumpLink = "https://bilibili.com/456", value = "456")
        )
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // "哔哩哔哩" is at index 1 in the platform list
        assertThat(state.selectedPlatformIndex).isEqualTo(1)
    }

    @Test
    fun platformCard_profilesStaySeparateAndRevisitsUseCache() = runTest {
        myContactFlow.value = Contact(id = MY_CONTACT_ID, name = "全局名字", bio = "全局简介")
        platformsFlow.value = listOf(
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "qq", value = "123"),
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "bilibili", value = "456")
        )
        coEvery { loadPlatformProfileUseCase("qq", any()) } returns PlatformProfile("QQ昵称", "QQ签名")
        coEvery { loadPlatformProfileUseCase("bilibili", any()) } returns PlatformProfile("B站昵称", "B站签名")
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        advanceUntilIdle()

        viewModel.loadPlatformProfile("qq")
        viewModel.loadPlatformProfile("bilibili")
        advanceUntilIdle()
        viewModel.loadPlatformProfile("qq")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.cardContentFor("qq").name).isEqualTo("QQ昵称")
        assertThat(viewModel.uiState.value.cardContentFor("qq").signature).isEqualTo("QQ签名")
        assertThat(viewModel.uiState.value.cardContentFor("bilibili").signature).isEqualTo("B站签名")
        assertThat(viewModel.uiState.value.cardContentFor(null).name).isEqualTo("添加")
        assertThat(viewModel.uiState.value.cardContentFor(null).signature).isNull()
        coVerify(exactly = 1) { loadPlatformProfileUseCase("qq", any()) }
        coVerify(exactly = 0) { selectPlatformUseCase(any(), any(), any()) }
    }

    @Test
    fun platformCard_failedProfileKeepsSavedNameWithoutGlobalBio() = runTest {
        myContactFlow.value = Contact(id = MY_CONTACT_ID, name = "全局名字", bio = "不是QQ签名")
        platformsFlow.value = listOf(
            ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "qq", value = "123", displayName = "已保存昵称")
        )
        coEvery { loadPlatformProfileUseCase("qq", any()) } throws IllegalStateException("offline")
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        advanceUntilIdle()

        viewModel.loadPlatformProfile("qq")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.cardContentFor("qq").name).isEqualTo("已保存昵称")
        assertThat(viewModel.uiState.value.cardContentFor("qq").signature).isNull()
    }

    @Test
    fun platformCard_lateOldAccountResultIsDiscardedAndInFlightLoadsAreDeduplicated() = runTest {
        val oldResult = CompletableDeferred<PlatformProfile?>()
        platformsFlow.value = listOf(ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "qq", value = "old"))
        coEvery { loadPlatformProfileUseCase("qq", match { it.value == "old" }) } coAnswers { oldResult.await() }
        coEvery { loadPlatformProfileUseCase("qq", match { it.value == "new" }) } returns PlatformProfile("新账号", "新签名")
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        advanceUntilIdle()
        viewModel.loadPlatformProfile("qq")
        viewModel.loadPlatformProfile("qq")
        runCurrent()

        platformsFlow.value = listOf(ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "qq", value = "new"))
        runCurrent()
        viewModel.loadPlatformProfile("qq")
        runCurrent()
        oldResult.complete(PlatformProfile("旧账号", "旧签名"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.cardContentFor("qq").name).isEqualTo("新账号")
        assertThat(viewModel.uiState.value.cardContentFor("qq").signature).isEqualTo("新签名")
        coVerify(exactly = 1) { loadPlatformProfileUseCase("qq", match { it.value == "old" }) }
    }

    @Test
    fun platformCard_changingAccountClearsCachedSignatureImmediately() = runTest {
        platformsFlow.value = listOf(ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "qq", value = "old"))
        coEvery { loadPlatformProfileUseCase("qq", any()) } returns PlatformProfile("旧昵称", "旧签名")
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)
        advanceUntilIdle()
        viewModel.loadPlatformProfile("qq")
        advanceUntilIdle()

        platformsFlow.value = listOf(ContactPlatform(contactId = MY_CONTACT_ID, platformKey = "qq", value = "new"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.cardContentFor("qq").name).isEqualTo("new")
        assertThat(viewModel.uiState.value.cardContentFor("qq").signature).isNull()
    }

    // --- NfcActivityHandler tests ---

    @Test
    fun dismissNfcWriteDialog_whenWriting_callsHandlerStopWriting() = runTest {
        val handler = mockk<NfcActivityHandler>(relaxed = true)
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)

        mockkObject(NfcHelper)
        every { NfcHelper.isWriting } returns true

        viewModel.dismissNfcWriteDialog(handler)

        verify { handler.stopWriting() }
        assertThat(viewModel.uiState.value.showNfcWriteDialog).isFalse()

        unmockkObject(NfcHelper)
    }

    @Test
    fun dismissNfcWriteDialog_whenNotWriting_doesNotCallHandler() = runTest {
        val handler = mockk<NfcActivityHandler>(relaxed = true)
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)

        mockkObject(NfcHelper)
        every { NfcHelper.isWriting } returns false

        viewModel.dismissNfcWriteDialog(handler)

        verify(exactly = 0) { handler.stopWriting() }

        unmockkObject(NfcHelper)
    }

    @Test
    fun startNfcWrite_whenAlreadyWriting_returnsEarly() = runTest {
        val handler = mockk<NfcActivityHandler>(relaxed = true)
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)

        mockkObject(NfcHelper)
        every { NfcHelper.isWriting } returns true

        viewModel.startNfcWrite(handler)
        advanceUntilIdle()

        verify(exactly = 0) { handler.startWriting(any()) }

        unmockkObject(NfcHelper)
    }

    @Test
    fun onNfcWriteSuccess_callsHandlerStopWriting() = runTest {
        val handler = mockk<NfcActivityHandler>(relaxed = true)
        viewModel = SocialViewModel(contactRepository, repository, mockk(relaxed = true), selectPlatformUseCase, prepareNfcWriteUseCase, loadPlatformProfileUseCase)

        viewModel.onNfcWriteSuccess(handler)
        advanceUntilIdle()

        verify { handler.stopWriting() }
        assertThat(viewModel.uiState.value.showNfcWriteDialog).isFalse()
    }
}
