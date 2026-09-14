package top.mcxiafeng.badger.data.model

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

@Immutable
data class LetterCount(val letter: String, val count: Int)

@Immutable
data class PersonWithFields(
    val contact: ContactCacheEntity,
    val fieldValues: List<PersonFieldDisplay>
)

@Immutable
data class PersonFieldDisplay(
    val valueId: Long,
    val fieldId: Long?,
    val customFieldId: Long?,
    val fieldName: String,
    val fieldKey: String?,
    val icon: String?,
    val fieldType: String?,
    val value: String,
    val sortOrder: Int
)

@Immutable
data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val existingContact: ContactCacheEntity?,
    val similarityScore: Float,
    val matchFields: List<String>
)
