package top.mcxiafeng.badger.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.ContactFieldDao
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactFieldValueDao
import top.mcxiafeng.badger.data.ContactFtsDao
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactPlatformDao
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.utils.PinyinUtils
import javax.inject.Inject

class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao,
    private val contactFieldDao: ContactFieldDao,
    private val customFieldDao: CustomFieldDao,
    private val contactFieldValueDao: ContactFieldValueDao,
    private val scanResultDao: ScanResultDao,
    private val contactPlatformDao: ContactPlatformDao,
    private val contactFtsDao: ContactFtsDao
) : ContactRepository {

    private val contactMutex = Mutex()

    // ========== 联系人基本操作 ==========

    override fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()

    override fun getAllContactsPagingSource(): PagingSource<Int, Contact> =
        contactDao.getAllContactsPagingSource()

    override fun searchContactsPagingSource(query: String): Flow<PagingData<Contact>> {
        val ftsQuery = escapeFtsQuery(query)
        Log.d("Tester", "searchContactsPagingSource: raw='$query', fts='$ftsQuery'")
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            if (ftsQuery.isNotEmpty()) {
                contactFtsDao.searchContactsCombinedPagingSource(ftsQuery, query)
            } else {
                // FTS 查询为空（纯特殊字符），退化为仅 LIKE 搜索
                contactDao.searchContactsByNameLikePagingSource(query)
            }
        }.flow
    }

    override fun getLetterIndex(): Flow<List<LetterCount>> =
        contactDao.getLetterIndex()

    override suspend fun getContactById(id: Long): Contact? = withContext(Dispatchers.IO) {
        contactDao.getContactById(id)
    }

    override fun getAllContactsWithFields(): Flow<List<ContactWithFields>> {
        return contactDao.getAllContacts().map { contacts ->
            contacts.map { contact ->
                ContactWithFields(contact, emptyList())
            }
        }
    }

    override suspend fun getContactWithFieldsById(id: Long): ContactWithFields? = withContext(Dispatchers.IO) {
        val contact = contactDao.getContactById(id) ?: return@withContext null
        val fieldValues = contactFieldValueDao.getFieldValuesByContactOnce(id)

        val fieldIds = fieldValues.mapNotNull { it.fieldId }.distinct()
        val customFieldIds = fieldValues.mapNotNull { it.customFieldId }.distinct()

        val fieldMap = if (fieldIds.isNotEmpty()) {
            contactFieldDao.getFieldsByIds(fieldIds).filter { it.isEnabled }.associateBy { it.id }
        } else emptyMap()
        val customFieldMap = if (customFieldIds.isNotEmpty()) {
            customFieldDao.getCustomFieldsByIds(customFieldIds).filter { it.isEnabled }.associateBy { it.id }
        } else emptyMap()

        val fields = fieldValues.mapNotNull { value ->
            if (value.fieldId != null) {
                val field = fieldMap[value.fieldId] ?: return@mapNotNull null
                ContactFieldDisplay(
                    valueId = value.id,
                    fieldId = field.id,
                    customFieldId = null,
                    fieldName = field.fieldName,
                    fieldKey = field.fieldKey,
                    icon = field.icon,
                    fieldType = null,
                    value = value.value,
                    sortOrder = field.sortOrder
                )
            } else if (value.customFieldId != null) {
                val customField = customFieldMap[value.customFieldId] ?: return@mapNotNull null
                ContactFieldDisplay(
                    valueId = value.id,
                    fieldId = null,
                    customFieldId = customField.id,
                    fieldName = customField.fieldName,
                    fieldKey = null,
                    icon = null,
                    fieldType = customField.fieldType,
                    value = value.value,
                    sortOrder = customField.sortOrder
                )
            } else null
        }.sortedBy { it.sortOrder }

        ContactWithFields(contact, fields)
    }

    override suspend fun insertContact(contact: Contact): Long = withContext(Dispatchers.IO) {
        val withPinyin = if (contact.pinyinInitial.isBlank() && contact.name.isNotBlank()) {
            contact.copy(pinyinInitial = PinyinUtils.getContactPinyinInitial(contact.name))
        } else contact
        contactDao.insertContact(withPinyin)
    }

    override suspend fun updateContact(contact: Contact) = withContext(Dispatchers.IO) {
        val withPinyin = if (contact.pinyinInitial.isBlank() && contact.name.isNotBlank()) {
            contact.copy(pinyinInitial = PinyinUtils.getContactPinyinInitial(contact.name))
        } else contact
        contactDao.updateContact(withPinyin)
    }

    override suspend fun deleteContact(contact: Contact) = withContext(Dispatchers.IO) {
        contactDao.deleteContact(contact)
    }

    override suspend fun deleteByIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        Log.d("Tester", "ContactRepositoryImpl.deleteByIds: count=${ids.size}")
        contactDao.deleteByIds(ids)
    }

    override fun searchContacts(query: String): Flow<List<Contact>> {
        return if (query.isBlank()) {
            contactDao.getAllContacts()
        } else {
            val ftsQuery = escapeFtsQuery(query)
            Log.d("Tester", "searchContacts: raw='$query', fts='$ftsQuery'")
            if (ftsQuery.isNotEmpty()) {
                contactFtsDao.searchContactsCombined(ftsQuery, query)
            } else {
                // FTS 查询为空，退化到 LIKE 搜索 name + field values
                contactDao.searchContacts(query)
            }
        }
    }

    // ========== 联系人社交平台操作 ==========

    override suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        contactMutex.withLock {
            withContext(Dispatchers.IO) {
                if (entry.jumpLink.isBlank() && entry.value.isNullOrBlank()) {
                    contactPlatformDao.deleteByContactAndKey(contactId, fieldKey)
                } else {
                    contactPlatformDao.insertPlatform(
                        ContactPlatform(
                            contactId = contactId,
                            platformKey = fieldKey,
                            value = entry.value,
                            displayName = entry.displayName,
                            jumpLink = entry.jumpLink,
                            originalLink = entry.originalLink,
                            avatarUrl = entry.avatarUrl
                        )
                    )
                }
            }
        }
    }

    override suspend fun removeContactPlatform(contactId: Long, fieldKey: String) = contactMutex.withLock {
        withContext(Dispatchers.IO) {
            contactPlatformDao.deleteByContactAndKey(contactId, fieldKey)
        }
    }

    override suspend fun getAllContactPlatformsGrouped(): Map<Long, List<ContactPlatform>> =
        withContext(Dispatchers.IO) {
            contactPlatformDao.getAllPlatforms().groupBy { it.contactId }
        }

    override suspend fun getContactPlatformKeys(contactId: Long): Set<String> =
        withContext(Dispatchers.IO) {
            contactPlatformDao.getPlatformsByContact(contactId).map { it.platformKey }.toSet()
        }

    override suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform> =
        withContext(Dispatchers.IO) {
            contactPlatformDao.getPlatformsByContact(contactId)
        }

    // ========== 重复检测 ==========

    override suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>
    ): DuplicateCheckResult = withContext(Dispatchers.IO) {
        var bestMatch: Contact? = null
        var bestScore = 0f
        var matchedFields = emptyList<String>()

        // 1. Name matching: use SQL exact match first, then Jaccard for fuzzy
        if (newContactName.isNotBlank()) {
            val exactMatches = contactDao.getContactsByName(newContactName)
            for (contact in exactMatches) {
                if (contact.name.equals(newContactName, ignoreCase = true)) {
                    if (1.0f > bestScore) {
                        bestScore = 1.0f
                        bestMatch = contact
                        matchedFields = listOf("name")
                    }
                }
            }
            // Fuzzy: search contacts starting with similar prefix
            if (bestScore < 1.0f) {
                val prefixMatches = contactDao.searchContactsByName(newContactName).first()
                for (contact in prefixMatches) {
                    val nameSimilarity = calculateNameSimilarity(newContactName, contact.name)
                    if (nameSimilarity > 0.7f && nameSimilarity < 1.0f) {
                        val score = nameSimilarity * 0.5f
                        if (score > bestScore) {
                            bestScore = score
                            bestMatch = contact
                            matchedFields = listOf("name")
                        }
                    }
                }
            }
        }

        if (fieldValues.isEmpty() && customFieldValues.isEmpty()) {
            Log.d("Tester", "checkDuplicate: name-only match, bestScore=$bestScore, bestMatch=${bestMatch?.id}, matchedFields=$matchedFields")
            return@withContext DuplicateCheckResult(
                isDuplicate = bestScore >= 1.0f,
                existingContact = bestMatch,
                similarityScore = bestScore.coerceIn(0f, 2f),
                matchFields = matchedFields
            )
        }

        val platformKeys = fieldValues.keys.filter { it in PLATFORM_FIELD_KEYS }.toSet()

        for ((key, value) in fieldValues) {
            if (value.isBlank()) continue

            if (key in platformKeys) {
                // Use SQL to find platform duplicates directly
                val platformDuplicates = contactPlatformDao.findDuplicatesByPlatform(key, value, -1)
                for (contact in platformDuplicates) {
                    var score = 0f
                    val fields = mutableListOf<String>()
                    score += 1.0f
                    fields.add(key)
                    val nameSimilarity = calculateNameSimilarity(newContactName, contact.name)
                    if (nameSimilarity > 0.7f) {
                        score += nameSimilarity * 0.5f
                        fields.add("name")
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = contact
                        matchedFields = fields
                    }
                }
            } else {
                // 组合搜索：FTS 匹配 name + LIKE 匹配字段值/二维码/OCR
                val ftsQuery = escapeFtsQuery(value)
                Log.d("Tester", "checkDuplicate: value='$value', ftsQuery='$ftsQuery'")

                val potentialDuplicates = buildList {
                    addAll(scanResultDao.findPotentialDuplicates(value, null))
                    if (ftsQuery.isNotEmpty()) {
                        addAll(contactFtsDao.searchContactsFtsOnce(ftsQuery, 5))
                    }
                }.distinctBy { it.id }

                for (potential in potentialDuplicates) {
                    var score = 0f
                    val fields = mutableListOf<String>()
                    val existingValues = contactFieldValueDao.getFieldValuesByContactOnce(potential.id)
                    for (existingValue in existingValues) {
                        val fieldId = existingValue.fieldId ?: continue
                        val field = contactFieldDao.getFieldById(fieldId)
                        if (field != null && field.fieldKey == key && existingValue.value == value) {
                            score += 1.0f
                            fields.add(field.fieldName)
                        }
                    }
                    val nameSimilarity = calculateNameSimilarity(newContactName, potential.name)
                    if (nameSimilarity > 0.7f) {
                        score += nameSimilarity * 0.5f
                        fields.add("name")
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = potential
                        matchedFields = fields
                    }
                }
            }
        }

        Log.d("Tester", "checkDuplicate: bestScore=$bestScore, bestMatch=${bestMatch?.id}, matchedFields=$matchedFields")

        DuplicateCheckResult(
            isDuplicate = bestScore >= 1.0f,
            existingContact = bestMatch,
            similarityScore = bestScore.coerceIn(0f, 2f),
            matchFields = matchedFields
        )
    }

    private fun calculateNameSimilarity(name1: String, name2: String): Float {
        if (name1.equals(name2, ignoreCase = true)) return 1.0f
        val set1 = name1.lowercase().toSet()
        val set2 = name2.lowercase().toSet()
        val intersection = set1.intersect(set2).size.toFloat()
        val union = set1.union(set2).size.toFloat()
        return if (union > 0) intersection / union else 0f
    }

    /** 转义 FTS4 查询，使用前缀匹配支持中文部分搜索 */
    private fun escapeFtsQuery(query: String): String {
        val sanitized = query.replace(Regex("[\"^~]"), "").trim()
        if (sanitized.isBlank()) return ""
        Log.d("Tester", "escapeFtsQuery: raw='$query', sanitized='$sanitized'")
        return "$sanitized*"
    }
}
