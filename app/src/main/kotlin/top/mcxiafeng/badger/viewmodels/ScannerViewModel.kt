package top.mcxiafeng.badger.viewmodels

import androidx.compose.runtime.Immutable
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.pages.scanner.stripFieldKeySuffix

/**
 * 扫描页面的 UI 状态
 *
 * @property selectedMode 当前扫描模式：0=拍照，1=扫码
 * @property isFlashOn 闪光灯是否开启
 * @property isProcessing 是否正在处理图片（OCR识别中）
 * @property scanResult 扫描结果原始文本
 * @property ocrResult OCR 文字识别结果
 * @property qrCodeContent 二维码内容
 * @property extractedInfo 从扫描结果中提取出的结构化联系人信息
 * @property duplicateCheckResult 重复检测的结果
 * @property showResultDialog 是否显示扫描结果对话框
 * @property showDuplicateDialog 是否显示重复联系人提示对话框
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
 *
 * @property repository 联系人数据仓库
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    val repository: ContactRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()
    
    /**
     * 处理二维码识别结果
     *
     * 接收到二维码内容后，尝试解析其中的联系人信息，
     * 并自动进行重复检测。
     *
     * @param content 二维码原始内容
     */
    fun onQrCodeDetected(content: String) {
        _uiState.value = _uiState.value.copy(
            qrCodeContent = content,
            scanResult = content,
            showResultDialog = true
        )
        
        viewModelScope.launch {
            checkForDuplicates(extractContactFromQrCode(content))
        }
    }
    
    /**
     * 处理拍照后的图片
     *
     * 执行 OCR 文字识别并提取联系人信息，识别完成后显示结果对话框。
     *
     * @param bitmap 拍摄的图片
     * @param extractedInfo 从图片中提取的联系人信息（由外部 AI 服务提供）
     */
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
    
    /**
     * 从二维码内容中提取联系人信息
     *
     * 支持 vCard 格式、邮箱、手机号、普通文本的自动识别：
     * - vCard (BEGIN:VCARD)：解析 FN/TEL/EMAIL 字段
     * - 邮箱格式：识别为 email
     * - 11位手机号：识别为 phone
     * - 其他：作为 name 处理
     */
    private fun extractContactFromQrCode(qrContent: String): ExtractedContactInfo {
        var name: String? = null
        var phone: String? = null
        var email: String? = null
        
        if (qrContent.contains("BEGIN:VCARD")) {
            // vCard 格式解析
            qrContent.lines().forEach { line ->
                when {
                    line.startsWith("FN:") -> name = line.removePrefix("FN:")
                    line.startsWith("TEL:") -> phone = line.removePrefix("TEL:")
                    line.startsWith("EMAIL:") -> email = line.removePrefix("EMAIL:")
                    else -> {}
                }
            }
        } else if (Regex("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$").matches(qrContent)) {
            email = qrContent
        } else if (Regex("^1[3-9]\\d{9}$").matches(qrContent)) {
            phone = qrContent
        } else {
            name = qrContent
        }
        
        return ExtractedContactInfo(
            name = name,
            phone = phone,
            email = email,
            rawText = qrContent
        )
    }
    
    /**
     * 重复检测
     *
     * 将提取到的联系人字段（手机、邮箱、微信、QQ、B站）提交给仓库层进行比对，
     * 如果发现重复联系人则自动弹出提示对话框。
     */
    private suspend fun checkForDuplicates(info: ExtractedContactInfo) {
        val result = repository.checkDuplicate(
            newContactName = info.name ?: "未知联系人",
            fieldValues = info.toFieldValues(),
            customFieldValues = emptyMap()
        )
        
        _uiState.value = _uiState.value.copy(duplicateCheckResult = result)
        
        if (result.isDuplicate) {
            _uiState.value = _uiState.value.copy(showDuplicateDialog = true)
        }
    }
    
    /**
     * 保存扫描到的联系人
     *
     * 1. 插入联系人记录
     * 2. 根据 extractedInfo 中的字段值，匹配系统预置字段并保存
     * 3. 将联系人添加到指定名片夹并记录扫描来源
     *
     * @param contact 联系人基本信息
     * @param extractedInfo 提取到的结构化联系人信息
     * @param collectionId 目标名片夹ID
     */
    fun saveContact(contact: Contact, extractedInfo: ExtractedContactInfo, collectionId: Long) {
        viewModelScope.launch {
            val contactId = repository.insertContact(contact)
            
            // 保存联系方式字段值（支持同平台多值，如多个QQ号）
            val fieldMap = mutableListOf<Pair<Long, String>>()
            val fieldValues = extractedInfo.toFieldValues()
            val enabledFields = repository.getAllEnabledFields().first()
            val fieldKeyToId = enabledFields.associate { it.fieldKey to it.id }
            for ((key, value) in fieldValues) {
                val baseKey = stripFieldKeySuffix(key)
                val fieldId = fieldKeyToId[baseKey] ?: continue
                fieldMap.add(fieldId to value)
            }

            if (fieldMap.isNotEmpty()) {
                repository.saveContactFieldValues(contactId, fieldMap)
            }
            
            repository.addContactToCollection(
                contactId = contactId,
                collectionId = collectionId,
                sourceType = if (_uiState.value.qrCodeContent != null) "scan" else "photo",
                rawData = extractedInfo.rawText,
                ocrText = _uiState.value.ocrResult,
                qrCodeContent = _uiState.value.qrCodeContent
            )
            
            resetState()
        }
    }
    
    /**
     * 将新扫描的信息合并到已有联系人
     *
     * 策略：只补充已有联系人缺失的字段值（不会覆盖已有值）。
     * 如果新联系人姓名非空则更新姓名。
     *
     * @param newContact 新扫描到的联系人信息
     * @param existingContact 已存在的重复联系人
     * @param extractedInfo 提取到的结构化联系人信息
     */
    fun mergeWithExisting(newContact: Contact, existingContact: Contact, extractedInfo: ExtractedContactInfo) {
        viewModelScope.launch {
            // 更新基本信息（优先使用新姓名）
            val mergedContact = existingContact.copy(
                name = if (!newContact.name.isNullOrBlank()) newContact.name else existingContact.name,
                updateTime = System.currentTimeMillis()
            )
            
            repository.updateContact(mergedContact)
            
            // 合并字段值：只补充缺失的信息（支持同平台多值）
            val fieldMap = mutableListOf<Pair<Long, String>>()
            val fieldValues = extractedInfo.toFieldValues()
            val enabledFields = repository.getAllEnabledFields().first()
            val fieldKeyToId = enabledFields.associate { it.fieldKey to it.id }
            for ((key, value) in fieldValues) {
                val baseKey = stripFieldKeySuffix(key)
                val fieldId = fieldKeyToId[baseKey] ?: continue
                val existingValue = repository.getFieldValueByContactAndKey(existingContact.id, baseKey)
                if (existingValue == null || existingValue.isBlank()) {
                    fieldMap.add(fieldId to value)
                }
            }

            if (fieldMap.isNotEmpty()) {
                repository.saveContactFieldValues(existingContact.id, fieldMap)
            }
            
            resetState()
        }
    }
    
    /** 关闭结果对话框并重置所有扫描状态（保留当前模式） */
    fun dismissResult() {
        _uiState.value = ScannerUiState(selectedMode = _uiState.value.selectedMode)
    }
    
    /** 关闭重复联系人提示对话框 */
    fun dismissDuplicateDialog() {
        _uiState.value = _uiState.value.copy(showDuplicateDialog = false)
    }
    
    /** 重置所有扫描状态（保留当前模式选择） */
    private fun resetState() {
        _uiState.value = ScannerUiState(selectedMode = _uiState.value.selectedMode)
    }
}
