package top.mcxiafeng.badger.pages.scanner

import androidx.compose.runtime.Immutable
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.domain.DuplicateDetectionUseCase
import top.mcxiafeng.badger.domain.MergeContactUseCase
import top.mcxiafeng.badger.domain.ParseQrCodeUseCase
import top.mcxiafeng.badger.domain.SaveScannedContactUseCase
import top.mcxiafeng.badger.ocr.ExtractedContactInfo

/**
 * 扫描页面的 UI 状态
 */
@Immutable
data class ScannerUiState(
    val selectedMode: Int = 0,
    val isFlashOn: Boolean = false,
    val isProcessing: Boolean = false,
    val scanResult: String? = null,
    val ocrResult: String? = null,
    val qrCodeContent: String? = null,
    val extractedInfo: ExtractedContactInfo? = null,
    val duplicateCheckResult: DuplicateCheckResult? = null,
    val showResultDialog: Boolean = false,
    val showDuplicateDialog: Boolean = false
)

/**
 * 扫描页面的 ViewModel
 *
 * 管理扫码/拍照模式的切换、二维码识别、OCR 文字识别、
 * 联系人信息提取、重复检测以及保存/合并操作。
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    val contactRepository: ContactRepository,
    val fieldRepository: FieldRepository,
    val collectionRepository: CollectionRepository,
    private val parseQrCodeUseCase: ParseQrCodeUseCase,
    private val duplicateDetectionUseCase: DuplicateDetectionUseCase,
    private val saveScannedContactUseCase: SaveScannedContactUseCase,
    private val mergeContactUseCase: MergeContactUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onQrCodeDetected(content: String) {
        _uiState.value = _uiState.value.copy(
            qrCodeContent = content,
            scanResult = content,
            showResultDialog = true
        )

        viewModelScope.launch {
            checkForDuplicates(parseQrCodeUseCase(content))
        }
    }

    fun setImageResult(extractedInfo: ExtractedContactInfo) {
        _uiState.value = _uiState.value.copy(
            extractedInfo = extractedInfo,
            isProcessing = false,
            showResultDialog = true
        )
        viewModelScope.launch {
            checkForDuplicates(extractedInfo)
        }
    }

    private suspend fun checkForDuplicates(info: ExtractedContactInfo) {
        val result = duplicateDetectionUseCase(
            newContactName = info.name ?: "",
            fieldValues = info.toFieldValues()
        )

        _uiState.value = _uiState.value.copy(duplicateCheckResult = result)

        if (result.isDuplicate) {
            _uiState.value = _uiState.value.copy(showDuplicateDialog = true)
        }
    }

    fun saveContact(contact: Contact, extractedInfo: ExtractedContactInfo, collectionId: Long) {
        viewModelScope.launch {
            saveScannedContactUseCase(
                contact = contact,
                extractedInfo = extractedInfo,
                collectionId = collectionId,
                sourceType = if (_uiState.value.qrCodeContent != null) "scan" else "photo",
                rawData = extractedInfo.rawText,
                ocrText = _uiState.value.ocrResult,
                qrCodeContent = _uiState.value.qrCodeContent
            )
            resetState()
        }
    }

    fun mergeWithExisting(newContact: Contact, existingContact: Contact, extractedInfo: ExtractedContactInfo) {
        viewModelScope.launch {
            mergeContactUseCase(newContact, existingContact, extractedInfo)
            resetState()
        }
    }

    fun dismissResult() {
        _uiState.value = ScannerUiState(selectedMode = _uiState.value.selectedMode)
    }

    fun dismissDuplicateDialog() {
        _uiState.value = _uiState.value.copy(showDuplicateDialog = false)
    }

    private fun resetState() {
        _uiState.value = ScannerUiState(selectedMode = _uiState.value.selectedMode)
    }
}
