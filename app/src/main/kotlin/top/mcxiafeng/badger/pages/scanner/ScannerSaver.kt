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
    // Write platform data to the V2 contact_platforms_cache table
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

/**
 * 从 fieldKey 中剥离去重后缀（如 "qq_1" → "qq"，"phone_2" → "phone"）
 */
internal fun stripFieldKeySuffix(key: String): String {
    val idx = key.lastIndexOf('_')
    if (idx <= 0) return key
    val suffix = key.substring(idx + 1)
    return if (suffix.all { it.isDigit() }) key.substring(0, idx) else key
}

/**
 * 从 ExtractedContactInfo 中构建 Contact.platforms 映射
 *
 * 仅处理社交平台字段（PLATFORM_FIELD_KEYS），将扁平值转为 PlatformEntry：
 * - value = 扫描提取的 ID/账号
 * - jumpLink = 通过 buildPlatformLink 自动生成（AUTO/NO_LINK）
 * - LINK_ONLY 平台不生成 jumpLink（需用户手动粘贴链接）
 */
internal fun buildPlatformEntries(info: ExtractedContactInfo): Map<String, PlatformEntry> {
    val result = mutableMapOf<String, PlatformEntry>()
    for ((key, value) in info.platforms) {
        val baseKey = stripFieldKeySuffix(key)
        if (baseKey !in PLATFORM_FIELD_KEYS) continue
        if (value.isBlank()) continue
        // 同一 fieldKey 多值时，只保留第一个
        if (baseKey in result) continue
        val jumpLink = buildPlatformLink(baseKey, value)
        result[baseKey] = PlatformEntry(
            jumpLink = jumpLink,
            value = value
        )
    }
    return result
}

/**
 * 将平台数据合并到已有 Contact.platforms 中
 */
internal fun mergePlatformEntries(
    existing: Map<String, PlatformEntry>?,
    newEntries: Map<String, PlatformEntry>
): Map<String, PlatformEntry> {
    val merged = (existing?.toMutableMap() ?: mutableMapOf())
    for ((key, entry) in newEntries) {
        if (key !in merged) {
            merged[key] = entry
        }
    }
    return merged
}

/**
 * 将 [ExtractedContactInfo] 中的字段值映射为 fieldId → value 列表
 *
 * 支持同平台多值：如 qq_1 和 qq 都映射到同一个 fieldId，生成独立记录。
 */
internal suspend fun buildFieldMap(
    fieldRepository: FieldRepository,
    info: ExtractedContactInfo,
    filterKeys: Set<String>? = null
): List<Pair<Long, String>> {
    val result = mutableListOf<Pair<Long, String>>()
    val fields = fieldRepository.getAllEnabledFields().first()
    val fieldKeyToId = fields.associate { it.fieldKey to it.id }
    val fieldValues = info.toFieldValues()
    for ((key, value) in fieldValues) {
        val baseKey = stripFieldKeySuffix(key)
        if (baseKey in PLATFORM_FIELD_KEYS) continue
        if (filterKeys != null && baseKey !in filterKeys && key !in filterKeys) continue
        val fieldId = fieldKeyToId[baseKey] ?: continue
        result.add(fieldId to value)
    }
    return result
}

/**
 * 构建字段合并对比列表
 *
 * 将新扫描结果与已有联系人的字段逐一对比，生成合并条目列表。
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

    val entries = newMap.mapNotNull { (key, newValue) ->
        val existingValue = existingMap[key]
        if (existingValue != null && existingValue == newValue) return@mapNotNull null
        FieldMergeEntry(
            fieldKey = key,
            fieldName = fieldNameMap[key] ?: key,
            existingValue = existingValue,
            newValue = newValue,
            selectedValue = MergeChoice.APPEND
        )
    }
        return entries
}

/**
 * 按用户选择合并字段到已有联系人
 *
 * 根据合并条目中用户的选择：
 * - KEEP：不做任何操作
 * - REPLACE：替换已有字段值为新值
 * - APPEND：追加新值（同一字段多个值）
 * 并新增一条成员关联记录。
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
    
    // 过滤掉重复字段（与已有联系人相同的字段），只处理 mergeEntries 中的字段
    val fieldValues = newInfo.toFieldValues()
    val filteredFieldValues = fieldValues.filterNot { duplicateFieldKeys.contains(it.key) }
    
    for (entry in mergeEntries) {
        if (entry.selectedValue == MergeChoice.KEEP) continue
        val fieldId = fieldIdMap[entry.fieldKey] ?: continue
        val newValue = entry.newValue ?: continue

        when (entry.selectedValue) {
            MergeChoice.REPLACE -> {
                val allValues = fieldRepository.getFieldValuesByContactOnce(existingContact.id)
                val target = allValues.find { it.fieldId == fieldId }
                if (target != null) {
                                        fieldRepository.updateFieldValue(target.copy(value = newValue, updateTime = System.currentTimeMillis()))
                } else {
                                        fieldRepository.saveContactFieldValues(existingContact.id, mapOf(fieldId to newValue))
                }
            }
            MergeChoice.APPEND -> {
                                fieldRepository.saveContactFieldValues(existingContact.id, mapOf(fieldId to newValue))
            }
            MergeChoice.KEEP -> { /* 不做任何操作 */ }
        }
    }

    // 更新联系人名字（平台数据已通过 V2 contact_platforms_cache 表管理）
    val freshContact = contactRepository.getContactById(existingContact.id) ?: existingContact
    val newPlatformEntries = buildPlatformEntries(newInfo)
    // Write new platform entries to the V2 contact_platforms_cache table
    val existingPlatformKeys = contactRepository.getContactPlatformKeys(existingContact.id)
    for ((key, entry) in newPlatformEntries) {
        if (key !in existingPlatformKeys) {
            contactRepository.updateContactPlatform(existingContact.id, key, entry)
        }
    }
    val updatedName = chosenName ?: freshContact.name
    contactRepository.updateContact(
        freshContact.copy(name = updatedName, updateTime = System.currentTimeMillis())
    )

    // 新增成员关联记录
    collectionRepository.addContactToCollection(
        contactId = existingContact.id,
        collectionId = collectionId,
        sourceType = sourceType,
    )
}

/**
 * 将扫描到的联系方式附加到已有联系人
 *
 * 根据用户在 AttachFieldDialog 中勾选的字段，逐一保存到已有联系人。
 * 同值跳过、异值更新、空值新增。
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
    // 重新从 DB 读取最新数据，避免用过时的参数覆盖并发修改
    val freshContact = contactRepository.getContactById(existingContact.id) ?: existingContact
    val avatarToSet = networkResult?.avatarUrl?.ifBlank { null }
    val newPlatformEntries = buildPlatformEntries(info)
    // Write new platform entries to the V2 contact_platforms_cache table
    val existingPlatformKeys = contactRepository.getContactPlatformKeys(existingContact.id)
    for ((key, entry) in newPlatformEntries) {
        if (key !in existingPlatformKeys) {
            contactRepository.updateContactPlatform(existingContact.id, key, entry)
        }
    }
    if (freshContact.avatarUrl.isNullOrBlank() && !avatarToSet.isNullOrBlank()) {
        contactRepository.updateContact(
            freshContact.copy(avatarUrl = avatarToSet, updateTime = System.currentTimeMillis())
        )
    } else {
        contactRepository.updateContact(freshContact.copy(updateTime = System.currentTimeMillis()))
    }

    // 按用户勾选保存系统字段值：同值跳过，不同值一律新增（允许同字段多值）
    if (selectedFields.isNotEmpty()) {
        val fieldMap = buildFieldMap(fieldRepository, info, filterKeys = selectedFields.toSet())
        if (fieldMap.isNotEmpty()) {
            val allExistingValues = fieldRepository.getFieldValuesByContactOnce(existingContact.id)
            val insertList = mutableListOf<Pair<Long, String>>()
            val enabledFields = fieldRepository.getAllEnabledFields().first()
            val fieldIdToKey = enabledFields.associate { it.id to it.fieldKey }
            for ((fieldId, value) in fieldMap) {
                val fieldKey = fieldIdToKey[fieldId] ?: continue
                // 检查该字段是否已有完全相同的值（避免重复附加）
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
        for ((index, value) in customFields) {
            val matchedField = customFieldDefs.find { it.fieldName == value.split(":").getOrNull(0)?.trim() }
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
