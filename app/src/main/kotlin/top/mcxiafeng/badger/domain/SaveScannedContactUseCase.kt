package top.mcxiafeng.badger.domain

import kotlinx.coroutines.flow.first
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.pages.scanner.stripFieldKeySuffix
import javax.inject.Inject

/**
 * 保存扫描联系人 UseCase
 *
 * 编排流程：插入联系人 → 解析字段 ID → 保存字段值 → 添加到名片夹
 */
class SaveScannedContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val fieldRepository: FieldRepository,
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(
        contact: Contact,
        extractedInfo: ExtractedContactInfo,
        collectionId: Long,
        sourceType: String,
        rawData: String? = null,
        ocrText: String? = null,
        qrCodeContent: String? = null
    ) {
        val contactId = contactRepository.insertContact(contact)

        // 保存联系方式字段值（支持同平台多值）
        val fieldMap = mutableListOf<Pair<Long, String>>()
        val fieldValues = extractedInfo.toFieldValues()
        val enabledFields = fieldRepository.getAllEnabledFields().first()
        val fieldKeyToId = enabledFields.associate { it.fieldKey to it.id }
        for ((key, value) in fieldValues) {
            val baseKey = stripFieldKeySuffix(key)
            val fieldId = fieldKeyToId[baseKey] ?: continue
            fieldMap.add(fieldId to value)
        }

        if (fieldMap.isNotEmpty()) {
            fieldRepository.saveContactFieldValues(contactId, fieldMap)
        }

        collectionRepository.addContactToCollection(
            contactId = contactId,
            collectionId = collectionId,
            sourceType = sourceType,
            rawData = rawData,
            ocrText = ocrText,
            qrCodeContent = qrCodeContent
        )
    }
}
