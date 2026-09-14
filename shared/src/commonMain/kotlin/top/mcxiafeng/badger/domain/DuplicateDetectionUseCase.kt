package top.mcxiafeng.badger.domain

import top.mcxiafeng.badger.data.model.DuplicateCheckResult
import top.mcxiafeng.badger.data.repository.ContactRepository

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
