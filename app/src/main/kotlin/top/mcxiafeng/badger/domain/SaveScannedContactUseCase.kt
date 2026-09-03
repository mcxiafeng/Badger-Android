package top.mcxiafeng.badger.domain

import kotlinx.coroutines.flow.first
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.pages.scanner.stripFieldKeySuffix

/**
 * 保存扫描联系人：插入联系人 → 保存字段值 → 加入名片夹。
 * 注意：Repository 内部 withContext(IO) 会跳出 Room 事务边界，
 * 真正的原子写入需要 DAO 层事务 API，当前为顺序写入。
 */
class SaveScannedContactUseCase(
    private val contactRepository: ContactRepository,
    private val fieldRepository: FieldRepository,
    private val collectionRepository: CollectionRepository,
) {
    suspend operator fun invoke(
        contact: Contact,
        extractedInfo: ExtractedContactInfo,
        collectionId: Long,
        sourceType: String,
    ) {
        val contactId = contactRepository.insertContact(contact)

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
        )
    }
}
