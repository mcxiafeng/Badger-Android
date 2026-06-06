package top.mcxiafeng.badger.domain

import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.repository.ContactRepository
import javax.inject.Inject

/**
 * 重复联系人检测 UseCase
 *
 * 将提取到的联系人字段与已有联系人进行比对，检测是否已存在相同联系人。
 * 使用 SQL 查询代替全量加载，性能从 O(n) 提升到 O(log n)。
 */
class DuplicateDetectionUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String> = emptyMap()
    ): DuplicateCheckResult {
        return contactRepository.checkDuplicate(newContactName, fieldValues, customFieldValues)
    }
}

/** 计算两个名字的 Jaccard 相似度（基于字符集合） */
fun calculateNameSimilarity(name1: String, name2: String): Float {
    if (name1.equals(name2, ignoreCase = true)) return 1.0f
    val set1 = name1.lowercase().toSet()
    val set2 = name2.lowercase().toSet()
    val intersection = set1.intersect(set2).size.toFloat()
    val union = set1.union(set2).size.toFloat()
    return if (union > 0) intersection / union else 0f
}
