package top.mcxiafeng.badger.pages.scanner

import android.util.Log
import kotlinx.coroutines.flow.first
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.FieldMergeEntry
import top.mcxiafeng.badger.data.MergeChoice
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.ensureCollectionId
import top.mcxiafeng.badger.network.NetworkResolveResult
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildPlatformLink

/**
 * 保存扫描到的联系人
 *
 * @return 新建联系人的 row id,给调用方用于后置写入(打标记 Tag / 异步 AI 贴标签等)
 */
internal suspend fun saveScannedContact(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    contact: Contact,
    info: ExtractedContactInfo,
    sourceType: String,
    collectionId: Long? = null
): Long {
    val effectiveCollectionId = ensureCollectionId(collectionRepository, collectionId)
    val platformEntries = buildPlatformEntries(info)
    val contactId = contactRepository.insertContact(contact)
    for ((key, entry) in platformEntries) {
        contactRepository.updateContactPlatform(contactId, key, entry)
    }
    val fieldMap = buildFieldMap(fieldRepository, info)
    if (fieldMap.isNotEmpty()) {
        fieldRepository.saveContactFieldValues(contactId, fieldMap)
    }
    collectionRepository.addContactToCollection(
        contactId = contactId,
        collectionId = effectiveCollectionId,
        sourceType = sourceType,
    )
    return contactId
}

/** 从 fieldKey 中剥离去重后缀（如 "qq_1" → "qq"）。 */
internal fun stripFieldKeySuffix(key: String): String {
    val idx = key.lastIndexOf('_')
    if (idx <= 0) return key
    val suffix = key.substring(idx + 1)
    return if (suffix.all { it.isDigit() }) key.substring(0, idx) else key
}

/** 将扫描结果中的社交平台字段转换为平台缓存条目。 */
internal fun buildPlatformEntries(info: ExtractedContactInfo): Map<String, PlatformEntry> {
    val result = mutableMapOf<String, PlatformEntry>()
    for ((key, value) in info.platforms) {
        val baseKey = stripFieldKeySuffix(key)
        if (baseKey !in PLATFORM_FIELD_KEYS || value.isBlank() || baseKey in result) continue
        result[baseKey] = PlatformEntry(
            jumpLink = buildPlatformLink(baseKey, value),
            value = value
        )
    }
    return result
}

/** 将平台数据合并到已有 Contact.platforms 中，不覆盖已有平台。 */
internal fun mergePlatformEntries(
    existing: Map<String, PlatformEntry>?,
    newEntries: Map<String, PlatformEntry>
): Map<String, PlatformEntry> {
    val merged = existing?.toMutableMap() ?: mutableMapOf()
    for ((key, entry) in newEntries) {
        if (key !in merged) merged[key] = entry
    }
    return merged
}

/** 将 ExtractedContactInfo 中的系统字段映射为 fieldId → value 列表。 */
internal suspend fun buildFieldMap(
    fieldRepository: FieldRepository,
    info: ExtractedContactInfo,
    filterKeys: Set<String>? = null
): List<Pair<Long, String>> {
    val result = mutableListOf<Pair<Long, String>>()
    val fields = fieldRepository.getAllEnabledFields().first()
    val fieldKeyToId = fields.associate { it.fieldKey to it.id }

    for ((key, value) in info.toFieldValues()) {
        val baseKey = stripFieldKeySuffix(key)
        if (baseKey in PLATFORM_FIELD_KEYS) continue
        if (filterKeys != null && baseKey !in filterKeys && key !in filterKeys) continue
        val fieldId = fieldKeyToId[baseKey] ?: continue
        result.add(fieldId to value)
    }
    return result
}

/**
 * 构建字段合并对比列表。
 *
 * fieldKey 可能带有多值去重后缀（例如 phone_1），但数据库字段定义使用基础 key。
 * 合并条目保留原始 key 以便 UI 精确对应用户选择，同时使用基础 key 查找字段名称和值。
 */
internal suspend fun buildMergeEntries(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    existingContactId: Long,
    newInfo: ExtractedContactInfo
): List<FieldMergeEntry> {
    val existingMap = fieldRepository.getFieldValueMapByContact(existingContactId)
    val newMap = newInfo.toFieldValues()
    val enabledFields = fieldRepository.getAllEnabledFields().first()
    val fieldNameMap = enabledFields.associate { it.fieldKey to it.fieldName }

    return newMap.mapNotNull { (key, newValue) ->
        val baseKey = stripFieldKeySuffix(key)
        val existingValue = existingMap[key] ?: existingMap[baseKey]
        if (existingValue != null && existingValue == newValue) return@mapNotNull null

        FieldMergeEntry(
            fieldKey = key,
            fieldName = fieldNameMap[baseKey] ?: fieldNameMap[key] ?: key,
            existingValue = existingValue,
            newValue = newValue,
            selectedValue = MergeChoice.APPEND
        )
    }
}

/**
 * 按用户选择合并字段到已有联系人。
 */
internal suspend fun mergeFieldsToContact(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    existingContact: Contact,
    newInfo: ExtractedContactInfo,
    mergeEntries: List<FieldMergeEntry>,
    collectionId: Long,
    sourceType: String,
    chosenName: String? = null,
    duplicateFieldKeys: Set<String> = emptySet()
) {
    val enabledFields = fieldRepository.getAllEnabledFields().first()
    val fieldIdMap = enabledFields.associate { it.fieldKey to it.id }

    for (entry in mergeEntries) {
        if (entry.selectedValue == MergeChoice.KEEP) continue
        val fieldId = fieldIdMap[stripFieldKeySuffix(entry.fieldKey)] ?: continue
        val newValue = entry.newValue ?: continue

        when (entry.selectedValue) {
            MergeChoice.REPLACE -> {
                val allValues = fieldRepository.getFieldValuesByContactOnce(existingContact.id)
                val target = allValues.find { it.fieldId == fieldId }
                if (target != null) {
                    fieldRepository.updateFieldValue(
                        target.copy(value = newValue, updateTime = System.currentTimeMillis())
                    )
                } else {
                    fieldRepository.saveContactFieldValues(existingContact.id, mapOf(fieldId to newValue))
                }
            }
            MergeChoice.APPEND -> {
                fieldRepository.saveContactFieldValues(existingContact.id, mapOf(fieldId to newValue))
            }
            MergeChoice.KEEP -> Unit
        }
    }

    val freshContact = contactRepository.getContactById(existingContact.id) ?: existingContact
    val newPlatformEntries = buildPlatformEntries(newInfo)
    val existingPlatformKeys = contactRepository.getContactPlatformKeys(existingContact.id)
    for ((key, entry) in newPlatformEntries) {
        if (key !in existingPlatformKeys) {
            contactRepository.updateContactPlatform(existingContact.id, key, entry)
        }
    }

    contactRepository.updateContact(
        freshContact.copy(
            name = chosenName ?: freshContact.name,
            updateTime = System.currentTimeMillis()
        )
    )

    collectionRepository.addContactToCollection(
        contactId = existingContact.id,
        collectionId = collectionId,
        sourceType = sourceType,
    )
}

/**
 * 将扫描到的联系方式附加到已有联系人。
 */
internal suspend fun attachToExistingContact(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    existingContact: Contact,
    info: ExtractedContactInfo,
    selectedFields: List<String>,
    customFields: Map<Int, String>,
    networkResult: NetworkResolveResult?
) {
    val freshContact = contactRepository.getContactById(existingContact.id) ?: existingContact
    val avatarToSet = networkResult?.avatarUrl?.ifBlank { null }
    val newPlatformEntries = buildPlatformEntries(info)
    val existingPlatformKeys = contactRepository.getContactPlatformKeys(existingContact.id)
    for ((key, entry) in newPlatformEntries) {
        if (key !in existingPlatformKeys) {
            contactRepository.updateContactPlatform(existingContact.id, key, entry)
        }
    }

    contactRepository.updateContact(
        if (freshContact.avatarUrl.isNullOrBlank() && !avatarToSet.isNullOrBlank()) {
            freshContact.copy(avatarUrl = avatarToSet, updateTime = System.currentTimeMillis())
        } else {
            freshContact.copy(updateTime = System.currentTimeMillis())
        }
    )

    if (selectedFields.isNotEmpty()) {
        val fieldMap = buildFieldMap(fieldRepository, info, filterKeys = selectedFields.toSet())
        if (fieldMap.isNotEmpty()) {
            val allExistingValues = fieldRepository.getFieldValuesByContactOnce(existingContact.id)
            val insertList = mutableListOf<Pair<Long, String>>()
            val enabledFields = fieldRepository.getAllEnabledFields().first()
            val fieldIdToKey = enabledFields.associate { it.id to it.fieldKey }
            for ((fieldId, value) in fieldMap) {
                val fieldKey = fieldIdToKey[fieldId] ?: continue
                val sameValueExists = allExistingValues.any { it.fieldId == fieldId && it.value == value }
                if (!sameValueExists) {
                    insertList.add(fieldId to value)
                }
            }
            if (insertList.isNotEmpty()) {
                fieldRepository.saveContactFieldValues(existingContact.id, insertList)
            }
        }
    }

    if (customFields.isNotEmpty()) {
        val customFieldMap = mutableMapOf<Long, String>()
        val customFieldDefs = fieldRepository.getAllEnabledCustomFields().first()
        for ((_, value) in customFields) {
            val matchedField = customFieldDefs.find {
                it.fieldName == value.split(":").getOrNull(0)?.trim()
            }
            if (matchedField != null) {
                customFieldMap[matchedField.id] = value
            }
        }
        if (customFieldMap.isNotEmpty()) {
            fieldRepository.saveContactCustomFieldValues(existingContact.id, customFieldMap)
        }
    }

    val collectionId = ensureCollectionId(collectionRepository, null)
    collectionRepository.addContactToCollection(
        contactId = existingContact.id,
        collectionId = collectionId,
        sourceType = "scan",
    )
}
