package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS

/** Encapsulates duplicate-search scoring so ContactRepositoryImpl does not own matching policy. */
internal class ContactDuplicateMatcher(
    private val contactCacheDao: ContactCacheDao,
    private val contactFieldCacheDao: ContactFieldCacheDao,
    private val contactFieldValueCacheDao: ContactFieldValueCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
) {
    suspend fun check(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>,
    ): DuplicateCheckResult {
        var bestMatch: ContactCacheEntity? = null
        var bestScore = 0f
        var matchedFields = emptyList<String>()

        fun consider(contact: ContactCacheEntity, score: Float, fields: List<String>) {
            if (score > bestScore) {
                bestScore = score
                bestMatch = contact
                matchedFields = fields
            }
        }

        if (newContactName.isNotBlank()) {
            for (contact in contactCacheDao.getContactsByName(newContactName)) {
                if (contact.name.equals(newContactName, ignoreCase = true)) {
                    consider(contact, 1f, listOf("name"))
                }
            }
            if (bestScore < 1f) {
                for (contact in contactCacheDao.searchContactsByName(newContactName).first()) {
                    val similarity = calculateNameSimilarity(newContactName, contact.name)
                    if (similarity > 0.7f && similarity < 1f) {
                        consider(contact, similarity * 0.5f, listOf("name"))
                    }
                }
            }
        }

        if (fieldValues.isEmpty() && customFieldValues.isEmpty()) {
            return result(bestMatch, bestScore, matchedFields)
        }

        for ((key, value) in fieldValues) {
            if (value.isBlank()) continue
            if (key in PLATFORM_FIELD_KEYS) {
                val ids = contactPlatformCacheDao.findContactIdsByPlatform(key, value, -1)
                for (contact in ids.mapNotNull(contactCacheDao::getContactById)) {
                    val fields = mutableListOf(key)
                    var score = 1f
                    val similarity = calculateNameSimilarity(newContactName, contact.name)
                    if (similarity > 0.7f) {
                        score += similarity * 0.5f
                        fields += "name"
                    }
                    consider(contact, score, fields)
                }
            } else {
                for (potential in contactCacheDao.searchContacts(value).first()) {
                    var score = 0f
                    val fields = mutableListOf<String>()
                    val existingValues = contactFieldValueCacheDao
                        .getFieldValuesByContactOnce(potential.id)
                    for (existingValue in existingValues) {
                        val fieldId = existingValue.fieldId ?: continue
                        val field = contactFieldCacheDao.getFieldById(fieldId) ?: continue
                        if (field.fieldKey == key && existingValue.value == value) {
                            score += 1f
                            fields += field.fieldName
                        }
                    }
                    val similarity = calculateNameSimilarity(newContactName, potential.name)
                    if (similarity > 0.7f) {
                        score += similarity * 0.5f
                        fields += "name"
                    }
                    consider(potential, score, fields)
                }
            }
        }

        for ((customFieldId, value) in customFieldValues) {
            if (value.isBlank()) continue
            val contactIds = contactFieldValueDaoFindByCustomField(customFieldId, value)
            for (contact in contactIds.mapNotNull(contactCacheDao::getContactById)) {
                val similarity = calculateNameSimilarity(newContactName, contact.name)
                val fields = mutableListOf("custom:$customFieldId")
                var score = 1f
                if (similarity > 0.7f) {
                    score += similarity * 0.5f
                    fields += "name"
                }
                consider(contact, score, fields)
            }
        }

        return result(bestMatch, bestScore, matchedFields)
    }

    private suspend fun contactFieldValueDaoFindByCustomField(customFieldId: Long, value: String): List<Long> =
        contactFieldValueCacheDao.findContactIdsByCustomFieldValue(customFieldId, value)

    private fun result(
        contact: ContactCacheEntity?,
        score: Float,
        fields: List<String>,
    ) = DuplicateCheckResult(
        isDuplicate = score >= 1f,
        existingContact = contact,
        similarityScore = score.coerceIn(0f, 2f),
        matchFields = fields,
    )

    private fun calculateNameSimilarity(name1: String, name2: String): Float {
        if (name1.equals(name2, ignoreCase = true)) return 1f
        val set1 = name1.lowercase().toSet()
        val set2 = name2.lowercase().toSet()
        val intersection = set1.intersect(set2).size.toFloat()
        val union = set1.union(set2).size.toFloat()
        return if (union > 0f) intersection / union else 0f
    }
}
