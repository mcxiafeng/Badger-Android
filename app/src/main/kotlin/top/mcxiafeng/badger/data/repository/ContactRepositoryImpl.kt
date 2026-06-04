package top.mcxiafeng.badger.data.repository

import android.util.Log
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
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import javax.inject.Inject

class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao,
    private val contactFieldDao: ContactFieldDao,
    private val customFieldDao: CustomFieldDao,
    private val contactFieldValueDao: ContactFieldValueDao,
    private val scanResultDao: ScanResultDao
) : ContactRepository {

    private val contactMutex = Mutex()

    // ========== 联系人基本操作 ==========

    override fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()

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
        contactDao.insertContact(contact)
    }

    override suspend fun updateContact(contact: Contact) = withContext(Dispatchers.IO) {
        contactDao.updateContact(contact)
    }

    override suspend fun deleteContact(contact: Contact) = withContext(Dispatchers.IO) {
        contactDao.deleteContact(contact)
    }

    override fun searchContacts(query: String): Flow<List<Contact>> {
        return contactDao.searchContacts(query)
    }

    // ========== 联系人社交平台操作 ==========

    override suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) = contactMutex.withLock {
        withContext(Dispatchers.IO) {
            val contact = contactDao.getContactById(contactId) ?: return@withContext
            val newPlatforms = (contact.platforms?.toMutableMap() ?: mutableMapOf()).apply {
                if (entry.jumpLink.isBlank() && entry.value.isNullOrBlank()) {
                    remove(fieldKey)
                } else {
                    this[fieldKey] = entry
                }
            }
            val updated = contact.copy(platforms = newPlatforms, updateTime = System.currentTimeMillis())
            contactDao.updateContact(updated)
        }
    }

    override suspend fun removeContactPlatform(contactId: Long, fieldKey: String) = contactMutex.withLock {
        withContext(Dispatchers.IO) {
            val contact = contactDao.getContactById(contactId) ?: return@withContext
            val newPlatforms = (contact.platforms?.toMutableMap() ?: mutableMapOf()).apply {
                remove(fieldKey)
            }
            val updated = contact.copy(platforms = newPlatforms, updateTime = System.currentTimeMillis())
            contactDao.updateContact(updated)
        }
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

        val allContacts = contactDao.getAllContacts().first()

        // 纯名字匹配：同名联系人应被视为重复候选
        if (newContactName.isNotBlank()) {
            for (contact in allContacts) {
                val nameSimilarity = calculateNameSimilarity(newContactName, contact.name)
                if (nameSimilarity == 1.0f) {
                    val score = 1.0f
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = contact
                        matchedFields = listOf("name")
                    }
                } else if (nameSimilarity > 0.7f) {
                    val score = nameSimilarity * 0.5f
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = contact
                        matchedFields = listOf("name")
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
                // 检查 Contact.platforms 中的平台字段
                for (contact in allContacts) {
                    val platforms = contact.platforms ?: continue
                    val entry = platforms[key] ?: continue
                    if (entry.value == value) {
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
                }
            } else {
                // 原有逻辑：检查 contact_field_values
                val potentialDuplicates = scanResultDao.findPotentialDuplicates(value, null)
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
}
