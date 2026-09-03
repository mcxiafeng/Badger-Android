package top.mcxiafeng.badger.data.model

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

/**
 * 联系人字母桶统计。
 *
 * 由 ContactCacheDao.getLetterIndex 返回，UI 侧栏渲染。
 */
@Immutable
data class LetterCount(val letter: String, val count: Int)

/**
 * 联系人及其所有字段值的组合数据类。
 *
 * UI 侧一次性展示联系人完整信息时使用，内嵌 ContactCacheEntity。
 * [Phase 5] 原名 ContactWithFields，向 Person* 命名收敛。
 */
@Immutable
data class PersonWithFields(
    val contact: ContactCacheEntity,
    val fieldValues: List<PersonFieldDisplay>
)

/**
 * 联系人字段值的展示数据类。
 *
 * UI 层直接渲染。
 * [Phase 5] 原名 ContactFieldDisplay，向 Person* 命名收敛。
 */
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

/**
 * 重复联系人检查结果。
 *
 * 由 ContactRepository.checkDuplicate 返回；existingContact 改为 ContactCacheEntity。
 */
@Immutable
data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val existingContact: ContactCacheEntity?,
    val similarityScore: Float,
    val matchFields: List<String>
)
