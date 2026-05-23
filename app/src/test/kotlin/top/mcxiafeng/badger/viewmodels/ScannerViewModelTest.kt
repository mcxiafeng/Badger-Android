package top.mcxiafeng.badger.viewmodels

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.testutil.MainDispatcherRule
import top.mcxiafeng.badger.testutil.TestDataProvider

class ScannerViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: ContactRepository
    private lateinit var viewModel: ScannerViewModel

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        coEvery { repository.checkDuplicate(any(), any(), any()) } returns DuplicateCheckResult(
            isDuplicate = false, existingContact = null, similarityScore = 0f, matchFields = emptyList()
        )
        coEvery { repository.getAllEnabledFields() } returns flowOf(
            listOf(
                TestDataProvider.testContactField(id = 1, fieldKey = "phone"),
                TestDataProvider.testContactField(id = 2, fieldKey = "email")
            )
        )
    }

    @Test
    fun onQrCodeDetected_vCard_parsesNamePhoneEmail() = runTest {
        viewModel = ScannerViewModel(repository)
        val vcard = "BEGIN:VCARD\nFN:张三\nTEL:13800138000\nEMAIL:zhangsan@test.com\nEND:VCARD"
        viewModel.onQrCodeDetected(vcard)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.qrCodeContent).isEqualTo(vcard)
        assertThat(state.showResultDialog).isTrue()
        assertThat(state.scanResult).isEqualTo(vcard)
    }

    @Test
    fun onQrCodeDetected_emailFormat_parsesEmail() = runTest {
        viewModel = ScannerViewModel(repository)
        viewModel.onQrCodeDetected("test@example.com")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.showResultDialog).isTrue()
    }

    @Test
    fun onQrCodeDetected_phoneFormat_parsesPhone() = runTest {
        viewModel = ScannerViewModel(repository)
        viewModel.onQrCodeDetected("13800138000")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.showResultDialog).isTrue()
    }

    @Test
    fun onQrCodeDetected_plainText_parsesAsName() = runTest {
        viewModel = ScannerViewModel(repository)
        viewModel.onQrCodeDetected("HelloWorld")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showResultDialog).isTrue()
    }

    @Test
    fun onQrCodeDetected_setsShowResultDialogTrue() = runTest {
        viewModel = ScannerViewModel(repository)
        viewModel.onQrCodeDetected("some content")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showResultDialog).isTrue()
    }

    @Test
    fun checkForDuplicates_duplicateFound_setsShowDuplicateDialog() = runTest {
        coEvery { repository.checkDuplicate(any(), any(), any()) } returns DuplicateCheckResult(
            isDuplicate = true,
            existingContact = TestDataProvider.testContact(name = "张三"),
            similarityScore = 1.0f,
            matchFields = listOf("phone")
        )
        viewModel = ScannerViewModel(repository)
        viewModel.onQrCodeDetected("13800138000")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDuplicateDialog).isTrue()
        assertThat(viewModel.uiState.value.duplicateCheckResult?.isDuplicate).isTrue()
    }

    @Test
    fun checkForDuplicates_noDuplicate_doesNotShowDialog() = runTest {
        viewModel = ScannerViewModel(repository)
        viewModel.onQrCodeDetected("13800138000")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDuplicateDialog).isFalse()
    }

    @Test
    fun saveContact_insertsContactAndFieldValues() = runTest {
        viewModel = ScannerViewModel(repository)
        val contact = TestDataProvider.testContact(name = "张三")
        val extractedInfo = ExtractedContactInfo(name = "张三", phone = "13800138000")
        coEvery { repository.insertContact(any()) } returns 1L

        viewModel.saveContact(contact, extractedInfo, 1L)
        advanceUntilIdle()

        coVerify { repository.insertContact(match { it.name == "张三" }) }
        coVerify { repository.saveContactFieldValues(1L, any<List<Pair<Long, String>>>()) }
        coVerify { repository.addContactToCollection(1L, 1L, any(), any(), any(), any(), any()) }
    }

    @Test
    fun saveContact_scanMode_sourceTypeIsScan() = runTest {
        viewModel = ScannerViewModel(repository)
        viewModel.onQrCodeDetected("13800138000")
        advanceUntilIdle()

        val contact = TestDataProvider.testContact(name = "张三")
        val extractedInfo = ExtractedContactInfo(phone = "13800138000")
        coEvery { repository.insertContact(any()) } returns 1L

        viewModel.saveContact(contact, extractedInfo, 1L)
        advanceUntilIdle()

        coVerify { repository.addContactToCollection(any(), any(), "scan", any(), any(), any(), any()) }
    }

    @Test
    fun mergeWithExisting_updatesContactName() = runTest {
        viewModel = ScannerViewModel(repository)
        val newContact = TestDataProvider.testContact(name = "新名字")
        val existingContact = TestDataProvider.testContact(id = 5, name = "旧名字")
        val extractedInfo = ExtractedContactInfo(name = "新名字")
        coEvery { repository.getFieldValueByContactAndKey(any(), any()) } returns null

        viewModel.mergeWithExisting(newContact, existingContact, extractedInfo)
        advanceUntilIdle()

        coVerify { repository.updateContact(match { it.name == "新名字" && it.id == 5L }) }
    }

    @Test
    fun mergeWithExisting_onlyFillsMissingFields() = runTest {
        viewModel = ScannerViewModel(repository)
        val newContact = TestDataProvider.testContact(name = "张三")
        val existingContact = TestDataProvider.testContact(id = 5, name = "张三")
        val extractedInfo = ExtractedContactInfo(phone = "13800138000", email = "test@test.com")
        coEvery { repository.getFieldValueByContactAndKey(5L, "phone") } returns "13900139000"
        coEvery { repository.getFieldValueByContactAndKey(5L, "email") } returns null

        viewModel.mergeWithExisting(newContact, existingContact, extractedInfo)
        advanceUntilIdle()

        coVerify { repository.saveContactFieldValues(5L, match<List<Pair<Long, String>>> { list -> !list.any { it.second == "13800138000" } && list.any { it.second == "test@test.com" } }) }
    }

    @Test
    fun dismissResult_resetsState() = runTest {
        viewModel = ScannerViewModel(repository)
        viewModel.onQrCodeDetected("test")
        advanceUntilIdle()

        viewModel.dismissResult()

        val state = viewModel.uiState.value
        assertThat(state.scanResult).isNull()
        assertThat(state.qrCodeContent).isNull()
        assertThat(state.showResultDialog).isFalse()
    }

    @Test
    fun dismissDuplicateDialog_hidesDialog() = runTest {
        coEvery { repository.checkDuplicate(any(), any(), any()) } returns DuplicateCheckResult(
            isDuplicate = true,
            existingContact = TestDataProvider.testContact(name = "张三"),
            similarityScore = 1.0f,
            matchFields = listOf("phone")
        )
        viewModel = ScannerViewModel(repository)
        viewModel.onQrCodeDetected("13800138000")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDuplicateDialog).isTrue()
        viewModel.dismissDuplicateDialog()
        assertThat(viewModel.uiState.value.showDuplicateDialog).isFalse()
    }
}