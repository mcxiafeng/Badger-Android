package top.mcxiafeng.badger.domain

import kotlinx.coroutines.flow.first
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.repository.ContactRepository
import javax.inject.Inject

/**
 * 重复联系人检测 UseCase
 *
 * 将提取到的联系人字段与已有联系人进行比对，检测是否已存在相同联系人。
 */
class DuplicateDetectionUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String> = emptyMap()
    ): DuplicateCheckResult {
        var bestMatch: Contact? = null
        var bestScore = 0f
        var matchedFields = emptyList<String>()

        val allContacts = contactRepository.getAllContacts().first()

        // 纯名字匹配
        if (newContactName.isNotBlank()) {
            for (contact in allContacts) {
                val nameSimilarity = calculateNameSimilarity(newContactName, contact.name)
                if (nameSimilarity == 1.0f) {
                    if (1.0f > bestScore) {
                        bestScore = 1.0f
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
            return DuplicateCheckResult(
                isDuplicate = bestScore >= 1.0f,
                existingContact = bestMatch,
                similarityScore = bestScore.coerceIn(0f, 2f),
                matchFields = matchedFields
            )
        }

        // 检查所有字段值（包括 phone、email 和平台字段）与联系人 platforms 的匹配
        for ((key, value) in fieldValues) {
            if (value.isBlank()) continue
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
        }

        return DuplicateCheckResult(
            isDuplicate = bestScore >= 1.0f,
            existingContact = bestMatch,
            similarityScore = bestScore.coerceIn(0f, 2f),
            matchFields = matchedFields
        )
    }

    companion object {
        fun calculateNameSimilarity(name1: String, name2: String): Float {
            if (name1.equals(name2, ignoreCase = true)) return 1.0f
            val set1 = name1.lowercase().toSet()
            val set2 = name2.lowercase().toSet()
            val intersection = set1.intersect(set2).size.toFloat()
            val union = set1.union(set2).size.toFloat()
            return if (union > 0) intersection / union else 0f
        }
    }
}
