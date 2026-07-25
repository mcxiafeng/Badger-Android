package top.mcxiafeng.badger.pages.scanner

import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.BadgerApplication
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.domain.DuplicateDetectionUseCase
import top.mcxiafeng.badger.domain.MergeContactUseCase
import top.mcxiafeng.badger.domain.ParseQrCodeUseCase
import top.mcxiafeng.badger.domain.SaveScannedContactUseCase
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import org.opencv.OpenCV
import com.king.wechat.qrcode.WeChatQRCodeDetector

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
    /** 暴露给 ScannerPage 用于「本次扫描标记 Tag」配置面板 */
    val tagRepository: TagRepository,
    /** 暴露给 ScannerPage 用于全新联系人后台 AI 贴标签 */
    val aiTagGenerator: AiTagGenerator,
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
            ensureOpenCvInitialized()
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
            ensureOpenCvInitialized()
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

    companion object {
        private val initMutex = Mutex()
        @Volatile
        private var openCvInitialized = false

        /**
         * 暴露给单测用的探针：判断 OpenCV/WeChatQRCode 是否已初始化。
         * 生产代码请勿依赖此方法。
         */
        internal fun isOpenCvInitialized(): Boolean = openCvInitialized
    }

    private suspend fun ensureOpenCvInitialized() {
        if (openCvInitialized) return
        initMutex.withLock {
            if (openCvInitialized) return@withLock
            // OpenCV 已由 BadgerApplication.onCreate() 同步 init（轻量、幂等）；
            // 此处仅做幂等保护 + WeChatQRCodeDetector 懒加载（首次扫码才需要模型文件）。
            try {
                OpenCV.initOpenCV()
                WeChatQRCodeDetector.init(BadgerApplication.getInstance())
                Log.d("Tester", "WeChatQRCode 懒加载完成（OpenCV 由 Application 兜底）")
            } catch (e: IllegalStateException) {
                Log.w("Tester", "WeChatQRCode 懒加载跳过（Application 未就绪，可能是测试环境）", e)
            } catch (e: UnsatisfiedLinkError) {
                // Robolectric 等没有 native lib 的环境下 System.loadLibrary 会抛此异常
                Log.w("Tester", "WeChatQRCode 懒加载跳过（native 库未加载，可能是测试环境）", e)
            }
            openCvInitialized = true
        }
    }
}
