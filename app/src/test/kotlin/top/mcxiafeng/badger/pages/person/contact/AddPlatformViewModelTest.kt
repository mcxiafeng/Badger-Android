package top.mcxiafeng.badger.pages.person.contact

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.repository.AddPlatformsResult
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.domain.ParsePlatformInputUseCase
import top.mcxiafeng.badger.testutil.MainDispatcherRule
import top.mcxiafeng.badger.utils.PinyinUtils

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AddPlatformViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()
    private val repository = mockk<ContactRepository>()
    private lateinit var handle: SavedStateHandle
    private lateinit var viewModel: AddPlatformViewModel

    @Before
    fun setUp() {
        mockkObject(PinyinUtils)
        every { PinyinUtils.getContactPinyinInitial(any()) } answers {
            when (firstArg<String>()) {
                "微信", "微博", "网站" -> "W"
                "抖音" -> "D"
                "小红书" -> "X"
                else -> firstArg<String>().first().uppercase()
            }
        }
        coEvery { repository.getContactById(CONTACT_ID) } returns Contact(id = CONTACT_ID, name = "联系人")
        coEvery { repository.getContactPlatformKeys(CONTACT_ID) } returns emptySet()
        coEvery { repository.addContactPlatforms(CONTACT_ID, any()) } returns AddPlatformsResult.Saved
        handle = SavedStateHandle()
        viewModel = AddPlatformViewModel(repository, ParsePlatformInputUseCase(), handle)
    }

    @After
    fun tearDown() { unmockkObject(PinyinUtils) }

    @Test
    fun existingAliasAndRepeatedRowsNeverOverwriteAnotherPlatform() = runTest {
        coEvery { repository.getContactPlatformKeys(CONTACT_ID) } returns setOf("QQ")
        load()
        viewModel.updateInput("QQ:123456\nGitHub:one\nGitHub:two")

        val state = viewModel.uiState.value
        assertThat(state.candidates.map { it.message != null }).containsExactly(true, false, true).inOrder()
        assertThat(state.additions.keys).containsExactly("github")
        viewModel.save()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.addContactPlatforms(any(), any()) }
    }

    @Test
    fun searchMatchesAliasesAndInitialsAndKeepsExistingRowsDisabled() = runTest {
        coEvery { repository.getContactPlatformKeys(CONTACT_ID) } returns setOf("微信")
        load()
        viewModel.openManual()
        viewModel.updateQuery("tg")
        assertThat(viewModel.uiState.value.platforms.map { it.definition.fieldKey }).containsExactly("telegram")
        viewModel.updateQuery("W")
        assertThat(viewModel.uiState.value.platforms.map { it.definition.fieldKey }).containsAtLeast("wechat", "weibo", "website")
        assertThat(viewModel.uiState.value.platforms.first { it.definition.fieldKey == "wechat" }.isAdded).isTrue()
    }

    @Test
    fun manualLinkOnlyAccountAndCustomPlatformJoinTheSameSaveBatch() = runTest {
        load()
        viewModel.openManual()
        viewModel.selectPlatform("douyin")
        viewModel.updateAuxiliary("my_douyin_id")
        viewModel.addManualEntry()
        viewModel.openManual()
        viewModel.selectPlatform("")
        viewModel.updateCustomName("Discord")
        viewModel.updateAccount("someone")
        viewModel.addManualEntry()
        viewModel.updateInput("QQ:123456")
        viewModel.save()
        advanceUntilIdle()

        coVerify { repository.addContactPlatforms(CONTACT_ID, match {
            it.keys == setOf("douyin", "discord", "qq") &&
                it["douyin"]?.value == "my_douyin_id" && it["douyin"]?.jumpLink == ""
        }) }
        assertThat(viewModel.uiState.value.isSaved).isTrue()
    }

    @Test
    fun savedStateRestoresBothAutomaticTextAndUnfinishedManualForm() = runTest {
        load()
        viewModel.updateInput("QQ:123456")
        viewModel.openManual()
        viewModel.selectPlatform("telegram")
        viewModel.updateAccount("pending_account")
        viewModel.updateDisplayName("待添加昵称")
        viewModel.addManualEntry()
        viewModel.openManual()
        viewModel.selectPlatform("github")
        viewModel.updateAccount("octocat")
        viewModel.updateDisplayName("昵称")
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val restored = AddPlatformViewModel(repository, ParsePlatformInputUseCase(), restoredHandle)
        restored.load(CONTACT_ID)
        advanceUntilIdle()

        assertThat(restored.uiState.value.step).isEqualTo(AddPlatformStep.FORM)
        assertThat(restored.uiState.value.input).isEqualTo("QQ:123456")
        assertThat(restored.uiState.value.account).isEqualTo("octocat")
        assertThat(restored.uiState.value.displayName).isEqualTo("昵称")
        assertThat(restored.uiState.value.additions.keys).containsExactly("qq", "telegram")
        assertThat(restored.uiState.value.pending["telegram"]?.value).isEqualTo("pending_account")
        assertThat(restored.uiState.value.pending["telegram"]?.displayName).isEqualTo("待添加昵称")
        assertThat(restored.uiState.value.pending["telegram"]?.jumpLink).isEqualTo("https://t.me/pending_account")
    }

    @Test
    fun saveFailurePreservesInputAndAllowsAnExplicitRetry() = runTest {
        load()
        coEvery { repository.addContactPlatforms(CONTACT_ID, any()) } throws IOException("disk unavailable")
        viewModel.updateInput("QQ:123456")
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.input).isEqualTo("QQ:123456")
        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(viewModel.uiState.value.error).contains("保存失败")

        coEvery { repository.addContactPlatforms(CONTACT_ID, any()) } returns AddPlatformsResult.Saved
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSaved).isTrue()
        coVerify(exactly = 2) { repository.addContactPlatforms(CONTACT_ID, any()) }
    }

    @Test
    fun secondSubmitAndBackAreBlockedUntilTheDatabaseWriteFinishes() = runTest {
        load()
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.addContactPlatforms(CONTACT_ID, any()) } coAnswers {
            gate.await()
            AddPlatformsResult.Saved
        }
        viewModel.updateInput("QQ:123456")
        viewModel.save()
        runCurrent()
        viewModel.save()
        assertThat(viewModel.back()).isTrue()
        assertThat(viewModel.uiState.value.isSaved).isFalse()
        gate.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.addContactPlatforms(CONTACT_ID, any()) }
        assertThat(viewModel.uiState.value.isSaved).isTrue()
    }

    @Test
    fun concurrentDuplicateKeepsTheDraftAndMarksTheConflictingLine() = runTest {
        load()
        coEvery { repository.addContactPlatforms(CONTACT_ID, any()) } returns AddPlatformsResult.Duplicate(setOf("qq"))
        viewModel.updateInput("QQ:123456\nGitHub:octocat")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.input).isEqualTo("QQ:123456\nGitHub:octocat")
        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(viewModel.uiState.value.candidates.first().message).isNotNull()
    }

    @Test
    fun oversizedAutomaticInputCannotBeBypassedBySavingManualEntries() = runTest {
        load()
        viewModel.openManual()
        viewModel.selectPlatform("qq")
        viewModel.updateAccount("123456")
        viewModel.addManualEntry()
        viewModel.updateInput("x".repeat(20_001))
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.input).hasLength(20_001)
        assertThat(viewModel.uiState.value.error).contains("最多输入")
        coVerify(exactly = 0) { repository.addContactPlatforms(any(), any()) }
    }

    @Test
    fun movingBetweenManualAndAutomaticKeepsAutomaticInput() = runTest {
        load()
        viewModel.updateInput("QQ:123456")
        viewModel.openManual()
        assertThat(viewModel.back()).isTrue()
        assertThat(viewModel.uiState.value.input).isEqualTo("QQ:123456")
        assertThat(viewModel.back()).isFalse()
    }

    private suspend fun TestScope.load() {
        viewModel.load(CONTACT_ID)
        advanceUntilIdle()
    }

    private companion object { const val CONTACT_ID = 42L }
}
