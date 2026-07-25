package top.mcxiafeng.badger.domain

import kotlinx.coroutines.flow.first
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.pages.scanner.stripFieldKeySuffix
import javax.inject.Inject

/**
 * 合并联系人信息 UseCase
 *
 * 将新扫描的信息合并到已有联系人：只补充缺失字段，不覆盖已有值。
 * 如果新联系人姓名非空则更新姓名。
 */
class MergeContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val fieldRepository: FieldRepository
) {
    suspend operator fun invoke(
        newContact: Contact,
        existingContact: Contact,
        extractedInfo: ExtractedContactInfo
    ) {
        // 重新从 DB 读取最新联系人数据
        val freshExisting = contactRepository.getContactById(existingContact.id) ?: existingContact
        val mergedContact = freshExisting.copy(
            name = if (!newContact.name.isNullOrBlank()) newContact.name else freshExisting.name,
            updateTime = System.currentTimeMillis()
        )

        contactRepository.updateContact(mergedContact)

        // 合并字段值：只补充缺失的信息
        val fieldMap = mutableListOf<Pair<Long, String>>()
        val fieldValues = extractedInfo.toFieldValues()
        val enabledFields = fieldRepository.getAllEnabledFields().first()
        val fieldKeyToId = enabledFields.associate { it.fieldKey to it.id }
        for ((key, value) in fieldValues) {
            val baseKey = stripFieldKeySuffix(key)
            val fieldId = fieldKeyToId[baseKey] ?: continue
            val existingValue = fieldRepository.getFieldValueByContactAndKey(existingContact.id, baseKey)
            if (existingValue == null || existingValue.isBlank()) {
                fieldMap.add(fieldId to value)
            }
        }

        if (fieldMap.isNotEmpty()) {
            fieldRepository.saveContactFieldValues(existingContact.id, fieldMap)
        }
    }
}
