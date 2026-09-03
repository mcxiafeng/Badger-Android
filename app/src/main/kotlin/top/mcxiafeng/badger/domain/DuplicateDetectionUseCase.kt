package top.mcxiafeng.badger.domain

import top.mcxiafeng.badger.data.model.DuplicateCheckResult
import top.mcxiafeng.badger.data.repository.ContactRepository

/**
 * 重复联系人检测 UseCase
 *
 * 将提取到的联系人字段与已有联系人进行比对，检测是否已存在相同联系人。
 * 使用 SQL 查询代替全量加载，性能从 O(n) 提升到 O(log n)。
 *
 * [§14.2] Hilt `@Inject constructor` → Koin `factoryOf(::DuplicateDetectionUseCase)`。
 */
class DuplicateDetectionUseCase(
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
